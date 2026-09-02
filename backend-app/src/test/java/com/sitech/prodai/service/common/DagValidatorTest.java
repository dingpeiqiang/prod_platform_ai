package com.sitech.prodai.service.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DAG 环检测公共组件测试（P3-2 验收）：Kahn 与 DFS 两实现对同一图语义一致。
 */
class DagValidatorTest {

    @Test
    void kahnDetectsCycleAndCollectsNodes() {
        Set<String> nodes = Set.of("s", "a", "b", "e");
        Map<String, List<String>> adjacency = Map.of(
                "s", List.of("a"), "a", List.of("b"), "b", List.of("a", "e"), "e", List.of());
        List<String> cyclic = new ArrayList<>();
        assertTrue(DagValidator.hasCycleKahn(nodes, adjacency, cyclic));
        // e 依赖环内节点（b→e），环消除后 e 入度仍 >0，也被列入——属预期行为
        assertEquals(List.of("a", "b", "e"), cyclic);
    }

    @Test
    void kahnPassesDag() {
        Set<String> nodes = Set.of("s", "a", "e");
        Map<String, List<String>> adjacency = Map.of(
                "s", List.of("a"), "a", List.of("e"), "e", List.of());
        assertFalse(DagValidator.hasCycleKahn(nodes, adjacency, null));
    }

    @Test
    void dfsDetectsCycle() {
        Map<String, List<String>> deps = new LinkedHashMap<>();
        deps.put("t1", List.of("t2"));
        deps.put("t2", List.of("t1"));
        assertTrue(DagValidator.hasCycleDfs(deps));
    }

    @Test
    void dfsSelfLoopDetected() {
        Map<String, List<String>> deps = Map.of("t1", List.of("t1"));
        assertTrue(DagValidator.hasCycleDfs(deps));
    }

    @Test
    void dfsPassesLinearChain() {
        Map<String, List<String>> deps = Map.of(
                "t1", List.of("t2"), "t2", List.of("t3"), "t3", List.of());
        assertFalse(DagValidator.hasCycleDfs(deps));
    }

    @Test
    void bothImplementationsAgreeOnSameGraph() {
        // 同一图：菱形 + 回边
        Map<String, List<String>> graph = Map.of(
                "s", List.of("a", "b"), "a", List.of("c"), "b", List.of("c"), "c", List.of("s"));
        Set<String> nodes = Set.of("s", "a", "b", "c");
        assertTrue(DagValidator.hasCycleKahn(nodes, graph, null));
        assertTrue(DagValidator.hasCycleDfs(graph));

        // 去掉回边后同为 DAG
        Map<String, List<String>> dag = new HashMap<>(graph);
        dag.put("c", List.of());
        assertFalse(DagValidator.hasCycleKahn(nodes, dag, null));
        assertFalse(DagValidator.hasCycleDfs(dag));
    }

    @Test
    void emptyGraphsAreAcyclic() {
        assertFalse(DagValidator.hasCycleKahn(Set.of(), Map.of(), null));
        assertFalse(DagValidator.hasCycleDfs(Map.of()));
    }
}
