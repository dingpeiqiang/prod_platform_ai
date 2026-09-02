package com.sitech.prodai.service.agent.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流定义图：节点 + 分支条件 + 节点间数据流。
 * <p>
 * 描述一轮对话的实际业务处理流程（而非平铺步骤），供思考时间线渲染为
 * 可审计的工作流视图：每个节点从哪里承接输入、走到哪个分支、产出什么。
 * <p>
 * 数据结构：
 * <pre>
 * nodes: [{id, title, kind, desc, dependsOn:[节点id], branch: {on: "上游节点id", when: "条件说明"}}]
 * edges: [{from, to, when}]  // 条件分支（when 为空表示必经）
 * </pre>
 */
public class WorkflowGraph {

    private final String id;
    private final String title;
    private final List<Map<String, Object>> nodes = new ArrayList<>();
    private final List<Map<String, Object>> edges = new ArrayList<>();

    public WorkflowGraph(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<Map<String, Object>> getNodes() {
        return nodes;
    }

    public List<Map<String, Object>> getEdges() {
        return edges;
    }

    /** 追加工作流节点：id / 标题 / 节点类型（intent|plan|branch|tool|summarize）/ 说明 / 上游依赖节点 id 列表 */
    public WorkflowGraph node(String id, String title, String kind, String desc, String... dependsOn) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("title", title);
        n.put("kind", kind);
        n.put("desc", desc == null ? "" : desc);
        n.put("depends_on", List.of(dependsOn));
        nodes.add(n);
        return this;
    }

    /** 追加条件分支边：from 节点在 when 条件成立时走到 to 节点；when 为空表示必经路径 */
    public WorkflowGraph edge(String from, String to, String when) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("from", from);
        e.put("to", to);
        e.put("when", when == null ? "" : when);
        edges.add(e);
        return this;
    }

    /** 转为可序列化 Map（SSE / 快照落库通用）。 */
    public Map<String, Object> toView() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", id);
        view.put("title", title);
        view.put("nodes", nodes);
        view.put("edges", edges);
        return view;
    }

    /** 查询指定节点的定义（供编排层读取 depends_on / branch 语义）。 */
    public Map<String, Object> nodeById(String nodeId) {
        for (Map<String, Object> n : nodes) {
            if (nodeId.equals(n.get("id"))) {
                return n;
            }
        }
        return null;
    }
}
