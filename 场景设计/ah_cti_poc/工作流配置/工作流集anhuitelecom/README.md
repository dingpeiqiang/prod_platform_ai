# 工作流集 anhuitelecom（测试环境版）

本目录由 `智能体工作流集V1.6`（开发环境）复制而来，仅替换了工作流内的 API 地址，
工作流结构、节点逻辑、脚本代码与 V1.6 完全一致。

## 环境地址对照

| 用途 | 开发环境（V1.6 原值） | 测试环境（本目录现值） |
|------|----------------------|----------------------|
| 前端页面（config-workbench 等） | `http://10.86.13.201:31280` | `http://szyg-stq-test.anhuitelecom.com:10001/prod-ai` |
| 后端 API（/api/v1/appstore/*） | `http://10.86.13.201:31281` | `http://szyg-stq-test.anhuitelecom.com:10001/prod-ai` |

> 测试环境前端与后端 API 统一走第三方 Nginx `szyg-stq-test.anhuitelecom.com:10001`，
> 由第三方 Nginx 按路径转发到本应用（前端静态 → 6173，`/prod-ai/api/` → 后端 6174）。
> 第三方 Nginx（szyg-stq-test.anhuitelecom.com:10001）以 `/prod-ai` 前缀透传转发到本应用（前端 6173 / 后端 6174），前缀剥除由本应用 prod-ai.conf 负责。

## 替换明细（共 72 处）

| 文件 | 替换处数 |
|------|---------|
| wf_sub_00_需求提报.json | 3 |
| wf_sub_01_需求分析.json | 5 |
| wf_sub_02_智能配置.json | 4 |
| wf_sub_03_规格稽核.json | 4 |
| wf_sub_04_自动测试.json | 9 |
| wf_sub_05_资费校准.json | 4 |
| wf_sub_06_上线审批.json | 7 |
| wf_sub_07_监控运维.json | 6 |
| wf_sub_08_审批进度查询.json | 2 |
| wf_sub_09_存量产品查询.json | 1 |
| wf_sub_10_存量合规扫描.json | 2 |
| wf_sub_11_发起需求审批.json | 3 |
| 产销品-智能配置-稽核-测试-资费校准_合并_export.json | 12 |
| _test00.json | 6 |
| gen_workflows.py / gen_workflows_v2.py | 各 1 |
| _rebuild11_and_transform.py | 2 |

说明：
- `产销品-组合智能配置-自动化测试_export.json` 原本不含任何 IP/URL，无需替换。
- 所有 JSON 已校验可正常解析。
- 工作流测试用例手册.md 不含环境地址，保持原样。

## 导入方式

与 V1.6 相同，使用 `gen_import_requests.py` 生成批量导入请求体后，
POST 到平台的 `aiFlow/workFlowScriptSaveV3` 保存接口（Header 会话信息见 `import_headers.json`）。
