# -*- coding: utf-8 -*-
"""为剩余 Controller 批量添加 Swagger 中文注解（构建期一次性工具，幂等）。"""
import io
import os

BASE = 'backend-app/src/main/java/com/sitech/prodai/controller'

# controller -> (tag_name, tag_desc, {method_anchor: operation})
PLANS = {
    'McpManagementController.java': (
        'MCP 工具管理',
        'MCP 工具注册表查询、调用统计/日志、外部 HTTP 工具 CRUD 与启停、OpenAPI 规范导入',
        {
            '@GetMapping("/tools")': ('工具清单', '返回已注册的全部 MCP 工具（内置 AgentTool + 外部工具，含参数契约）'),
            '@GetMapping("/stats")': ('调用统计', '返回各工具调用次数/成功率/平均耗时聚合统计'),
            '@GetMapping("/categories")': ('工具分类', '返回工具分类清单及其计数'),
            '@GetMapping("/logs")': ('调用日志', '分页返回工具调用明细日志（时间/入参/出参/耗时/状态）'),
            '@PostMapping("/tools/{toolName}/test")': ('工具测试', '按给定入参真实执行一次工具调用，返回执行结果'),
            '@GetMapping("/external-tools")': ('外部工具列表', '返回 DB 中登记的外部 HTTP 工具清单'),
            '@GetMapping("/external-tools/{toolName}")': ('外部工具详情', '按 toolName 返回外部工具定义（URL/方法/鉴权/Schema）'),
            '@PostMapping("/external-tools")': ('创建外部工具', 'body: tool_name/url/request_method/auth_type/input_schema 等'),
            '@PutMapping("/external-tools/{toolName}")': ('更新外部工具', '按 toolName 更新外部工具定义'),
            '@DeleteMapping("/external-tools/{toolName}")': ('删除外部工具', '按 toolName 删除外部工具登记'),
            '@PostMapping("/external-tools/{toolName}/toggle")': ('启停外部工具', '切换外部工具启用/禁用状态'),
            '@PostMapping("/external-tools/parse")': ('OpenAPI 规范解析', '提交 OpenAPI/Swagger 规范文本，解析为工具定义预览（导入前校验）'),
            '@PostMapping("/external-tools/import")': ('外部工具批量导入', '将解析后的工具定义批量落库登记'),
        },
    ),
    'ToolExecutionController.java': (
        '工具执行',
        '工作流编辑器与流程引擎直接执行注册的 AgentTool（结构化入参，非自由文本）',
        {
            '@GetMapping\n    public ApiResponse<List<Map<String, Object>>> list()': ('工具注册清单', '返回工具名称/中文描述/参数契约/输出契约，供工具节点选择与参数表单渲染'),
            '@PostMapping("/{toolName}/execute")': ('执行单个工具', 'params 为结构化入参 JSON，返回 ExecutionResult 同构结果'),
        },
    ),
    'FlowEngineController.java': (
        '流程引擎',
        '固定流程引擎：流程实例执行、人工介入恢复、取消与执行日志查询',
        {
            '@PostMapping("/executions")': ('发起流程执行', 'body: flow_code/initial_context，创建执行实例并开始运行'),
            '@PostMapping("/executions/{executionId}/resume")': ('恢复执行', '实例暂停后自动恢复执行'),
            '@PostMapping("/executions/{executionId}/human-resume")': ('人工介入恢复', 'body: 人工填写的节点输出/确认数据，从挂起节点继续'),
            '@GetMapping("/executions/{executionId}")': ('执行详情', '返回执行实例状态/上下文/当前节点'),
            '@GetMapping("/executions/{executionId}/node-logs")': ('节点日志', '返回该实例各节点的执行日志（入参/出参/耗时/状态）'),
            '@GetMapping("/executions")': ('执行列表', '分页返回执行实例（按状态/流程/时间筛选）'),
            '@PostMapping("/executions/{executionId}/cancel")': ('取消执行', '终止运行中的执行实例（不可恢复）'),
        },
    ),
    'ChatHistoryController.java': (
        '会话历史',
        '会话与消息记录查询：会话列表、消息明细、会话检索',
        {
            '@GetMapping("/sessions")': ('会话列表', '按用户分页返回会话（标题/时间/标签）'),
            '@GetMapping("/sessions/{sessionId}/messages")': ('会话消息', '返回指定会话的全部消息（按 sort_order 排序）'),
            '@DeleteMapping("/sessions/{sessionId}")': ('删除会话', '级联删除会话及其消息与元数据'),
            '@GetMapping("/search")': ('历史检索', '跨会话全文检索历史消息'),
        },
    ),
    'KnowledgeBaseController.java': (
        '知识库',
        '文件知识库：知识条目添加、检索问答、文档明细管理',
        {
            '@GetMapping("/stats")': ('知识库统计', '返回条目总数/切片总数/最近更新时间'),
            '@PostMapping("/add")': ('新增知识条目', 'body: title/content/metadata，文本切片后入库'),
            '@PostMapping("/search")': ('知识检索', '按关键词检索知识切片，返回命中片段与得分'),
            '@PostMapping("/qa")': ('知识问答', '基于知识库检索增强的问答接口'),
            '@GetMapping("/document/{entryId}")': ('文档详情', '返回知识条目原文与切片明细'),
            '@DeleteMapping("/document/{entryId}")': ('删除知识条目', '按 entryId 删除条目及其切片'),
        },
    ),
    'ConfigController.java': (
        '系统配置',
        '运行时配置：本体清单、应用信息、数据源状态、导入导出',
        {
            '@GetMapping("/ontologies")': ('本体配置清单', '返回可用本体资产配置'),
            '@GetMapping("/app")': ('应用信息', '返回应用名/版本/构建信息'),
            '@GetMapping("/datasource")': ('数据源状态', '返回本体/指标库等数据源连接状态'),
            '@PostMapping("/reload")': ('重载配置', '重载运行时配置与图谱资源'),
            '@GetMapping("/import/list")': ('导入清单', '返回可导入的表单配置清单'),
            '@GetMapping("/import/template/{formCode}")': ('导入模板下载', '按 formCode 返回 Excel 导入模板'),
            '@PostMapping("/import/upload")': ('上传导入文件', 'multipart 上传 Excel 导入文件并预校验'),
            '@PostMapping("/import/execute")': ('执行导入', '执行已上传文件的导入（返回成功/失败明细）'),
            '@GetMapping("/export/{formCode}")': ('数据导出', '按 formCode 导出数据为 Excel'),
        },
    ),
}


def inject(path, tag_name, tag_desc, ops):
    with io.open(path, encoding='utf-8') as f:
        src = f.read()

    if 'io.swagger.v3.oas.annotations.Operation' in src:
        print('skip (already):', path)
        return

    # 插 import（放在首个 org.springframework.web import 前）
    anchor_import = 'import org.springframework.web.bind.annotation.'
    if anchor_import in src:
        src = src.replace(
            anchor_import,
            'import io.swagger.v3.oas.annotations.Operation;\n'
            'import io.swagger.v3.oas.annotations.tags.Tag;\n' + anchor_import,
            1)
    else:
        print('warn: no import anchor in', path)

    # 插 @Tag（放在 @RestController 前）
    if '@RestController' in src:
        src = src.replace(
            '@RestController',
            f'@Tag(name = "{tag_name}", description = "{tag_desc}")\n@RestController',
            1)
    else:
        print('warn: no @RestController in', path)

    # 逐个方法插 @Operation（仅注解级匹配，避免误插 import 行）
    hit = 0
    for anchor, (summary, desc) in ops.items():
        needle = f'@GetMapping("{anchor}")' if False else anchor
        # 支持裸注解匹配（多行 anchor 已含完整注解）
        if needle in src:
            src = src.replace(needle, f'@Operation(summary = "{summary}", description = "{desc}")\n' + needle, 1)
            hit += 1
        else:
            print('warn: anchor not found:', anchor, 'in', os.path.basename(path))

    with io.open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(src)
    print(f'ok: {os.path.basename(path)} ({hit}/{len(ops)})')


def main():
    for name, (tag, desc, ops) in PLANS.items():
        path = os.path.join(BASE, name)
        if not os.path.exists(path):
            print('missing:', name)
            continue
        inject(path, tag, desc, ops)


if __name__ == '__main__':
    main()
