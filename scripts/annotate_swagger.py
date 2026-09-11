# -*- coding: utf-8 -*-
"""为 WorkflowController 批量添加 Swagger 中文注解（构建期一次性工具）。"""
import io

path = 'backend-app/src/main/java/com/sitech/prodai/controller/WorkflowController.java'
with io.open(path, encoding='utf-8') as f:
    src = f.read()

# 幂等：已注入则跳过
if 'io.swagger.v3.oas.annotations.Operation' in src:
    print('already annotated, skip')
    raise SystemExit(0)

src = src.replace(
    'import com.sitech.prodai.service.WorkflowService;',
    'import com.sitech.prodai.service.WorkflowService;\n'
    'import io.swagger.v3.oas.annotations.Operation;\n'
    'import io.swagger.v3.oas.annotations.tags.Tag;'
)
src = src.replace(
    '@RestController\n@RequestMapping("/api/workflows")',
    '@Tag(name = "工作流管理", description = "LangChain 工作流全生命周期：创建/编辑/删除、启停、发布/下线、版本历史/回滚/对比/复制")\n'
    '@RestController\n@RequestMapping("/api/workflows")'
)

annos = [
    ('    @GetMapping("/categories")',
     '    @Operation(summary = "工作流分类列表", description = "返回全部工作流分类及其计数，供筛选器渲染")'),
    ('    @GetMapping("")',
     '    @Operation(summary = "工作流列表（分页）", description = "多条件筛选：分类/启停/关键字/标签/创建人/执行次数区间，支持排序与分页")'),
    ('    @GetMapping("/{workflowCode}")',
     '    @Operation(summary = "工作流详情", description = "按 workflowCode 返回工作流完整定义（含节点/连线/执行参数）")'),
    ('    @PostMapping("")',
     '    @Operation(summary = "创建工作流", description = "body: workflow_code(唯一)/workflow_name/workflow_data(画布JSON)/category/tags 等")'),
    ('    @PutMapping("/{workflowCode}")',
     '    @Operation(summary = "更新工作流", description = "按 workflowCode 更新定义与元信息，自动落版本历史")'),
    ('    @DeleteMapping("/{workflowCode}")',
     '    @Operation(summary = "删除工作流", description = "按 workflowCode 删除工作流及其版本历史")'),
    ('    @PostMapping("/{workflowCode}/toggle")',
     '    @Operation(summary = "启停工作流", description = "切换 is_active 状态（停用后不可被场景命中执行）")'),
    ('    @GetMapping("/{workflowCode}/history")',
     '    @Operation(summary = "版本历史", description = "返回该工作流的全部历史版本（含变更说明）")'),
    ('    @PostMapping("/{workflowCode}/publish")',
     '    @Operation(summary = "发布工作流", description = "将当前版本发布为正式版本（可被场景编排引用）")'),
    ('    @PostMapping("/{workflowCode}/unpublish")',
     '    @Operation(summary = "下线工作流", description = "将已发布工作流下线，场景不再命中")'),
    ('    @PostMapping("/batch-publish")',
     '    @Operation(summary = "批量发布", description = "body: workflowCodes 数组，批量发布多个工作流")'),
    ('    @PostMapping("/{workflowCode}/rollback")',
     '    @Operation(summary = "版本回滚", description = "body: targetVersion 目标版本号，回滚后自动生成新版本记录")'),
    ('    @GetMapping("/{workflowCode}/compare")',
     '    @Operation(summary = "版本对比", description = "query: version1/version2，返回两版定义的结构化差异")'),
    ('    @PostMapping("/{workflowCode}/copy")',
     '    @Operation(summary = "复制工作流", description = "body: newWorkflowCode 新编码，深拷贝定义为新工作流")'),
]
for anchor, anno in annos:
    src = src.replace(anchor, anno + '\n' + anchor, 1)

with io.open(path, 'w', encoding='utf-8', newline='') as f:
    f.write(src)
print('done')
