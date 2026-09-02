package com.sitech.prodai.service.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAG 环检测公共组件（P3-2，设计文档 §10 组件共享边界）。
 * <p>
 * 合并原先两处同源不同实现：
 * <ul>
 *   <li>Kahn 拓扑排序实现（原 {@code FlowDefinitionValidator.validateAcyclic}）——
 *       优点：能报告环涉及的节点集合，适合定义期守门的可读报错</li>
 *   <li>DFS 三色标记实现（原 {@code DefaultUnderstander.hasDependencyCycle}）——
 *       优点：早停，适合执行前的轻量防御</li>
 * </ul>
 * 两实现语义一致（有环返回 true），统一收口后由调用方按需选择；
 * 均为纯函数（无状态、无外部依赖）。
 */
public final class DagValidator {

    private DagValidator() {
    }

    /**
     * Kahn 拓扑环检测：入度归约后未访问节点数 &gt; 0 即有环。
     *
     * @param nodeIds   全部节点 id
     * @param adjacency 邻接表 source → targets（允许含 nodeIds 之外的 key/值，忽略）
     * @param collector 环涉及节点的收集器（可为 null；命中时收集排序后的节点列表）
     * @return true = 存在环
     */
    public static boolean hasCycleKahn(Set<String> nodeIds, Map<String, List<String>> adjacency,
                                       List<String> collector) {
        Map<String, Integer> inDegree = new HashMap<>();
        nodeIds.forEach(n -> inDegree.put(n, 0));
        adjacency.values().forEach(targets -> targets.forEach(t -> inDegree.merge(t, 1, Integer::sum)));

        Deque<String> queue = new ArrayDeque<>();
        inDegree.entrySet().stream().filter(e -> e.getValue() == 0).forEach(e -> queue.add(e.getKey()));
        int visited = 0;
        while (!queue.isEmpty()) {
            String node = queue.poll();
            visited++;
            for (String next : adjacency.getOrDefault(node, List.of())) {
                Integer remain = inDegree.get(next);
                if (remain == null) {
                    continue;
                }
                if (inDegree.merge(next, -1, Integer::sum) == 0) {
                    queue.add(next);
                }
            }
        }
        if (visited != nodeIds.size()) {
            if (collector != null) {
                inDegree.entrySet().stream()
                        .filter(e -> e.getValue() > 0).map(Map.Entry::getKey).sorted()
                        .forEach(collector::add);
            }
            return true;
        }
        return false;
    }

    /**
     * DFS 三色标记环检测（早停版本）：沿边递归，回边（在栈内节点）即有环。
     *
     * @param deps 邻接表 source → targets
     * @return true = 存在环
     */
    public static boolean hasCycleDfs(Map<String, List<String>> deps) {
        Set<String> visited = new java.util.HashSet<>();
        Set<String> inStack = new java.util.HashSet<>();
        for (String node : deps.keySet()) {
            if (hasCycleDfsVisit(node, deps, visited, inStack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCycleDfsVisit(String node, Map<String, List<String>> deps,
                                            Set<String> visited, Set<String> inStack) {
        if (inStack.contains(node)) {
            return true;
        }
        if (!visited.add(node)) {
            return false;
        }
        inStack.add(node);
        for (String next : deps.getOrDefault(node, List.of())) {
            if (hasCycleDfsVisit(next, deps, visited, inStack)) {
                return true;
            }
        }
        inStack.remove(node);
        return false;
    }

    /** 便捷重载：从 source→targets 边列表建邻接表（忽略空边）。 */
    public static Map<String, List<String>> buildAdjacency(Set<String> nodeIds,
                                                           Iterable<Map.Entry<String, String>> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();
        nodeIds.forEach(n -> adjacency.put(n, new ArrayList<>()));
        edges.forEach(edge -> {
            String source = edge.getKey();
            String target = edge.getValue();
            if (source != null && target != null) {
                adjacency.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
            }
        });
        return adjacency;
    }
}
