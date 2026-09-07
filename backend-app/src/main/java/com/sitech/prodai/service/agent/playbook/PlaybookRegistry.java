package com.sitech.prodai.service.agent.playbook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手册（Playbook）注册表：完成一类事情的标准作业程序——步骤 + 每步操作方法 + 使用工具。
 * <p>
 * 设计理念对齐 Anthropic SKILL.md（手册作为知识，Agent 照着办事），取舍：
 * <ul>
 *   <li>双读者：YAML 人可读、LLM 可读（拒绝 BPMN XML 重规范）</li>
 *   <li>双消费：① 路由视图（applies_to 显式适用域 → SceneFlowRouter 按意图分流）
 *              ② 编排视图（渲染 SOP 文本 → 理解层/表达层 prompt 注入，LLM 照手册调工具）</li>
 *   <li>可解析约束：steps[].tool / applies_to.tools 引用必须可解析（MCP 约束，装载门禁校验）</li>
 * </ul>
 * <p>
 * 装载：classpath:playbooks/*.yaml，启动时全量加载 + 校验，问题仅告警不阻断启动
 * （手册缺失时各消费方自然降级——路由回落场景配置，编排回落无 SOP 动态模式）。
 */
@Component
public class PlaybookRegistry {

    private static final Logger log = LoggerFactory.getLogger(PlaybookRegistry.class);

    /** 手册 code → 原始 YAML 映射（保持字段原样，消费方按需取） */
    private final Map<String, Map<String, Object>> playbooks = new ConcurrentHashMap<>();
    /** 装载期问题清单（code → 问题），供运维排查与测试断言 */
    private final Map<String, List<String>> loadProblems = new ConcurrentHashMap<>();
    /** 装载两遍法的第一遍暂存（code → 原始 YAML），reload 结束后清空 */
    private final Map<String, Map<String, Object>> pendingBooks = new LinkedHashMap<>();
    /** 步骤片段库约定文件名（下划线前缀 = 非手册，不进注册表） */
    private static final String FRAGMENT_LIB = "_shared-steps";

    /**
     * 已注册 AgentTool 名单（MCP 可解析约束的校验依据）。
     * 引擎装配后经 {@link #registerKnownTools} 注入；装载早于工具装配时校验降级为"跳过工具引用校验"。
     */
    private volatile Set<String> knownTools = Set.of();

    /** 注入已注册工具名单（DefaultExecutor 装配完成后调用一次）。 */
    public void registerKnownTools(Set<String> toolNames) {
        if (toolNames != null && !toolNames.isEmpty()) {
            this.knownTools = Set.copyOf(toolNames);
        }
    }

    // ==================== 装载 ====================

    @jakarta.annotation.PostConstruct
    public void init() {
        reload();
    }

    /** 全量重载（运维改手册后可经管理端点触发，无需重启）。 */
    public synchronized void reload() {
        playbooks.clear();
        loadProblems.clear();
        Yaml yaml = new Yaml();
        Map<String, Map<String, Object>> fragments = new LinkedHashMap<>();
        try {
            var resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:playbooks/*.yaml");
            // 两遍装载：第一遍收集步骤片段库（下划线前缀文件，非手册），第二遍装载手册并展开引用
            for (var resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                String code = filename.substring(0, filename.length() - 5);
                try (InputStream in = resource.getInputStream()) {
                    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    Object root = yaml.load(content);
                    if (!(root instanceof Map<?, ?> raw)) {
                        problem(code, "顶层必须是 YAML 映射");
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> book = (Map<String, Object>) raw;
                    if (code.startsWith("_")) {
                        // 片段库：不进注册表（对路由/触发词/SOP 消费不可见），仅作步骤复用源
                        fragments.put(code, book);
                        log.info("[PlaybookRegistry] 步骤片段库已装载: {} ({})", code, book.get("title"));
                        continue;
                    }
                    pendingBooks.put(code, book);
                } catch (Exception e) {
                    problem(code, "解析失败: " + e.getMessage());
                }
            }
            // 第二遍：展开步骤引用后校验、入册
            pendingBooks.forEach((code, book) -> {
                List<String> problems = resolveStepRefs(code, book, fragments);
                problems.addAll(validate(code, book));
                if (!problems.isEmpty()) {
                    problems.forEach(p -> problem(code, p));
                    return;
                }
                playbooks.put(code, book);
                log.info("[PlaybookRegistry] 手册已装载: {} ({})", code, book.get("title"));
            });
        } catch (Exception e) {
            log.warn("[PlaybookRegistry] 手册目录加载失败: {}", e.getMessage());
        } finally {
            pendingBooks.clear();
        }
        log.info("[PlaybookRegistry] 装载完成 playbooks={}, problems={}", playbooks.size(), loadProblems.size());
    }

    /**
     * 步骤片段引用展开（装载期一次性替换，运行期视图无感知）：
     * steps 项形如 {@code use: query-facts}（可选 {@code do/how 覆写}）时，
     * 从片段库取同名片段展开——覆写字段优先（手册可微调措辞而不复制全量定义）。
     * 装载期展开使 route/renderSop/runPlaybookPath 等消费方仍读展开后的完整步骤，
     * 片段机制对双消费 API 完全透明。
     */
    @SuppressWarnings("unchecked")
    private List<String> resolveStepRefs(String code, Map<String, Object> book,
                                         Map<String, Map<String, Object>> fragments) {
        List<String> problems = new ArrayList<>();
        if (!(book.get("steps") instanceof List<?> steps)) {
            return problems;
        }
        List<Object> resolved = new ArrayList<>();
        for (Object o : steps) {
            if (o instanceof Map<?, ?> rawStep && rawStep.get("use") instanceof String ref) {
                String fragment = ref.startsWith("steps.") ? ref.substring("steps.".length()) : ref;
                Map<String, Object> lib = fragments.get(FRAGMENT_LIB);
                // 片段库 steps 是「片段名 → 模板」映射（与手册的步骤列表不同构）
                Object template = lib != null && lib.get("steps") instanceof Map<?, ?> stepsLib
                        ? stepsLib.get(fragment) : null;
                if (!(template instanceof Map<?, ?> tpl)) {
                    problems.add("步骤引用不可解析: " + fragment + "（片段库 " + FRAGMENT_LIB + " 中不存在）");
                    resolved.add(rawStep);
                    continue;
                }
                Map<String, Object> step = new LinkedHashMap<>();
                // 片段模板展开后步骤 id 缺省取片段名（手册内步骤 id 保持唯一，validate 门禁复检）
                tpl.forEach((k, v) -> step.put(String.valueOf(k), v));
                step.putIfAbsent("id", fragment);
                // 覆写字段优先于片段模板（手册可微调 do/how 而不复制全量定义）；use 键不进结果
                for (Map.Entry<?, ?> e : rawStep.entrySet()) {
                    if (!"use".equals(e.getKey())) {
                        step.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                resolved.add(step);
            } else {
                resolved.add(o);
            }
        }
        book.put("steps", resolved);
        return problems;
    }

    /** 装载门禁校验：结构完整性 + 引用可解析。返回问题清单（空 = 通过）。 */
    private List<String> validate(String code, Map<String, Object> book) {
        List<String> problems = new ArrayList<>();
        if (!code.equals(str(book.get("name")))) {
            problems.add("name 与文件名不一致（name=" + book.get("name") + "）");
        }
        Object appliesTo = book.get("applies_to");
        if (!(appliesTo instanceof Map<?, ?> at)) {
            problems.add("缺少 applies_to 适用域声明");
        } else {
            boolean hasIntent = at.get("intents") instanceof List<?> l && !l.isEmpty();
            boolean hasTool = at.get("tools") instanceof List<?> l && !l.isEmpty();
            if (!hasIntent && !hasTool) {
                problems.add("applies_to.intents / applies_to.tools 至少声明一项");
            }
        }
        if (!(book.get("steps") instanceof List<?> steps) || steps.isEmpty()) {
            problems.add("缺少 steps 步骤定义");
            return problems;
        }
        Set<String> stepIds = new HashSet<>();
        Set<String> referencedTools = new HashSet<>();
        for (Object o : steps) {
            if (!(o instanceof Map<?, ?> step)) {
                problems.add("steps 含非映射项");
                continue;
            }
            String id = str(step.get("id"));
            if (id.isBlank()) {
                problems.add("步骤缺少 id");
            } else if (!stepIds.add(id)) {
                problems.add("步骤 id 重复: " + id);
            }
            if (str(step.get("do")).isBlank()) {
                problems.add("步骤 " + id + " 缺少 do（做什么）");
            }
            String tool = str(step.get("tool"));
            if (tool.isBlank()) {
                problems.add("步骤 " + id + " 缺少 tool（用什么工具）");
            } else {
                referencedTools.add(tool);
            }
        }
        // 工具引用可解析（MCP 约束）；knownTools 未注入时降级跳过
        if (!knownTools.isEmpty()) {
            for (String tool : referencedTools) {
                if (!knownTools.contains(tool)) {
                    problems.add("工具引用不可解析: " + tool + "（未注册的 AgentTool）");
                }
            }
        }
        // 人工门引用的步骤必须存在
        if (book.get("guardrails") instanceof Map<?, ?> guard) {
            if (guard.get("human_gate") instanceof List<?> gates) {
                for (Object gate : gates) {
                    if (gate instanceof String s && !stepIds.contains(s)) {
                        problems.add("guardrails.human_gate 引用不存在的步骤: " + s);
                    }
                }
            }
        }
        return problems;
    }

    private void problem(String code, String message) {
        loadProblems.computeIfAbsent(code, k -> new ArrayList<>()).add(message);
        log.warn("[PlaybookRegistry] 手册 {} 装载问题: {}", code, message);
    }

    // ==================== 查询（消费方 API） ====================

    /** 全部已装载手册（code → YAML 映射）。 */
    public Map<String, Map<String, Object>> all() {
        return Map.copyOf(playbooks);
    }

    /**
     * 触发词快筛（入口三级瀑布第一级）：用户话术包含任一手册触发词 → 返回命中的手册 code。
     * <p>
     * 路由器在 LLM 理解<b>之前</b>调用本方法——命中即零 LLM 成本直达该手册链路，
     * 兼具消除 LLM 误判与降低时延两个收益。多个手册同时命中时取触发词最长者
     * （「批量导入文档」优先于「导入」）；未命中返回 null。
     */
    public String matchTrigger(String scene, String question) {
        if (question == null || question.isBlank() || scene == null || scene.isBlank()) {
            return null;
        }
        String lowered = question.toLowerCase();
        String bestCode = null;
        int bestLen = 0;
        for (Map.Entry<String, Map<String, Object>> e : playbooks.entrySet()) {
            if (!(e.getValue().get("applies_to") instanceof Map<?, ?> at)) {
                continue;
            }
            String bookScene = str(at.get("scene"));
            if (!bookScene.isBlank() && !bookScene.equals(scene)) {
                continue;
            }
            if (!(at.get("triggers") instanceof List<?> triggers)) {
                continue;
            }
            for (Object t : triggers) {
                String trigger = str(t).toLowerCase();
                if (!trigger.isBlank() && lowered.contains(trigger) && trigger.length() > bestLen) {
                    bestCode = e.getKey();
                    bestLen = trigger.length();
                }
            }
        }
        return bestCode;
    }

    public Map<String, Object> get(String code) {
        return playbooks.get(code);
    }

    /** 装载问题清单（测试断言与运维排查）。 */
    public Map<String, List<String>> problems() {
        return Map.copyOf(loadProblems);
    }

    /**
     * 路由判定：给定场景 + 意图（+ 工具名），返回命中的手册 code；未命中 null。
     * <p>
     * 匹配优先级：intent 命中 > tool 命中（同场景内）；场景必须一致（声明了 scene 时）。
     * 意图匹配忽略大小写——LLM 意图经归一化为小写（product_ops_query），而手册
     * applies_to.intents 按展示惯例声明为大写（PRODUCT_OPS_QUERY），严格 equals 永不命中。
     * 这是 {@code SceneFlowRouter} 按意图分流的依据——手册显式声明自己适用什么，
     * 路由器不再按场景一刀切。
     */
    public String route(String scene, String intent, List<String> toolNames) {
        return route(scene, intent, toolNames, "");
    }

    /**
     * 路由判定（含话术判别）：意图命中多本手册时，按各手册 intent_guide 归口话术特征
     * 与用户话术的匹配度二跳判定——手册即意图定义源（intent_guide 声明"什么话术归本手册"），
     * 判定依据是声明而非注册序（Map 迭代序脆弱，拆分后同码手册互为镜像）。
     * <p>
     * 判定规则（语义匹配，substring 命中计分）：
     * <ul>
     *   <li>唯一意图命中 → 直接返回（多数场景无歧义，不加判别成本）</li>
     *   <li>多本命中 → 逐本对照 intent_guide 话术特征与用户话术求交集，取命中特征最多者；
     *       全部零命中（话术与归口描述都对不上）→ 注册序首本兜底（退化为旧行为）</li>
     * </ul>
     * 话术判别是词面交集而非 LLM 二跳：intent_guide 本身就是 LLM 选码依据，
     * 已按话术语义归口到意图码；同码歧义是码内子形态，词面判定足够且零额外成本。
     *
     * @param question 用户话术（原话），用于同码歧义判定；空则退化为注册序
     */
    public String route(String scene, String intent, List<String> toolNames, String question) {
        String firstHit = null;
        String bestByUtterance = null;
        int bestScore = 0;
        for (Map.Entry<String, Map<String, Object>> e : playbooks.entrySet()) {
            Map<String, Object> book = e.getValue();
            if (!(book.get("applies_to") instanceof Map<?, ?> at)) {
                continue;
            }
            String bookScene = str(at.get("scene"));
            if (!bookScene.isBlank() && scene != null && !bookScene.equals(scene)) {
                continue;
            }
            boolean intentHit = intent != null && at.get("intents") instanceof List<?> intents
                    && intents.stream().map(this::str).anyMatch(i -> i.equalsIgnoreCase(intent));
            boolean toolHit = false;
            if (!intentHit && toolNames != null && at.get("tools") instanceof List<?> tools) {
                for (String t : toolNames) {
                    if (tools.stream().map(this::str).anyMatch(t::equals)) {
                        toolHit = true;
                        break;
                    }
                }
            }
            if (!intentHit && !toolHit) {
                continue;
            }
            if (firstHit == null) {
                firstHit = e.getKey();
            }
            if (intentHit && firstHit != null && question != null && !question.isBlank()
                    && at.get("intent_guide") instanceof Map<?, ?> guide && !guide.isEmpty()) {
                int score = guideUtteranceScore(guide, question);
                if (score > bestScore) {
                    bestScore = score;
                    bestByUtterance = e.getKey();
                }
            }
        }
        if (bestByUtterance != null) {
            return bestByUtterance;
        }
        return firstHit;
    }

    /** intent_guide 归口话术特征与用户话术的词面交集数（小写 substring 计分）。 */
    private int guideUtteranceScore(Map<?, ?> guide, String question) {
        String lowered = question.toLowerCase();
        int score = 0;
        for (Object v : guide.values()) {
            String desc = str(v).toLowerCase();
            // 归口描述形如「风险稽核/合规筛查/该不该下架等在架商品风险判定」——按分隔符切特征词，
            // 末段「等…」为概括性收尾非独立话术特征，不参与计分
            for (String seg : desc.split("[/、，,；;]")) {
                String feature = seg.trim();
                int etcIdx = feature.indexOf("等");
                if (etcIdx >= 0) {
                    feature = feature.substring(0, etcIdx);
                }
                if (feature.length() >= 2 && lowered.contains(feature)) {
                    score++;
                }
            }
        }
        return score;
    }

    /**
     * 编排视图：渲染手册为 SOP 文本（system prompt 注入用）。
     * <p>
     * 输出形如「【标准作业程序：文档批量导入配置】
     * 第1步 解析文档提取文本——按文件类型选引擎……（工具：rd_file_parse）
     * 护栏：……」——LLM 照此执行，人也能直接读懂。
     * <p>
     * 手册声明了 intent_guide（意图归口）时追加「意图归口」段——手册即意图定义源，
     * LLM 依据话术语义对照归口声明输出 intent（而非自由发挥），路由按声明匹配。
     */
    public String renderSop(String code) {
        Map<String, Object> book = playbooks.get(code);
        if (book == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("【标准作业程序：").append(str(book.get("title"))).append("】\n");
        if (book.get("applies_to") instanceof Map<?, ?> at
                && at.get("intent_guide") instanceof Map<?, ?> guide && !guide.isEmpty()) {
            sb.append("意图归口（选本手册时 intent 必须从下列码中按话术语义选一个）：\n");
            guide.forEach((k, v) -> sb.append("- ").append(k).append("：").append(v).append('\n'));
        }
        if (book.get("steps") instanceof List<?> steps) {
            int no = 0;
            for (Object o : steps) {
                if (!(o instanceof Map<?, ?> step)) {
                    continue;
                }
                no++;
                sb.append("第").append(no).append("步 ").append(str(step.get("do")));
                String how = str(step.get("how"));
                if (!how.isBlank()) {
                    sb.append("——").append(how);
                }
                String tool = str(step.get("tool"));
                if (!tool.isBlank()) {
                    sb.append("（工具：").append(tool).append("）");
                }
                String policy = str(step.get("policy"));
                if (!policy.isBlank()) {
                    sb.append("；约束：").append(policy);
                }
                sb.append('\n');
            }
        }
        if (book.get("guardrails") instanceof Map<?, ?> guard) {
            List<String> guards = new ArrayList<>();
            if (guard.get("audit") != null) {
                guards.add("审计：" + str(guard.get("audit")));
            }
            if (guard.get("fallback") instanceof List<?> fallbacks && !fallbacks.isEmpty()) {
                List<String> items = new ArrayList<>();
                fallbacks.forEach(f -> items.add(str(f)));
                guards.add("兜底：" + String.join("；", items));
            }
            if (!guards.isEmpty()) {
                sb.append("护栏：").append(String.join("；", guards)).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /** 按场景列出可用手册 code（前端/管理端展示）；场景为空时不匹配任何手册（适用域显式声明，无通配）。 */
    public List<String> codesForScene(String scene) {
        List<String> out = new ArrayList<>();
        if (scene == null || scene.isBlank()) {
            return out;
        }
        for (Map.Entry<String, Map<String, Object>> e : playbooks.entrySet()) {
            if (e.getValue().get("applies_to") instanceof Map<?, ?> at
                    && (str(at.get("scene")).isBlank() || str(at.get("scene")).equals(scene))) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
