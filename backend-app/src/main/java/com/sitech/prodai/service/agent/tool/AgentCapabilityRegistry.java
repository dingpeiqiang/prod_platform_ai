package com.sitech.prodai.service.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 能力注册表（单源）：场景 → 可见工具清单的唯一定义处。
 * <p>
 * 数据源为 {@link AgentTool#getScenes()} 自声明——每个工具声明自己所属的对话场景，
 * 本组件按 Spring 注入的 List&lt;AgentTool&gt; 构建场景索引，取代原先散落在
 * 理解层（DefaultUnderstander）与表达层（DefaultPresenter）的四处同构白名单 Set。
 * <p>
 * 守门语义不变：
 * <ul>
 *   <li>场景能力清单：LLM 只能看到本场景声明的工具（toolsOf）</li>
 *   <li>工具名守门：LLM 输出的工具名必须命中本场景白名单（isVisible）</li>
 * </ul>
 * 新增工具成本 = 实现 {@code getScenes()} 一个方法，无需同步修改任何调用方。
 */
@Component
public class AgentCapabilityRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentCapabilityRegistry.class);

    /** 缺省场景：未带 scene（或无法识别）时按运营场景处理，与既有语义一致。 */
    public static final String DEFAULT_SCENE = "ops";

    private final Map<String, AgentTool> toolMap = new LinkedHashMap<>();
    private final Map<String, List<AgentTool>> sceneIndex = new LinkedHashMap<>();

    public AgentCapabilityRegistry(List<AgentTool> tools) {
        if (tools != null) {
            for (AgentTool tool : tools) {
                this.toolMap.put(tool.getName(), tool);
                for (String scene : tool.getScenes()) {
                    if (scene == null || scene.isBlank()) {
                        continue;
                    }
                    this.sceneIndex.computeIfAbsent(scene, k -> new ArrayList<>()).add(tool);
                }
            }
        }
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        sceneIndex.forEach((scene, list) -> snapshot.put(scene,
                list.stream().map(AgentTool::getName).toList()));
        log.info("[CapabilityRegistry] 场景能力索引构建完成: {}", snapshot);
    }

    /** 场景内的已注册工具（按注册顺序）；scene 空白时回落运营场景。 */
    public List<AgentTool> toolsOf(String scene) {
        List<AgentTool> list = sceneIndex.get(normalizeScene(scene));
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /** 工具是否对场景可见（白名单守门：防 LLM 编造工具名/跨场景调用）。 */
    public boolean isVisible(String toolName, String scene) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        List<AgentTool> list = sceneIndex.get(normalizeScene(scene));
        if (list == null) {
            return false;
        }
        return list.stream().anyMatch(t -> toolName.equals(t.getName()));
    }

    /** 工具是否属于指定场景（等价 isVisible 的工具名维度便捷方法）。 */
    public boolean belongsToScene(String toolName, String scene) {
        return isVisible(toolName, scene);
    }

    /** 工具声明的全部场景（只读）；未注册/未声明返回空集。 */
    public Set<String> scenesOf(String toolName) {
        AgentTool tool = toolName == null ? null : toolMap.get(toolName);
        if (tool == null) {
            return Set.of();
        }
        Set<String> scenes = new LinkedHashSet<>(tool.getScenes());
        scenes.removeIf(s -> s == null || s.isBlank());
        return Collections.unmodifiableSet(scenes);
    }

    /** 场景归一化：空白场景回落运营场景（与理解层/表达层既有语义一致）；未知场景严格守门返回空清单。 */
    private String normalizeScene(String scene) {
        return scene == null || scene.isBlank() ? DEFAULT_SCENE : scene;
    }
}
