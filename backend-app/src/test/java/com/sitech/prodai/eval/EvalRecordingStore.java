package com.sitech.prodai.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 评测「录制-回放」存储（方案 R1 Step 3）。
 * <p>
 * 录制：将每次 LLM 请求（system + history + user）与响应存为
 * {@code eval/recordings/<case_id>/<hash>.json}；
 * 回放：相同请求哈希命中录制文件时直接返回录制响应，不真实调用 LLM。
 * <p>
 * 降本语义（方案 1.3 Step 3）：
 * <ul>
 *   <li>提示词/守门逻辑改动 → 跑录制回放（秒级、零成本）看守门结果差异</li>
 *   <li>模型切换/大改提示词 → 显式 {@code --live} 重录</li>
 * </ul>
 */
public final class EvalRecordingStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path rootDir;
    private final boolean live;

    public EvalRecordingStore(Path rootDir, boolean live) {
        this.rootDir = rootDir;
        this.live = live;
    }

    public boolean isLive() {
        return live;
    }

    /** 回放：命中录制响应则返回，否则 null（调用方决定是否真实调用/失败）。 */
    public String replay(String caseId, String systemPrompt, String userMessage) {
        Path file = recordingFile(caseId, systemPrompt, userMessage);
        if (file == null || !Files.exists(file)) {
            return null;
        }
        try {
            Map<String, Object> record = MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeReference<Map<String, Object>>() {});
            Object response = record.get("response");
            return response == null ? null : String.valueOf(response);
        } catch (Exception e) {
            return null;
        }
    }

    /** 录制：写请求响应对（live 模式才调用）。 */
    public void record(String caseId, String systemPrompt, String userMessage, String response) {
        if (!live) {
            return;
        }
        Path file = recordingFile(caseId, systemPrompt, userMessage);
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("system_prompt_hash", sha256(systemPrompt));
            record.put("system_prompt", systemPrompt);
            record.put("user_message", userMessage);
            record.put("response", response);
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(record), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 录制失败不阻断评测主流程
        }
    }

    private Path recordingFile(String caseId, String systemPrompt, String userMessage) {
        if (caseId == null || caseId.isBlank()) {
            return null;
        }
        String hash = sha256(systemPrompt + "\u0000" + userMessage);
        return rootDir.resolve("recordings").resolve(caseId).resolve(hash + ".json");
    }

    /** 请求指纹：SHA-256 截断 32 位十六进制（足够区分提示词/输入变化）。 */
    static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 32);
        } catch (Exception e) {
            return String.valueOf(content == null ? 0 : content.hashCode());
        }
    }

    /** 从 classpath 读取全部黄金用例（JSONL）。 */
    public static List<EvalCase> loadGoldenCases(String classpathLocation) throws IOException {
        List<EvalCase> cases = new ArrayList<>();
        try (var in = EvalRecordingStore.class.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IOException("黄金评测集不存在: " + classpathLocation);
            }
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
                    continue;
                }
                cases.add(MAPPER.readValue(trimmed, EvalCase.class));
            }
        }
        return cases;
    }

    /** 评测根目录（默认 test 资源相对工作目录，可用 -Dprodai.eval.dir 覆盖）。 */
    public static Path defaultRoot() {
        String configured = System.getProperty("prodai.eval.dir");
        return Paths.get(configured != null ? configured : "target/eval");
    }
}
