# 场景管理与引擎框架完美匹配方案

## 一、现有架构分析

### 1.1 引擎框架核心组件

| 组件 | 位置 | 功能 | 与场景的关系 |
|------|------|------|-------------|
| `SceneRecognitionSkill` | `app/skills/scene_recognition.py` | 识别用户输入场景 | **直接相关** - 现有场景识别能力 |
| `IntentHandlerRegistry` | `app/intent/registry.py` | 意图处理器注册中心 | **可扩展** - 场景路由 |
| `MCPToolHub` | `app/mcp_tools/tool_hub.py` | MCP工具注册与调度 | **核心** - 场景可使用的工具 |
| `BaseIntentHandler` | `app/intent/base.py` | 意图处理器基类 | **可扩展** - 场景处理基类 |
| `ConfigLoader` | `app/core/config_loader.py` | 配置加载器 | **核心** - 场景配置加载 |
| `FieldExtractionSkill` | `app/skills/field_extraction.py` | 字段提取技能 | **相关** - 场景数据处理 |
| `RecommendationEngine` | `app/services/recommendation_engine.py` | 推荐引擎 | **相关** - 场景数据推荐 |

### 1.2 现有处理流程

```
用户消息
  ↓
[1] 场景识别 (SceneRecognitionSkill)
  ├─ 关键词匹配
  ├─ LLM识别
  └─ 确定 sceneCode
  ↓
[2] 意图解析 (LLM intent recognition)
  ├─ 解析 intentType
  ├─ 解析 formCode
  └─ 解析 tool_calls
  ↓
[3] 工具执行 (MCPToolHub)
  └─ 执行 tool_calls
  ↓
[4] Handler分发 (IntentHandlerRegistry)
  └─ 路由到对应 Handler
  ↓
[5] Handler处理
  ├─ 表单生成
  ├─ 数据验证
  └─ 推荐生成
```

---

## 二、场景与引擎的完美匹配架构

### 2.1 核心理念

**场景 = 能力边界 + 行为规范 + 工具集**

- **能力边界**: 场景定义了助手能做什么（关键词、优先级、启用状态）
- **行为规范**: 场景提示词定义了助手如何思考和执行
- **工具集**: 场景配置了可用的工具列表和执行流程

### 2.2 架构演进图

#### 当前架构
```
用户输入 → 场景识别 → 意图识别 → 工具调用 → Handler处理
(固定)    (独立)     (通用)     (通用)
```

#### 目标架构
```
用户输入 → 场景识别 → [场景上下文加载] → [场景驱动LLM] → [场景工具编排] → 输出
            ↓              ↓                   ↓
         场景配置     场景动作提示词    场景专属工具集
         (管理后台)   (管理后台配置)    (管理后台配置)
```

---

## 三、完美匹配方案

### 3.1 方案一：渐进式演进（推荐）

#### 阶段1：增强场景配置
**目标**：扩展场景配置，加入动作提示词和工具集配置

**变更点**：
1. **增强场景配置结构**（已在 scene_mapping.json 中）
   ```json
   {
     "sceneCode": "tariff_filing_publicity",
     "sceneName": "资费备案公示",
     "keywords": ["资费备案"],
     "priority": 10,
     "isActive": true,
     "intentType": "tariff_filing",
     "formCode": "tariff_filing_publicity",
     "actionType": "form_with_mcp",
     "actionPrompt": "tariff_filing_publicity_prompt.txt",
     "requiredTools": ["query_tariff_by_code"],
     "availableTools": ["query_tariff_by_code", "validate_fields"],
     "preActionSteps": [...],
     "postActionSteps": [...]
   }
   ```

2. **创建场景提示词管理器** (`ScenePromptManager`)
   - 加载场景提示词
   - 变量替换
   - 缓存管理

3. **扩展 ConfigLoader**
   - 增加 `get_scene_by_code(sceneCode)`
   - 增加 `get_scene_prompt(sceneCode)`
   - 增加 `get_scene_tools(sceneCode)`

#### 阶段2：创建场景驱动Handler
**目标**：创建统一的 SceneDrivenHandler，替代部分特定Handler

**架构**：
```python
# app/intent/handlers/scene_driven_handler.py
@intent_handler("scene_driven")
class SceneDrivenHandler(BaseIntentHandler):
    """场景驱动处理器 - 统一处理所有配置化场景"""
    
    async def handle(self, ctx: IntentContext):
        # 1. 获取场景配置
        scene_config = config_loader.get_scene_by_code(ctx.intent_data.get("sceneCode"))
        
        # 2. 加载场景提示词
        prompt = ScenePromptManager.build_prompt(scene_config, ctx)
        
        # 3. 执行前置动作
        await self.execute_pre_actions(scene_config, ctx)
        
        # 4. 调用LLM进行场景处理
        llm_result = await llm_service.call_llm(prompt)
        
        # 5. 执行后置动作
        await self.execute_post_actions(scene_config, ctx, llm_result)
        
        # 6. 输出结果
        yield from self.output_result(llm_result)
```

#### 阶段3：工具编排增强
**目标**：让场景可以配置工具执行流程

**实现**：
```python
# app/skills/tool_orchestrator.py
class ToolOrchestrator:
    """工具编排器 - 根据场景配置执行工具序列"""
    
    async def execute_workflow(self, steps: List[Dict], ctx: IntentContext):
        for step in steps:
            if step['type'] == 'call_tool':
                result = await self.execute_tool(step, ctx)
                if not result['success'] and step.get('onFailure') == 'stop':
                    break
            elif step['type'] == 'extract_param':
                await self.extract_param(step, ctx)
            elif step['type'] == 'validate':
                await self.validate(step, ctx)
```

### 3.2 方案二：革命性重构

创建 `SceneExecutionEngine`（场景执行引擎），替代现有的意图识别和Handler流程。

```python
# app/engine/scene_execution_engine.py
class SceneExecutionEngine:
    """场景执行引擎 - 统一的场景处理入口"""
    
    async def execute(self, user_input: str, ctx: dict):
        # 阶段1: 场景识别
        scene = await self.recognize_scene(user_input)
        
        # 阶段2: 场景准备
        await self.prepare_scene(scene, ctx)
        
        # 阶段3: 场景执行
        result = await self.execute_scene(scene, ctx)
        
        # 阶段4: 结果输出
        return await self.format_output(scene, result)
```

---

## 四、具体实现路径

### 4.1 数据库设计扩展

#### 场景配置表
```sql
CREATE TABLE scenes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scene_code VARCHAR(100) UNIQUE NOT NULL,
    scene_name VARCHAR(200) NOT NULL,
    description TEXT,
    keywords JSON NOT NULL,
    priority INT DEFAULT 10,
    is_active TINYINT(1) DEFAULT 1,
    intent_type VARCHAR(50),
    form_code VARCHAR(100),
    action_type VARCHAR(50),
    action_prompt_file VARCHAR(255),
    required_tools JSON,
    available_tools JSON,
    pre_action_steps JSON,
    post_action_steps JSON,
    version INT DEFAULT 1,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME ON UPDATE CURRENT_TIMESTAMP
);
```

#### 场景提示词模板表
```sql
CREATE TABLE scene_prompt_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scene_code VARCHAR(100) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    template_content TEXT NOT NULL,
    version INT DEFAULT 1,
    is_active TINYINT(1) DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME ON UPDATE CURRENT_TIMESTAMP
);
```

#### 场景统计日志表
```sql
CREATE TABLE scene_execution_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scene_code VARCHAR(100) NOT NULL,
    session_id VARCHAR(64),
    user_input TEXT,
    recognition_method VARCHAR(20),
    confidence DECIMAL(5,4),
    success_count INT,
    tool_calls JSON,
    execution_time_ms INT,
    is_success TINYINT(1) DEFAULT 1,
    error_message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 4.2 核心类设计

#### 4.2.1 SceneConfig - 场景配置模型
```python
@dataclass
class SceneConfig:
    scene_code: str
    scene_name: str
    description: Optional[str] = None
    keywords: List[str] = field(default_factory=list)
    priority: int = 10
    is_active: bool = True
    intent_type: Optional[str] = None
    form_code: Optional[str] = None
    action_type: str = "form_generation"
    action_prompt_file: Optional[str] = None
    required_tools: List[str] = field(default_factory=list)
    available_tools: List[str] = field(default_factory=list)
    pre_action_steps: List[dict] = field(default_factory=list)
    post_action_steps: List[dict] = field(default_factory=list)
    version: int = 1
```

#### 4.2.2 ScenePromptManager - 场景提示词管理器
```python
class ScenePromptManager:
    """场景提示词管理器"""
    
    @classmethod
    async def load_prompt(cls, scene_config: SceneConfig, context: dict) -> str:
        """加载并构建场景提示词"""
        if scene_config.action_prompt_file:
            prompt_template = await cls._load_template_file(scene_config.action_prompt_file)
        else:
            prompt_template = await cls._load_default_template(scene_config.action_type)
        
        return cls._inject_variables(prompt_template, scene_config, context)
    
    @classmethod
    def _inject_variables(cls, template: str, scene: SceneConfig, context: dict) -> str:
        """注入变量到提示词模板"""
        variables = {
            "{scene_code}": scene.scene_code,
            "{scene_name}": scene.scene_name,
            "{form_code}": scene.form_code or "",
            "{available_tools}": ", ".join(scene.available_tools),
            "{user_input}": context.get("user_input", ""),
            "{current_date}": datetime.now().strftime("%Y-%m-%d"),
            "{tools_info}": cls._build_tools_info(scene.available_tools)
        }
        result = template
        for key, value in variables.items():
            result = result.replace(key, value)
        return result
    
    @classmethod
    def _build_tools_info(cls, tool_names: List[str]) -> str:
        """构建工具信息描述"""
        hub = get_toolhub()
        tools_info = []
        for tool_name in tool_names:
            tool = hub.get_tool(tool_name)
            if tool:
                tools_info.append(f"- {tool.name}: {tool.description}")
        return "\n".join(tools_info) if tools_info else "暂无可用工具"
```

#### 4.2.3 SceneDrivenHandler - 场景驱动处理器
```python
@intent_handler("scene_driven")
class SceneDrivenHandler(BaseIntentHandler):
    """场景驱动处理器 - 处理配置化场景"""
    
    async def handle(self, ctx: IntentContext):
        yield thinking("📋 Phase 1: 加载场景配置...", result={"scene": ctx.intent_data.get("sceneCode")})
        
        # 获取场景配置
        scene_code = ctx.intent_data.get("sceneCode")
        scene_config = await SceneConfigLoader.load(scene_code)
        
        if not scene_config or not scene_config.is_active:
            yield thinking("⚠️ 场景不可用，切换到通用模式")
            yield from self._fallback_to_chat(ctx)
            return
        
        # 构建场景提示词
        yield thinking("🧠 Phase 2: 构建场景提示词...")
        prompt_context = {
            "user_input": ctx.last_user_message,
            "messages_text": ctx.messages_text,
            "session_id": ctx.request.session_id if hasattr(ctx.request, "session_id") else None
        }
        scene_prompt = await ScenePromptManager.load_prompt(scene_config, prompt_context)
        
        # 执行前置动作
        yield thinking("⚙️ Phase 3: 执行前置动作...")
        pre_result = await self._execute_steps(scene_config.pre_action_steps, ctx)
        
        # 调用LLM执行场景逻辑
        yield thinking("🤖 Phase 4: LLM处理中...")
        llm_result = await self._call_scene_llm(scene_prompt, ctx)
        
        # 执行后置动作
        yield thinking("✅ Phase 5: 执行后置动作...")
        post_result = await self._execute_steps(scene_config.post_action_steps, ctx, llm_result)
        
        # 输出结果
        yield from self._output_result(scene_config, llm_result, ctx)
        
        # 记录统计
        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
        yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
        
        # 完成事件
        yield done_event(is_form=True, intent_data=ctx.intent_data)
    
    async def _execute_steps(self, steps: List[dict], ctx: IntentContext, data: dict = None) -> dict:
        """执行步骤序列"""
        results = {}
        for step in steps:
            step_type = step.get("type", step.get("stepType"))
            
            if step_type == "extract_param":
                result = await self._extract_param(step, ctx)
            elif step_type == "call_tool":
                result = await self._call_tool(step, ctx)
            elif step_type == "validate":
                result = await self._validate(step, ctx, data)
            elif step_type == "recommend":
                result = await self._recommend(step, ctx, data)
            else:
                continue
            
            results[step.get("id", f"step_{len(results)}")] = result
        return results
    
    async def _call_tool(self, step: dict, ctx: IntentContext) -> dict:
        """调用工具"""
        tool_name = step.get("toolName", step.get("tool_name"))
        tool_args = step.get("arguments", step.get("paramMapping", {}))
        
        hub = get_toolhub()
        if not hub.has_tool(tool_name):
            return {"success": False, "error": f"工具 {tool_name} 不存在"}
        
        yield thinking(f"🔧 调用工具: {tool_name}")
        result = await hub.execute(tool_name, tool_args)
        return result
```

#### 4.2.4 EnhancedSceneRecognitionSkill - 增强的场景识别
```python
class EnhancedSceneRecognitionSkill(SceneRecognitionSkill):
    """增强的场景识别技能"""
    
    @classmethod
    async def recognize_with_context(cls, user_input: str, context: dict) -> dict:
        """带上下文的场景识别"""
        # 1. 先尝试关键词匹配
        keyword_result = await cls._keyword_match(user_input)
        if keyword_result.get("success") and keyword_result.get("confidence", 0) > 0.8:
            return keyword_result
        
        # 2. LLM智能识别
        llm_result = await cls._llm_recognize(user_input, context)
        if llm_result.get("success"):
            return llm_result
        
        # 3. 返回默认场景
        return cls._get_default_scene()
    
    @classmethod
    async def _llm_recognize(cls, user_input: str, context: dict) -> dict:
        """LLM智能识别"""
        scenes = await SceneConfigLoader.get_active_scenes()
        scenes_info = "\n".join([
            f"- {s.scene_code}: {s.scene_name} (关键词: {', '.join(s.keywords)})"
            for s in scenes
        ])
        
        prompt = f"""你是一个场景识别助手。请根据用户输入，选择最合适的场景。

可用场景：
{scenes_info}

用户输入：{user_input}
对话历史：{context.get("messages", "")}

请直接输出JSON格式：
{{
    "sceneCode": "选中的场景编码",
    "sceneName": "场景名称",
    "confidence": 0.95,
    "method": "llm",
    "reasoning": "选择理由"
}}
"""
        # 调用LLM进行识别
        llm_result = await llm_service.call_llm(prompt)
        return cls._parse_llm_result(llm_result)
```

### 4.3 集成到现有流程

#### 4.3.1 修改 chat_stream 流程
在 `chat.py` 中，修改现有流程以支持场景驱动：

```python
@router.post("/chat/stream")
async def chat_stream(request: ChatRequest, db: Session = Depends(get_db)):
    async def stream_generator():
        # ... 现有初始化代码 ...
        
        # 增强的场景识别
        scene_result = await EnhancedSceneRecognitionSkill.recognize_with_context(
            last_user_message,
            {"messages": messages_text}
        )
        
        if scene_result.get("success"):
            scene_code = scene_result.get("sceneCode")
            
            # 检查该场景是否有自定义处理
            scene_config = await SceneConfigLoader.load(scene_code)
            
            if scene_config and scene_config.action_type:
                # 场景驱动处理
                yield thinking(f"🎯 识别到场景: {scene_config.scene_name}")
                
                ctx = IntentContext(
                    intent_data={
                        "sceneCode": scene_code,
                        "formCode": scene_config.form_code
                    },
                    intent_result=json.dumps(scene_result),
                    intent_type="scene_driven",  # 使用场景驱动Handler
                    confidence=scene_result.get("confidence", 0.9),
                    ontologies=ontologies,
                    request=request,
                    db=db,
                    last_user_message=last_user_message,
                    messages_text=messages_text,
                    start_time=start_time,
                    stream_stats=stream_stats
                )
                
                async for chunk in get_intent_registry().dispatch("scene_driven", ctx):
                    yield chunk
                return
        
        # ... 保持现有的fallback逻辑 ...
```

#### 4.3.2 修改 IntentHandlerRegistry
支持动态注册场景：

```python
# 在 IntentHandlerRegistry 中增加
class IntentHandlerRegistry:
    # ... 现有代码 ...
    
    def register_scene_handler(self, scene_config: SceneConfig):
        """为场景注册处理器"""
        handler = SceneDrivenHandler()
        handler.scene_config = scene_config
        self._handlers[f"scene_{scene_config.scene_code}"] = handler
        logger.info(f"注册场景处理器: scene_{scene_config.scene_code}")
```

---

## 五、管理后台扩展

### 5.1 场景配置页面
在管理后台增加完整的场景配置界面：

#### 5.1.1 场景列表页
- 场景卡片展示
- 启用/停用切换
- 优先级调整
- 搜索筛选

#### 5.1.2 场景编辑页
**基本信息**：
- 场景编码
- 场景名称
- 场景描述
- 优先级
- 启用状态

**识别规则**：
- 关键词管理（添加/删除/排序）
- 识别方式选择（关键词/LLM/混合）
- 默认场景设置

**动作配置**：
- 动作类型选择（表单生成/带MCP/工作流/对话/自定义）
- 关联表单选择
- 提示词编辑器（支持实时预览）

**工具配置**：
- 可用工具列表（多选择）
- 工具执行顺序配置
- 工具参数映射

**执行流程**：
- 前置步骤可视化编辑
- 后置步骤可视化编辑
- 失败处理策略配置

#### 5.1.3 场景测试页
- 测试输入框
- 实时识别展示
- 执行过程可视化
- 结果预览

### 5.2 统计分析页面
- 场景使用排行
- 识别成功率分析
- 执行耗时分析
- 工具调用统计

---

## 六、迁移路径

### 6.1 第一阶段（最小改动）
1. 创建场景配置扩展结构
2. 实现 ScenePromptManager
3. 更新 SceneRecognitionSkill 支持从数据库加载
4. 创建基础的 SceneDrivenHandler

### 6.2 第二阶段（功能增强）
1. 实现工具编排器
2. 实现场景执行日志
3. 创建管理后台API
4. 集成到现有流程

### 6.3 第三阶段（完全重构）
1. 创建 SceneExecutionEngine
2. 迁移现有场景
3. 实现高级特性（A/B测试、版本回滚）
4. 完善统计分析

---

## 七、总结

### 7.1 匹配优势

| 优势 | 说明 |
|------|------|
| **向后兼容** | 不破坏现有Handler机制，渐进式迁移 |
| **配置驱动** | 通过配置而非代码扩展场景能力 |
| **复用现有** | 充分利用现有 MCPToolHub、IntentHandlerRegistry |
| **可观测** | 场景执行全流程可监控、可分析 |
| **易扩展** | 新增场景无需代码变更，通过管理后台配置 |

### 7.2 关键成功因素

1. **场景配置灵活性** - 配置结构要能覆盖各种场景需求
2. **提示词管理系统** - 提示词编辑、测试、版本管理要便捷
3. **工具编排能力** - 支持复杂工具调用序列和错误处理
4. **监控分析体系** - 为场景优化提供数据支撑

### 7.3 与管理计划的对应关系

| 管理计划章节 | 对应本方案部分 |
|--------------|--------------|
| 场景管理 (SceneManager) | 3.1 增强场景配置 + 5.1 场景配置页面 |
| 工具管理 (ToolRegistry) | 4.2.4 工具编排增强 |
| 场景提示词 | 4.2.2 ScenePromptManager |
| 数据库设计 | 4.1 数据库设计扩展 |
| API设计 | 集成到现有API + 扩展管理API |
