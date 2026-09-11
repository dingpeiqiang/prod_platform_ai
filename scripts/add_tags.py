# -*- coding: utf-8 -*-
"""One-off script: add @Tag/@Operation annotations to controllers missing them."""

import re

BASE = r'backend-app/src/main/java/com/sitech/prodai/controller/'


def add_imports(src):
    if 'io.swagger.v3.oas.annotations.tags.Tag' not in src:
        # insert after package line's first import
        src = src.replace('import org.springframework.',
                          'import io.swagger.v3.oas.annotations.Operation;\n'
                          'import io.swagger.v3.oas.annotations.tags.Tag;\n'
                          'import org.springframework.', 1)
    return src


def add_tag(src, tag_line):
    if '@Tag(name' in src:
        return src
    # insert before @RestController
    return src.replace('@RestController', tag_line + '\n@RestController', 1)


def add_ops(src, ops):
    """ops: list of (mapping_pattern, summary, description)."""
    inserted = 0
    for pattern, summary, desc in ops:
        if pattern not in src:
            print('  NOT FOUND:', pattern)
            continue
        idx = src.find(pattern)
        line_start = src.rfind('\n', 0, idx) + 1
        indent = src[line_start:idx]
        prev = src[:line_start].rstrip().split('\n')[-1]
        if '@Operation' in prev:
            continue
        op_line = f'{indent}@Operation(summary = "{summary}", description = "{desc}")\n'
        src = src[:idx] + op_line + src[idx:]
        inserted += 1
    return src, inserted


def process(fname, tag, ops):
    path = BASE + fname
    src = open(path, encoding='utf-8').read()
    src = add_imports(src)
    src = add_tag(src, tag)
    src, n = add_ops(src, ops)
    open(path, 'w', encoding='utf-8', newline='').write(src)
    print(f'{fname}: {n} operations added')


# ---------------- AdminController ----------------
process('AdminController.java',
        '@Tag(name = "场景与提示词管理", description = "后台管理 API：场景查询（只读）、提示词 CRUD、本体管理")',
        [
            ('@GetMapping("/scenes/tree")', '场景树', '返回场景层级树，可按启用状态过滤'),
            ('@GetMapping("/scenes")', '场景列表', '返回场景清单，可按启用状态过滤'),
            ('@GetMapping("/scenes/stats/summary")', '场景统计', '返回场景数量/启用状态等汇总统计'),
            ('@GetMapping("/scenes/{sceneCode}")', '场景详情', '按场景编码查询场景详情'),
            ('@PostMapping("/scenes/test")', '场景测试', '对指定场景执行测试请求'),
            ('@GetMapping("/prompts/categories")', '提示词分类', '返回提示词分类清单'),
            ('@GetMapping("/prompts")', '提示词列表', '分页/分类查询提示词清单'),
            ('@GetMapping("/prompts/{code}")', '提示词详情', '按编码查询提示词详情'),
            ('@PostMapping("/prompts")', '创建提示词', '新增提示词模板'),
            ('@PutMapping("/prompts/{code}")', '更新提示词', '按编码更新提示词内容'),
            ('@DeleteMapping("/prompts/{code}")', '删除提示词', '按编码删除提示词'),
            ('@GetMapping("/prompts/{code}/versions")', '提示词版本历史', '返回提示词历史版本列表'),
            ('@PostMapping("/prompts/{code}/preview")', '提示词预览', '按变量渲染提示词并返回预览结果'),
            ('@PostMapping("/prompts/generate")', 'AI 生成提示词', '由 LLM 依据需求生成提示词'),
            ('@PostMapping("/prompts/optimize")', 'AI 优化提示词', '由 LLM 优化既有提示词'),
            ('@PostMapping("/prompts/reload")', '重载提示词', '热重载提示词配置'),
            ('@GetMapping("/ontologies/categories")', '本体分类', '返回本体分类清单'),
            ('@GetMapping("/ontologies")', '本体列表', '分页/分类查询本体清单'),
            ('@GetMapping("/ontologies/{ontologyCode}")', '本体详情', '按编码查询本体详情'),
            ('@PostMapping("/ontologies")', '创建本体', '新增本体'),
            ('@PutMapping("/ontologies/{ontologyCode}")', '更新本体', '按编码更新本体'),
            ('@DeleteMapping("/ontologies/{ontologyCode}")', '删除本体', '按编码删除本体'),
            ('@PatchMapping("/ontologies/{ontologyCode}/toggle")', '启停本体', '切换本体启用/停用状态'),
        ])

# ---------------- AppStoreController ----------------
process('AppStoreController.java',
        '@Tag(name = "应用商店", description = "应用商店智能配置：商品配置查询、CRM/计费配置生成、规则校验、测试用例、验收与告警")',
        [
            ('@GetMapping("/products/config/query")', '商品配置查询', '按关键字查询商品配置'),
            ('@PostMapping("/crm/config/generate")', 'CRM 配置生成', 'AI 生成 CRM 配置'),
            ('@PostMapping("/billing/config/generate")', '计费配置生成', 'AI 生成计费配置'),
            ('@PostMapping("/rules/verify")', '计费规则校验', '校验计费规则合法性'),
            ('@PostMapping("/rules/config")', '计费规则配置', '配置计费规则'),
            ('@PostMapping("/spec/audit")', '商品规格审计', '审计商品规格'),
            ('@PostMapping("/cases/generate")', '测试用例生成', 'AI 生成测试用例'),
            ('@PostMapping("/cases/execute")', '测试用例执行', '执行已生成的测试用例'),
            ('@GetMapping("/tasks/{task_id}")', '任务查询', '按任务 ID 查询异步任务结果'),
            ('@PostMapping("/order/verify")', '订单验收核验', '订单验收核验'),
            ('@PostMapping("/approval/submit")', '发布审批提交', '提交发布审批'),
            ('@GetMapping("/product/monitor")', '商品监控', '按商品 ID 查询监控数据'),
            ('@PostMapping("/alert/send")', '告警发送', '发送告警通知'),
        ])

# ---------------- ProductCenterController ----------------
process('ProductCenterController.java',
        '@Tag(name = "产商品中心", description = "产商品运营图谱与指标：运营图查询、契约指标、ETL 与阈值校准")',
        [
            ('@GetMapping({"/ops-graph", "/api/v1/product-center/ops-graph"})', '运营图谱', '返回产商品运营图谱'),
            ('@GetMapping({"/api/v1/product-center/ops-graph/contract", "/ops-graph/contract"})', '运营图谱契约', '返回运营图谱数据契约'),
            ('@GetMapping("/api/v1/product-center/metrics/contract")', '指标契约', '返回指标契约定义'),
            ('@PostMapping("/api/v1/product-center/metrics/etl")', '指标 ETL 执行', '触发指标 ETL 加工'),
            ('@GetMapping("/api/v1/product-center/metrics/etl/last-run")', '最近 ETL 结果', '返回最近一次指标 ETL 运行结果'),
            ('@PostMapping("/api/v1/product-center/metrics/threshold-calibration")', '阈值校准', '触发指标阈值校准'),
            ('@GetMapping("/api/v1/product-center/metrics/threshold-calibration/last")', '最近校准结果', '返回最近一次阈值校准结果'),
        ])

# ---------------- ExecutionController ----------------
process('ExecutionController.java',
        '@Tag(name = "执行实例", description = "流程执行实例管理：查询、恢复、取消、状态更新与日志追加")',
        [
            ('@GetMapping("/{executionId}")', '执行实例详情', '按 ID 查询执行实例'),
            ('@GetMapping("")', '执行实例列表', '分页查询执行实例'),
            ('@GetMapping("/status/{status}")', '按状态查询实例', '按执行状态筛选实例列表'),
            ('@PostMapping("/{executionId}/resume")', '恢复执行', '人工介入后恢复执行'),
            ('@PostMapping("/{executionId}/cancel")', '取消执行', '取消指定执行实例'),
            ('@PutMapping("/{executionId}/status")', '更新执行状态', '更新执行实例状态'),
            ('@PostMapping("/{executionId}/logs")', '追加执行日志', '向执行实例追加日志记录'),
            ('@PostMapping("/execute")', '执行流程', '触发流程引擎执行（批量节点）'),
        ])

# ---------------- HealthController ----------------
process('HealthController.java',
        '@Tag(name = "健康检查", description = "服务健康探针与根路径信息")',
        [
            ('@RequestMapping(value = {"/health", "/api/v1/health"}, method = {RequestMethod.GET, RequestMethod.HEAD})',
             '健康检查', '返回服务健康状态与应用信息'),
            ('@GetMapping("/")', '根路径', '返回服务欢迎信息'),
        ])

# ---------------- ValidationController ----------------
process('ValidationController.java',
        '@Tag(name = "校验服务", description = "表单校验：字段级、表单级与 LLM 智能校验")',
        [
            ('@PostMapping("/field")', '字段校验', '单字段规则校验'),
            ('@PostMapping("/form")', '表单校验', '整表单规则校验'),
            ('@PostMapping("/llm")', 'LLM 校验', 'LLM 智能语义校验'),
        ])

# ---------------- FormController ----------------
process('FormController.java',
        '@Tag(name = "表单服务", description = "动态表单：Schema 获取、AI 生成与提交")',
        [
            ('@GetMapping("/form/schema/{formCode}")', '表单 Schema', '按表单编码获取动态表单 Schema'),
            ('@PostMapping("/form/generate")', 'AI 生成表单', '由 LLM 依据描述生成表单'),
            ('@PostMapping("/form/submit")', '表单提交', '提交表单数据并校验'),
        ])

# ---------------- AgentController ----------------
process('AgentController.java',
        '@Tag(name = "Agent 对话", description = "Agent 对话补全（同步/流式 SSE）")',
        [
            ('@PostMapping("/chat")', 'Agent 对话', '同步执行 Agent 对话补全'),
            ('@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)',
             'Agent 流式对话', 'SSE 流式执行 Agent 对话补全'),
        ])

# ---------------- HttpProxyController ----------------
process('HttpProxyController.java',
        '@Tag(name = "HTTP 代理", description = "通用 HTTP 转发代理（MCP 外部工具调试等场景）")',
        [
            ('@PostMapping\n', '代理请求', '按 body 中 url/method/headers 转发 HTTP 请求'),
        ])

print('ALL DONE')
