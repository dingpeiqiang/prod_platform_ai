package com.sitech.prodai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景提示词文件管理器 —— 对齐 Python {@code app/services/scene_prompt_manager.py::ScenePromptManager}。
 *
 * <p>Python 版本从 {@code backend/config/prompts/scenes/} 读写提示词文件。
 * Java 版本双轨制：
 * <ul>
 *   <li>读取：优先从 classpath:prompts/scenes/ 读取（对齐 Python 文件数据源）</li>
 *   <li>写入：写入工作目录下的 {@code prompts/scenes/}（Spring Boot 不能写入 jar 内部，
 *       故使用外部目录保存运行时生成的提示词）</li>
 * </ul>
 *
 * <p>外部目录可通过配置项 {@code app.prompt-scenes-dir} 自定义，默认 {@code ./prompts/scenes}。
 */
@Service
public class ScenePromptManager {

    private static final Logger log = LoggerFactory.getLogger(ScenePromptManager.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    private final Path externalBasePath;
    private final Path externalTemplatesDir;

    public ScenePromptManager(@Value("${app.prompt-scenes-dir:prompts/scenes}") String externalDir) {
        this.externalBasePath = Paths.get(externalDir).toAbsolutePath().normalize();
        this.externalTemplatesDir = externalBasePath.resolve("_templates");
        try {
            Files.createDirectories(externalBasePath);
            Files.createDirectories(externalTemplatesDir);
        } catch (IOException e) {
            log.warn("[ScenePromptManager] 初始化目录失败: {}", e.getMessage());
        }
    }

    /** 对齐 Python load_prompt —— 优先外部目录，回退 classpath */
    public String loadPrompt(String promptFile) {
        // 1. 先查外部目录
        Path external = externalBasePath.resolve(promptFile);
        if (Files.exists(external)) {
            try {
                return Files.readString(external, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("[ScenePromptManager] 读取外部提示词失败 {}: {}", promptFile, e.getMessage());
            }
        }
        // 2. 回退 classpath
        try {
            Resource resource = resolver.getResource("classpath:prompts/scenes/" + promptFile);
            if (resource.exists()) {
                try (var in = resource.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            log.warn("[ScenePromptManager] 读取 classpath 提示词失败 {}: {}", promptFile, e.getMessage());
        }
        return null;
    }

    /** 对齐 Python save_prompt —— 写入外部目录 */
    public Map<String, Object> savePrompt(String promptFile, String content) {
        try {
            Files.createDirectories(externalBasePath);
            Path target = externalBasePath.resolve(promptFile);
            Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
            log.info("[ScenePromptManager] 保存提示词: {}", promptFile);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("message", "Prompt saved successfully");
            return body;
        } catch (IOException e) {
            log.error("[ScenePromptManager] 保存提示词失败 {}", promptFile, e);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", str(e));
            return body;
        }
    }

    /** 对齐 Python delete_prompt —— 从外部目录删除 */
    public Map<String, Object> deletePrompt(String promptFile) {
        try {
            Path target = externalBasePath.resolve(promptFile);
            if (Files.exists(target)) {
                Files.delete(target);
                log.info("[ScenePromptManager] 删除提示词: {}", promptFile);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("message", "Prompt deleted successfully");
            return body;
        } catch (IOException e) {
            log.error("[ScenePromptManager] 删除提示词失败 {}", promptFile, e);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", str(e));
            return body;
        }
    }

    /** 对齐 Python list_prompts —— 合并外部目录与 classpath 中的 .txt 文件 */
    public Map<String, Object> listPrompts() {
        List<String> names = new ArrayList<>();
        // 1. 外部目录
        try (var stream = Files.list(externalBasePath)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .filter(p -> !p.getFileName().toString().startsWith("_"))
                    .forEach(p -> names.add(p.getFileName().toString()));
        } catch (IOException e) {
            log.warn("[ScenePromptManager] 列出外部目录失败: {}", e.getMessage());
        }
        // 2. classpath
        try {
            Resource[] resources = resolver.getResources("classpath*:prompts/scenes/*.txt");
            for (Resource r : resources) {
                String name = r.getFilename();
                if (name != null && name.endsWith(".txt") && !name.startsWith("_") && !names.contains(name)) {
                    names.add(name);
                }
            }
        } catch (IOException e) {
            log.warn("[ScenePromptManager] 列出 classpath 提示词失败: {}", e.getMessage());
        }
        names.sort(Comparator.naturalOrder());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", names);
        return body;
    }

    /** 对齐 Python load_template —— 优先外部 _templates 目录，回退 classpath */
    public String loadTemplate(String templateType) {
        String filename = templateType + ".txt";
        // 1. 外部目录
        Path external = externalTemplatesDir.resolve(filename);
        if (Files.exists(external)) {
            try {
                return Files.readString(external, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("[ScenePromptManager] 读取外部模板失败 {}: {}", templateType, e.getMessage());
            }
        }
        // 2. classpath
        try {
            Resource resource = resolver.getResource("classpath:prompts/scenes/_templates/" + filename);
            if (resource.exists()) {
                try (var in = resource.getInputStream()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            log.warn("[ScenePromptManager] 读取 classpath 模板失败 {}: {}", templateType, e.getMessage());
        }
        return null;
    }

    /** 对齐 Python build_prompt —— 替换模板变量 */
    public String buildPrompt(String promptTemplate, Map<String, Object> context) {
        if (promptTemplate == null) {
            return "";
        }
        String result = promptTemplate;
        result = result.replace("{scene_code}", str(context.get("scene_code")));
        result = result.replace("{scene_name}", str(context.get("scene_name")));
        result = result.replace("{form_code}", str(context.get("form_code")));
        result = result.replace("{user_input}", str(context.get("user_input")));
        result = result.replace("{current_date}", LocalDateTime.now().toLocalDate().toString());
        result = result.replace("{current_time}", LocalDateTime.now().toLocalTime().format(TIME_FMT));

        // available_tools / tools_info
        Object availableTools = context.get("available_tools");
        if (availableTools instanceof List<?> list) {
            result = result.replace("{available_tools}", String.join(", ", list.stream()
                    .map(String::valueOf)
                    .toList()));
        } else {
            result = result.replace("{available_tools}", str(availableTools));
        }

        Object toolsInfo = context.get("tools_info");
        if (toolsInfo instanceof String s) {
            result = result.replace("{tools_info}", s);
        } else if (toolsInfo instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object t : list) {
                sb.append("- ").append(t).append("\n");
            }
            result = result.replace("{tools_info}", sb.toString());
        } else if (toolsInfo != null) {
            result = result.replace("{tools_info}", String.valueOf(toolsInfo));
        }
        return result;
    }

    /** 对齐 Python create_prompt_from_template */
    public Map<String, Object> createPromptFromTemplate(String templateType, Map<String, Object> sceneInfo) {
        try {
            String template = loadTemplate(templateType);
            if (template == null) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("success", false);
                body.put("message", "Template " + templateType + " not found");
                return body;
            }
            String promptContent = buildPrompt(template, sceneInfo);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", promptContent);
            return body;
        } catch (Exception e) {
            log.error("[ScenePromptManager] 从模板创建提示词失败 {}", templateType, e);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", str(e));
            return body;
        }
    }

    /** 获取外部基础目录，供调试或外部 API 使用 */
    public Path getExternalBasePath() {
        return externalBasePath;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String str(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
