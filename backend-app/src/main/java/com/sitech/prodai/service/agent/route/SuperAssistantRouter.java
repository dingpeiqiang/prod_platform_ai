package com.sitech.prodai.service.agent.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 超级助手路由器（统一入口 + 自主路由，方案 §3）：scene=auto 时的本轮能力域判定。
 * <p>
 * 三级判定瀑布（确定性优先，LLM 兜底，全程可审计）：
 * <ol>
 *   <li><b>L0 会话路由记忆</b>：上一域存在挂起工作流/未终结事务 → 延续该域（零成本短路，
 *       保证跨轮指代「提交它」「再改一下」不被误路由）；</li>
 *   <li><b>L1 规则快筛</b>：术语级关键词词表（scene_routes.yaml，热更）命中即定域；
 *       多域同时命中 → 不定域交 L2；</li>
 *   <li><b>L2 LLM 场景判定</b>：理解层同一次调用的 scene 字段（{@link #llmScene} 注入，
 *       由编排器/理解层回填），L1 与 L2 冲突时采信 L2 并留痕；</li>
 *   <li><b>兜底</b>：有上一域 → 延续；无上一域 → 默认 ops（与 DEFAULT_SCENE 语义一致）。</li>
 * </ol>
 * <p>
 * 铁律边界：路由判定不产生业务结论——只决定本轮理解层用哪个域的角色提示词/工具白名单，
 * 守门语义（{@code AgentCapabilityRegistry}）不变。判定结果回写会话 routeState，
 * 随 query_plan.route 落库（SessionManager 恢复回读，跨轮/重启不依赖内存）。
 * <p>
 * 词表装载：外部目录（{@code app.prompt-intent-dir}）优先 → classpath 回退（mtime 热更），
 * 与 {@code IntentPromptAssembler} 双轨制语义一致；装载失败自然降级为 L0+兜底（不阻断）。
 */
@Component
public class SuperAssistantRouter {

    private static final Logger log = LoggerFactory.getLogger(SuperAssistantRouter.class);

    /** 超级助手场景标记：请求 scene=auto 时启用自主路由（显式 rd/ops/query 不进本路由器）。 */
    public static final String SCENE_AUTO = "auto";

    /** L2 LLM 判定来源标识。 */
    public static final String SOURCE_LLM = "L2-llm";
    /** L1 词表命中来源标识。 */
    public static final String SOURCE_KEYWORD = "L1-keyword";
    /** L0 挂起态延续来源标识。 */
    public static final String SOURCE_PENDING = "L0-pending";
    /** 兜底延续来源标识。 */
    public static final String SOURCE_FALLBACK = "fallback";

    /** 合法能力域（兜底/回填校验用）。 */
    private static final Set<String> VALID_SCENES = Set.of("rd", "ops", "query");

    /** classpath 词表资源路径。 */
    private static final String CLASSPATH_RESOURCE = "prompts/intent/scene_routes.yaml";

    private final Path externalDir;
    /** 词表缓存：scene → 关键词列表（mtime 热更检测）。 */
    private final Map<String, CachedWordTable> cache = new ConcurrentHashMap<>();

    public SuperAssistantRouter(@Value("${app.prompt-intent-dir:prompts/intent}") String externalDir) {
        this.externalDir = externalDir == null || externalDir.isBlank()
                ? null : Paths.get(externalDir).toAbsolutePath().normalize();
    }

    /** 路由判定结果：effectiveScene 已归一化为合法域；source/reason 供审计与前端透出。 */
    public record RouteDecision(String effectiveScene, String source, String matchedKeyword, String reason) {

        /** 转为会话 routeState / metadata.route 视图（方案 §3.3 可审计结构；键 last_scene
         * 与 SessionContext.lastRoutedScene 对齐——下一轮 L0 延续从本视图读取上一域）。 */
        public Map<String, Object> toView(boolean switched) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("last_scene", effectiveScene);
            view.put("last_source", source);
            if (matchedKeyword != null) {
                view.put("last_keyword", matchedKeyword);
            }
            view.put("switched", switched);
            if (reason != null && !reason.isBlank()) {
                view.put("reason", reason);
            }
            return view;
        }
    }

    // ==================== 判定入口 ====================

    /**
     * 判定本轮 effectiveScene。仅当请求 scene=auto 时由编排器调用。
     * <p>
     * 判定顺序（L2 冲突胜出，方案 §3.1）：L0 挂起延续 → L1 词表快筛 → L2 LLM 判定；
     * L1 与 L2 冲突时<b>采信 L2</b>（LLM 语义理解粒度高于关键词 substring），并留痕 reason；
     * 兜底：延续上一域 / 默认 ops。
     *
     * @param question        用户输入（可 null——L2 合并阶段仅需互校，不再做词表快筛）
     * @param context         会话上下文（读 routeState / 挂起态 / 工单状态）
     * @param llmScene        L2 判定域（理解层解析产物；null = LLM 未参与/未输出）
     * @param llmSceneReason  L2 判定理由（可空）
     * @return 路由决策（永不返回 null/空白域）
     */
    public RouteDecision decide(String question, SessionContextView context,
                                String llmScene, String llmSceneReason) {
        String prev = normalizeScene(context == null ? null : context.lastScene());

        // L0：会话挂起态/进行中事务 → 强制延续上一域（跨轮指代保护）
        if (context != null && context.hasPendingBusiness() && prev != null) {
            return new RouteDecision(prev, SOURCE_PENDING, null, "会话存在未完结事务，延续上一能力域");
        }

        // L1：词表快筛（命中即定域；多域命中 → 交 L2）
        String keywordScene = matchKeywords(question);

        // L2：LLM 判定（与理解层同一次调用输出）——冲突时采信 LLM（方案 §3.1 互校规则）
        String normalizedLlm = normalizeScene(llmScene);
        if (normalizedLlm != null) {
            String reason = llmSceneReason != null && !llmSceneReason.isBlank() ? llmSceneReason : null;
            if (keywordScene != null && !keywordScene.equals(normalizedLlm)) {
                reason = (reason != null ? reason + "；" : "") + "与词表命中冲突，采信大模型判定";
            }
            return new RouteDecision(normalizedLlm, SOURCE_LLM, null, reason);
        }

        // L1 无冲突命中 → 定域
        if (keywordScene != null) {
            return new RouteDecision(keywordScene, SOURCE_KEYWORD, keywordScene, null);
        }

        // 兜底：延续上一域；无记忆 → 默认 ops
        if (prev != null) {
            return new RouteDecision(prev, SOURCE_FALLBACK, null, "未命中词表，延续上一能力域");
        }
        return new RouteDecision("ops", SOURCE_FALLBACK, null, "默认运营场景");
    }

    /**
     * 预判定（LLM 调用前，L0+L1）：返回候选域 + 是否需要 LLM 补判定。
     * <p>
     * 候选域即预填 effectiveScene（理解层提示词按它组装能力清单——LLM 输出 scene 字段
     * 修正候选域时守门语义仍成立：修正后域的工具大概率是候选域的子集或近邻，
     * 越界工具由白名单过滤兜底剔除）。
     */
    public PreDecision preDecide(String question, SessionContextView context) {
        String prev = normalizeScene(context == null ? null : context.lastScene());

        // L0：挂起态/未完结事务 → 延续，无需 LLM 参与
        if (context != null && context.hasPendingBusiness() && prev != null) {
            return new PreDecision(prev, SOURCE_PENDING, false, null);
        }

        // L1：词表命中即定域，无需 LLM 参与
        String keywordScene = matchKeywords(question);
        if (keywordScene != null) {
            return new PreDecision(keywordScene, SOURCE_KEYWORD, false, keywordScene);
        }

        // 未定域：候选 = 延续上一域 / 默认 ops，需 LLM 补判定（L2）
        String candidate = prev != null ? prev : "ops";
        return new PreDecision(candidate, SOURCE_FALLBACK, true,
                prev != null ? "延续上一能力域" : "默认运营场景");
    }

    /** 预判定结果：candidateScene = 预填域；needLlm = 是否要求 LLM 输出 scene 字段（L2）。 */
    public record PreDecision(String candidateScene, String source, boolean needLlm, String keyword) {
    }

    // ==================== L1 词表快筛 ====================

    /** 词表命中判定：任一域任一关键词命中 → 该域；多域命中 → null（交 L2）。 */
    private String matchKeywords(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String lowered = question.toLowerCase(Locale.ROOT);
        String hitScene = null;
        Map<String, List<String>> table = loadTable();
        for (Map.Entry<String, List<String>> entry : table.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (keyword != null && !keyword.isBlank() && lowered.contains(keyword.toLowerCase(Locale.ROOT))) {
                    if (hitScene != null && !hitScene.equals(entry.getKey())) {
                        // 多域命中：冲突交 LLM 判定（方案 §3.1 冲突消解）
                        return null;
                    }
                    hitScene = entry.getKey();
                }
            }
        }
        return hitScene;
    }

    /** 词表加载：外部目录（mtime 热更）→ classpath 回退；失败返回空表（自然降级）。 */
    private Map<String, List<String>> loadTable() {
        if (externalDir != null) {
            Path external = externalDir.resolve("scene_routes.yaml");
            if (Files.exists(external)) {
                try {
                    long mtime = Files.getLastModifiedTime(external).toMillis();
                    CachedWordTable cached = cache.get("table");
                    if (cached != null && cached.mtime == mtime) {
                        return cached.table;
                    }
                    Map<String, List<String>> table = parseYamlTable(Files.readString(external, StandardCharsets.UTF_8));
                    cache.put("table", new CachedWordTable(table, mtime));
                    return table;
                } catch (IOException e) {
                    log.warn("[SuperAssistantRouter] 读取外部词表失败: {}（回退缓存/classpath）", e.getMessage());
                    CachedWordTable cached = cache.get("table");
                    if (cached != null) {
                        return cached.table;
                    }
                }
            }
        }
        try (InputStream in = new PathMatchingResourcePatternResolver()
                .getResource("classpath:" + CLASSPATH_RESOURCE).getInputStream()) {
            return parseYamlTable(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("[SuperAssistantRouter] classpath 词表加载失败（路由降级为 L0+兜底）: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 解析词表 YAML：顶层三域键（rd/ops/query）→ 关键词列表。 */
    @SuppressWarnings("unchecked")
    private Map<String, List<String>> parseYamlTable(String content) {
        Map<String, List<String>> table = new LinkedHashMap<>();
        try {
            Object root = new Yaml().load(content);
            if (root instanceof Map<?, ?> raw) {
                for (String scene : VALID_SCENES) {
                    Object words = raw.get(scene);
                    if (words instanceof List<?> list) {
                        List<String> normalized = new ArrayList<>();
                        for (Object w : list) {
                            if (w != null && !String.valueOf(w).isBlank()) {
                                normalized.add(String.valueOf(w).trim());
                            }
                        }
                        table.put(scene, normalized);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[SuperAssistantRouter] 词表解析失败（路由降级为 L0+兜底）: {}", e.getMessage());
        }
        return table;
    }

    private String normalizeScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return null;
        }
        String s = scene.trim().toLowerCase(Locale.ROOT);
        return VALID_SCENES.contains(s) ? s : null;
    }

    // ==================== 会话视图（解耦 SessionContext，测试友好） ====================

    /** 路由判定所需的会话只读视图（编排器从 SessionContext 适配）。 */
    public interface SessionContextView {

        /** 路由记忆中的上一能力域（无记忆 null）。 */
        String lastScene();

        /** 会话是否存在未完结业务（挂起工作流/未终结工单）——L0 延续依据。 */
        boolean hasPendingBusiness();
    }

    private record CachedWordTable(Map<String, List<String>> table, long mtime) {
    }
}
