# MEMORY.md - 长期记忆

## 项目概况
- prod_platform_ai：AI 驱动动态表单底层框架（FastAPI + Vue3）
- 后端：Python FastAPI, LLM 意图识别 + 字段提取 + 流式输出
- 前端：Vue3 + Element Plus, 主聊天窗口（管理后台已移除，配置功能内嵌于聊天）

## 关键架构
- 意图类型：form / form_update / configure / delete_form / manage_history / validate / chat（8种）
- **IntentHandler 注册器架构**（2026-04-29 改造）：
  - `backend/app/intent/` 模块：BaseIntentHandler ABC + IntentContext + IntentHandlerRegistry 单例
  - 8个独立处理器文件在 `handlers/` 目录下（form/form_update/delete_form/configure/manage_history/tariff_filing/validation/chat）
  - 通过 `__init__.py` 显式注册，handlers/__init__.py 需同步导出所有 Handler
  - chat.py 的 if/elif 链已替换为 `registry.dispatch(intent_type, ctx)` 一行分发
  - 前端 `intent-panels/intent-registry.js`：SSE事件处理器 + 意图后处理器注册器
  - 前端面板组件化：DeleteResultPanel.vue / HistoryPanel.vue / ConfigCard.vue
  - 新增意图只需：新建handler文件 → 注册 → 前端新面板 → 不改主逻辑
- configure 意图：主聊天窗口中通过自然语言创建新业务表单类型
- delete_form 意图：删除业务表单（自动备份到版本历史，支持回退）
- 部署流程：AdminAiService 生成配置 → deploy-config API 一键部署（写文件+热重载）
- **容器化部署**：离线依赖打包进 git（backend/frontend/vendor/），Dockerfile.offline 从本地安装，内网构建不访问外网。打包：`pack-offline-deps.ps1`，构建：`docker-compose up -d --build`
- 配置存储：config/ontologies/*.json（本体） + config/scenes/scene_mapping.json（场景映射） + config/versions/{formCode}/（版本历史）
- ConfigLoader 支持热重载：reload_config('ontologies'|'scene_mappings'|'prompts')
- AdminService 保留：被 chat.py 的 deploy-config / delete-form / rollback-form 端点复用
- 版本管理：create_version / list_versions / get_version / rollback_version（备份→删除→回退）

## 近期动态
- 2026-04-27：实现主聊天窗口「新业务配置向导」功能
  - 新增 configure 意图识别规则
  - 新增 ConfigCard.vue 配置预览卡片组件
  - 新增 /api/v1/chat/deploy-config 一键部署端点
  - 完整流程：用户说"我想加一个XX表单" → AI 生成配置 → 预览 → 部署 → 立即测试
- 2026-04-27：清理管理后台（AdminPanel）
  - 删除前端 admin 目录（7 个 Vue 组件）
  - 删除后端 admin.py / admin_ai.py / schemas/admin.py / admin_ai_service.py
  - 保留 admin_service.py（被 chat.py 复用）
  - 修改 App.vue 移除 mode 切换和「管理」按钮
  - 修改 main.py 移除 admin 路由注册
- 2026-04-28：配置生成 Prompt + 前端表单能力扩展
  - rules 设计：AI 可自由命名 rule_type（15 种内置 + custom 脚本 + 自定义类型后端兜底）
  - fieldType 枚举：11 种（input/textarea/number/select/radio/checkbox/date/datetime/email/phone/file）
  - enumConfig：静态枚举 + API 动态枚举（含 fallback 兜底）
  - submitConfig：外部 API 提交配置
  - 前端 validateForm 支持 custom 类型（Function 沙箱）
  - max_tokens 从 2048 提升到 8192，_extract_json 支持截断 JSON 括号补全
- 2026-04-28：规则体系重构——rules 结构化 → ruleDescription 自然语言
  - 本体字段新增 ruleDescription（自然语言规则描述），替代旧的 rules 结构化数组
  - 所有 7 个本体 JSON 已迁移，添加 ruleDescription 示例
  - _CONFIG_SYSTEM_PROMPT 已简化：删除 15 种 rule_type 巨表，改为 ruleDescription 说明
- form_service.py 新增 ruleDescription 字段传递
- ChatAssistant.vue 表单提交成功后展示提交摘要（字段值列表）
- 2026-05-07：表单提交流程改造——提交→聊天窗口校验→确认
  - handleConfirmSubmit 不再直接调 /api/v1/validation/llm，改为通过 SSE 流发送校验消息
  - 新增 doSendValidationMessage()：构造用户消息 + form_data 发送到 /api/v1/chat/stream
  - 后端 ChatRequest 新增 form_data 可选字段，chat_stream 在意图分发前注入到 intent_data
  - 前端注册 validate 意图后处理器：校验通过→追加确认提示；校验失败→清除 pendingConfirmForm
  - 完整流程：点击提交→聊天窗口显示校验消息→SSE流→ValidationHandler→通过/失败→确认提示→用户回复确认→执行提交
- 2026-05-07：枚举配置统一迁移
  - 所有本体枚举字段从 `field.options` 迁移为 `field.enumConfig = {type:"static", options:[...]}`
  - 后端 5 个文件统一改为读 `enumConfig.options`（form_service/history_ai_service/validation_skill/admin_service）
  - 前端 getFieldOptions 保留 field.options 回退作为防御性兼容
  - 表单校验消息中枚举字段显示为 `显示名[code]` 格式（方括号包裹 code）

## 启动方式
- **后端**：`start-backend.bat`
- **前端**：`start-frontend.bat`
- **不使用** start-all.bat（有问题），分开执行

## 三层智能推荐引擎
- 推荐数据结构：从 List[str] 升级为 List[{value, source, reason, confidence}]
- 来源优先级：llm_rule:4 > inference:3 > history:2 > static:1
- 三层合并：FormService._merge_recommendations() → Layer1(引擎+LLM) > Layer2(HistoryService DB) > Layer3(options/static)
- 两路推荐合并：chat.py _merge_field_recommendations() 合并 LLM 推荐和推荐引擎结果（不覆盖）
- 全字段推荐：batch_recommend 传 field_codes=本体所有字段，未提取字段也能获得推荐
- 条件关联推断：_get_history_recommendations 按已知字段值过滤历史记录，source=inference 优先级高于 history
- 前端增强：DynamicForm.vue 按来源区分颜色（紫色AI/蓝色推断/灰色静态）+ tooltip 展示 reason
- system_config.json smartRecommend 配置节：三层开关 + maxRecommendationsPerField

## 历史数据导入 v3
- 格式：JSONL 优先（每行一个 JSON 对象），兼容旧 CSV
- 支持 entities 层级/嵌套结构（flatten_record() 递归拍平）
- FormInstance.data=原始完整JSON，FormHistory=拍平后字段值
- 目录：config/import_data/{formCode}.schema.json + {formCode}.data.jsonl
- 脚本：backend/app/scripts/import_history.py
- 已有示例：leave(12条) / sales_order(10条) / tariff_filing_publicity(12条,30字段)

## AI 历史数据智能维护
- 意图类型：manage_history（第6种意图）
- 4种操作：analyze(分析质量)/ generate(AI生成数据)/ import(导入DB)/ status(查询统计)
- 核心服务：history_ai_service.py
- API端点：/chat/history/analyze, generate, import, list, {formCode}/summary
- AI生成流程：读取本体 → LLM生成符合约束的JSONL → 保存到import_data/ → 前端预览+确认导入
- 前端面板：评分环+异常检测+推荐列表+预览+导入按钮+字段分布
