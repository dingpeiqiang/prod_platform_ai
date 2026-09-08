package com.sitech.prodai.service.agent.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 意图识别提示词组装器（方案 R3：提示词外置化）。
 * <p>
 * 将原 {@code DefaultUnderstander.buildSystemPrompt} 的约 100 行硬编码提示词
 * 迁出为外部资源，支持不发版修改 + 场景变量化：
 * <ul>
 *   <li>模板文件：classpath {@code prompts/intent/}（角色定义 / 公共骨架 / rd 规则块）</li>
 *   <li>加载策略：外部目录（{@code app.prompt-intent-dir}，默认 {@code ./prompts/intent}）
 *       优先 → classpath 回退——与 {@code ScenePromptManager} 双轨制语义一致</li>
 *   <li>热更：外部文件 mtime 变化自动重载（缓存失效检测），无需重启</li>
 *   <li>运行时注入：可用能力清单 / 已发布流程清单 / 会话工单上下文仍由调用方
 *       （DefaultUnderstander）动态拼装——这部分依赖 Spring Bean 状态，不做静态模板化</li>
 * </ul>
 * <p>
 * 模板文件清单：
 * <ul>
 *   <li>{@code role_rd.txt}：rd 场景角色定义（占位符 {@code {role}}）</li>
 *   <li>{@code role_ops.txt}：ops 场景角色定义</li>
 *   <li>{@code role_query.txt}：query 场景角色定义（产商品查询助手）</li>
 *   <li>{@code base_prompt.txt}：公共骨架（输出 JSON 契约 + CONFIRM 判定规则，占位符
 *       {@code {rd_rules_block}}）</li>
 *   <li>{@code rd_rules_block.txt}：rd 场景专属铁律（工单操作 / 查已有 vs 造新分流）</li>
 * </ul>
 */
@Component
public class IntentPromptAssembler {

    private static final Logger log = LoggerFactory.getLogger(IntentPromptAssembler.class);

    /** classpath 模板根目录。 */
    private static final String CLASSPATH_DIR = "prompts/intent/";

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /** 外部提示词目录（可配置；空串表示禁用外部目录，仅用 classpath）。 */
    private final Path externalDir;

    /** 模板缓存：文件名 → {content, mtime}（仅缓存外部文件；classpath 每次读取成本可忽略但同样缓存）。 */
    private final Map<String, CachedTemplate> cache = new ConcurrentHashMap<>();

    public IntentPromptAssembler(@Value("${app.prompt-intent-dir:prompts/intent}") String externalDir) {
        this.externalDir = externalDir == null || externalDir.isBlank()
                ? null : Paths.get(externalDir).toAbsolutePath().normalize();
    }

    /**
     * 组装意图识别系统提示词。
     *
     * @param rdScene 是否为产商品研发场景（scene=rd）
     * @return 组装后的系统提示词（不含动态能力清单——那部分由调用方追加）
     */
    public String assembleSystemPrompt(boolean rdScene) {
        return assembleSystemPrompt(rdScene ? "rd" : "ops");
    }

    /**
     * 组装意图识别系统提示词（场景键三态：rd / ops / query）。
     * <p>
     * query 场景（产商品查询助手）使用独立角色定义 {@code role_query.txt}；
     * 未知场景回落 ops 角色（与 {@code AgentCapabilityRegistry.DEFAULT_SCENE} 语义一致）。
     *
     * @param scene 场景键（rd / ops / query；null/空 = ops）
     * @return 组装后的系统提示词（不含动态能力清单——那部分由调用方追加）
     */
    public String assembleSystemPrompt(String scene) {
        String normalized = scene == null || scene.isBlank()
                ? "ops" : scene.trim().toLowerCase(java.util.Locale.ROOT);
        String roleFile = switch (normalized) {
            case "rd" -> "role_rd.txt";
            case "query" -> "role_query.txt";
            default -> "role_ops.txt";
        };
        String role = loadTemplate(roleFile);
        if (role == null || role.isBlank()) {
            // 模板缺失兜底：内联最小角色定义（不阻断理解链路）
            role = switch (normalized) {
                case "rd" -> "你是一个产商品研发智能助手，负责理解用户的需求，并将其翻译为可执行的研发配置计划。\n";
                case "query" -> "你是一个产商品查询智能助手，负责理解用户的查询诉求，并将其翻译为可执行的查询计划。\n";
                default -> "你是一个产品运营智能助手，负责理解用户的问题，并将其翻译为可执行的查询计划。\n";
            };
        }
        String base = loadTemplate("base_prompt.txt");
        if (base == null || base.isBlank()) {
            log.error("[IntentPromptAssembler] base_prompt.txt 缺失，理解链路提示词不完整");
            base = "";
        }
        String rdRules = "";
        if ("rd".equals(normalized)) {
            String block = loadTemplate("rd_rules_block.txt");
            rdRules = block == null ? "" : block;
        }
        String withPlaceholder = base.replace("{rd_rules_block}", rdRules);
        return role.trim() + "\n" + withPlaceholder;
    }

    /**
     * 加载模板：外部目录（mtime 热更检测）→ classpath 回退。
     * 外部文件读取失败时回退缓存旧值（可用性优先）。
     */
    private String loadTemplate(String filename) {
        if (externalDir != null) {
            Path external = externalDir.resolve(filename);
            if (Files.exists(external)) {
                try {
                    long mtime = Files.getLastModifiedTime(external).toMillis();
                    CachedTemplate cached = cache.get(filename);
                    if (cached != null && cached.mtime == mtime) {
                        return cached.content;
                    }
                    String content = Files.readString(external, StandardCharsets.UTF_8);
                    cache.put(filename, new CachedTemplate(content, mtime));
                    log.info("[IntentPromptAssembler] 加载外部提示词模板: {} (mtime 更新)", filename);
                    return content;
                } catch (IOException e) {
                    log.warn("[IntentPromptAssembler] 读取外部模板失败 {}: {}（回退缓存/classpath）",
                            filename, e.getMessage());
                    CachedTemplate cached = cache.get(filename);
                    if (cached != null) {
                        return cached.content;
                    }
                }
            }
        }
        try {
            var resource = resolver.getResource("classpath:" + CLASSPATH_DIR + filename);
            if (resource.exists()) {
                try (var in = resource.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            log.warn("[IntentPromptAssembler] 读取 classpath 模板失败 {}: {}", filename, e.getMessage());
        }
        return null;
    }

    /** 列出全部模板文件名（外部 + classpath 合并，诊断用）。 */
    public java.util.List<String> listTemplates() {
        java.util.List<String> names = new ArrayList<>();
        if (externalDir != null && Files.isDirectory(externalDir)) {
            try (var stream = Files.list(externalDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".txt"))
                        .forEach(p -> names.add(p.getFileName().toString()));
            } catch (IOException e) {
                log.warn("[IntentPromptAssembler] 列出外部模板目录失败: {}", e.getMessage());
            }
        }
        try {
            var resources = resolver.getResources("classpath*:" + CLASSPATH_DIR + "*.txt");
            for (var r : resources) {
                String name = r.getFilename();
                if (name != null && name.endsWith(".txt") && !names.contains(name)) {
                    names.add(name);
                }
            }
        } catch (IOException e) {
            log.warn("[IntentPromptAssembler] 列出 classpath 模板失败: {}", e.getMessage());
        }
        return names;
    }

    private record CachedTemplate(String content, long mtime) {
    }
}
