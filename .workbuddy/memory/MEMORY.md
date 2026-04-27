# MEMORY.md - 长期记忆

## 项目概况
- prod_platform_ai：AI 驱动动态表单底层框架（FastAPI + Vue3）
- 后端：Python FastAPI, LLM 意图识别 + 字段提取 + 流式输出
- 前端：Vue3 + Element Plus, 主聊天窗口（管理后台已移除，配置功能内嵌于聊天）

## 关键架构
- 意图类型：form / form_update / configure / chat
- configure 意图：主聊天窗口中通过自然语言创建新业务表单类型
- 部署流程：AdminAiService 生成配置 → deploy-config API 一键部署（写文件+热重载）
- 配置存储：config/ontologies/*.json（本体） + config/scenes/scene_mapping.json（场景映射）
- ConfigLoader 支持热重载：reload_config('ontologies'|'scene_mappings'|'prompts')
- AdminService 保留：被 chat.py 的 deploy-config 端点复用（create_ontology / update_ontology / get_scene_mappings / update_scene_mappings）

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
