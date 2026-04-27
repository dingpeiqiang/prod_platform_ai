# Harness AI 重构分析报告

**文档**：Harness AI Project（AI马具工程项目）完整解析  
**目标项目**：work-ai（AI驱动动态表单底层框架）  
**分析日期**：2026-04-17

---

## 一、文档核心解读

### 1.1 Harness AI 的本质

**核心公式**：`Agent = Model + Harness`

> AI模型是一匹力大无穷但不认路的马，Harness就是套在马身上的缰绳、马鞍、围栏和路标。工程师是骑手，无需亲自"跑"，只需通过Harness引导AI朝着既定目标稳定前行。

**三大核心价值**：
- 🔒 **可控性**：解决AI不可控问题，限定操作边界
- ⚡ **高效性**：AI实现"自我纠错、自我验证"，降低人工成本
- 🏰 **护城河**：相同模型下，优秀Harness可使任务成功率提升26%+

### 1.2 六大核心组件

| 组件 | 功能定位 | 核心职责 |
|------|---------|---------|
| **上下文工程** | 解决AI"失忆" | 静态上下文(规范/契约) + 动态上下文(状态/日志) |
| **护栏系统** | 安全边界 | 输入过滤、输出校验、权限控制 |
| **工具编排** | 能力扩展 | 工具注册、调用路由、权限管理 |
| **验证纠错** | 自愈能力 | 自我验证循环、错误自动恢复 |
| **状态记忆** | 持续性 | 动态检索、状态持久化、清洁重置 |
| **熵管理** | 技术债务清理 | 定期扫描、修复偏差、保持健康 |

### 1.3 三阶段落地路径

```
MVP阶段 ─────→ 标准阶段 ─────→ 企业级阶段
   │              │                │
   ├─ 基础提示词   ├─ 状态持久化     ├─ 多Agent协作
   ├─ 简单工具     ├─ 自我验证      ├─ 分级审批
   ├─ 权限控制     ├─ 可观测性      ├─ 沙箱隔离
   │              │                │
   └─ AGENTS.md  └─ 向量记忆库     └─ 熵管理Agent
```

---

## 二、work-ai 现状分析

### 2.1 项目定位

**当前定位**：AI驱动动态表单底层框架 v2.0 - 配置化+LLM智能

### 2.2 现有架构组件（Harness对照）

| Harness组件 | work-ai现状 | 状态 |
|------------|------------|------|
| 上下文工程 | ❌ 缺失 | 仅有 prompts 目录，无系统化上下文管理 |
| 护栏系统 | ⚠️ 部分 | 无输入过滤、无输出校验、无危险操作拦截 |
| 工具编排 | ✅ 已实现 | ToolRegistry + 3个Skills（recognize_scene, extract_fields, get_available_forms） |
| 验证纠错 | ❌ 缺失 | 无自我验证、无错误自动恢复 |
| 状态记忆 | ⚠️ 基础 | 仅数据库持久化，无向量库、无会话状态管理 |
| 熵管理 | ❌ 缺失 | 无文档一致性扫描、无违规检测 |

### 2.3 现有项目结构

```
work-ai/
├── backend/
│   ├── app/
│   │   ├── api/          # API路由
│   │   ├── core/          # 核心配置、数据库
│   │   ├── models/        # 数据模型
│   │   ├── schemas/       # Pydantic schemas
│   │   ├── services/      # 业务服务
│   │   ├── skills/        # AI工具（ToolRegistry + 3个skills）
│   │   └── websocket/     # WebSocket支持
│   └── config/            # 配置文件
├── frontend/
│   └── src/components/    # Vue组件
└── docs/                  # 文档
```

---

## 三、重构方案

### 3.1 整体重构策略

采用 **渐进式重构**，分三个阶段实施：

```
┌─────────────────────────────────────────────────────────────────┐
│                    work-ai Harness 重构路线图                    │
├─────────────────────────────────────────────────────────────────┤
│  Phase 1: MVP Harness        Phase 2: 标准Harness    Phase 3: 企业级│
│  (当前 → 1个月)                (1-2个月)               (2-3个月)   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  上下文工程：                                                     │
│  ├─ AGENTS.md          →  ├─ 向量上下文库       →  ├─ 动态上下文   │
│  ├─ 架构约束文档        →  ├─ 上下文自动注入     →  ├─ 实时状态映射  │
│                         │                           │           │
│  护栏系统：               │                           │           │
│  ├─ 危险操作拦截    →     ├─ 输入/输出校验   →     ├─ 分级审批     │
│  ├─ 权限配置              │  (Schema验证)           │           │
│                          │                           │           │
│  工具编排：               │                           │           │
│  ├─ 扩展工具库    →     ├─ 智能路由          →     ├─ 多Agent协作  │
│  ├─ 工具权限              │                           │           │
│                          │                           │           │
│  验证纠错：               │                           │           │
│  ❌              →     ├─ 自我验证循环   →     ├─ 完整自愈系统  │
│                          │  错误自动恢复           │           │
│                          │                           │           │
│  状态记忆：               │                           │           │
│  ├─ 会话状态    →     ├─ 向量记忆库       →     ├─ 跨会话持久化  │
│                          │                           │           │
│  熵管理：                 │                           │           │
│  ❌              →     ⚠️ 基础扫描         →     ├─ 熵管理Agent  │
│                                                      │           │
│  可观测性：                │                           │           │
│  ❌              →     ├─ 日志/指标         →     ├─ 监控面板    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

### 3.2 Phase 1: MVP Harness（立即可做）

**目标**：实现AI智能体的基本可控，完成表单场景落地

#### 3.2.1 上下文工程 - 基础建设

```
backend/app/harness/context/
├── __init__.py
├── agents_md.py          # AGENTS.md 文档管理
├── prompt_templates.py    # 提示词模板
├── schema_registry.py     # 表单Schema注册表
└── constraints.py        # 架构约束规则
```

**新增文件：`backend/app/harness/context/agents_md.py`**
```python
"""AGENTS.md 文档管理 - 为AI提供静态上下文"""
from typing import Dict, List, Optional
from pathlib import Path
import json

class AgentsContextManager:
    """管理项目的 AGENTS.md 文档"""
    
    def __init__(self, config_path: str):
        self.config_path = Path(config_path)
        self._contexts: Dict[str, str] = {}
        self._load_contexts()
    
    def _load_contexts(self):
        """加载所有上下文文档"""
        # 1. 项目规范 (AGENTS.md)
        # 2. 表单Schema契约
        # 3. API接口规范
        # 4. 业务规则说明
        pass
    
    def get_context_for_task(self, task_type: str) -> str:
        """根据任务类型获取相关上下文"""
        return self._contexts.get(task_type, "")
    
    def inject_into_prompt(self, base_prompt: str, context_type: str) -> str:
        """将上下文注入到提示词"""
        context = self.get_context_for_task(context_type)
        return f"{context}\n\n---\n\n{base_prompt}"
```

#### 3.2.2 护栏系统 - 基础安全

```
backend/app/harness/guardrails/
├── __init__.py
├── input_guard.py        # 输入过滤（注入攻击检测）
├── output_guard.py       # 输出校验（Schema验证）
├── permission_guard.py   # 权限控制（危险操作拦截）
└── registry.py           # 护栏注册器
```

**新增文件：`backend/app/harness/guardrails/input_guard.py`**
```python
"""输入护栏 - 拦截注入攻击等不安全输入"""
import re
from typing import Optional, Tuple

class InputGuard:
    """输入安全检查"""
    
    DANGEROUS_PATTERNS = [
        r"<script[^>]*>.*?</script>",  # XSS
        r"rm\s+-rf",                     # 危险Shell命令
        r"\$\(.*\)",                     # 命令注入
        # ... 更多模式
    ]
    
    def validate(self, text: str) -> Tuple[bool, Optional[str]]:
        """
        验证输入安全性
        返回: (是否安全, 错误信息)
        """
        for pattern in self.DANGEROUS_PATTERNS:
            if re.search(pattern, text, re.IGNORECASE):
                return False, f"检测到不安全内容: {pattern}"
        return True, None
```

**新增文件：`backend/app/harness/guardrails/permission_guard.py`**
```python
"""权限护栏 - 拦截危险操作"""
from typing import List, Callable, Any, Tuple
from functools import wraps

class PermissionGuard:
    """权限控制"""
    
    # 危险操作白名单（需要明确授权）
    DANGEROUS_OPERATIONS = [
        "delete_all_forms",
        "drop_database", 
        "execute_raw_sql",
        "modify_system_config",
    ]
    
    def __init__(self):
        self._operation_permissions: dict = {}
    
    def register_operation(self, name: str, handler: Callable, requires_auth: bool = True):
        """注册操作及权限要求"""
        self._operation_permissions[name] = {
            "handler": handler,
            "requires_auth": requires_auth
        }
    
    def execute(self, operation: str, user_id: str, *args, **kwargs) -> Tuple[bool, Any]:
        """执行带权限检查的操作"""
        if operation not in self._operation_permissions:
            return False, f"未知操作: {operation}"
        
        op_config = self._operation_permissions[operation]
        
        if op_config["requires_auth"] and not self._check_permission(user_id, operation):
            return False, "权限不足"
        
        return True, op_config["handler"](*args, **kwargs)
```

#### 3.2.3 现有工具编排增强

```python
# 增强 tool_registry.py
class ToolRegistry:
    """增强版工具注册器"""
    
    # 工具分类
    TOOL_CATEGORIES = {
        "form": ["recognize_scene", "extract_fields", "get_available_forms"],
        "validation": ["validate_field", "validate_form"],
        "system": ["get_status", "health_check"],
    }
    
    # 工具权限级别
    PERMISSION_LEVELS = {
        "public": [],           # 公开
        "authenticated": [],    # 需要登录
        "admin": [],            # 需要管理员
    }
    
    def get_tools_for_context(self, context: str, user_level: str) -> List[Dict]:
        """根据上下文和用户级别获取可用工具"""
        # 过滤用户无权访问的工具
        pass
```

---

### 3.3 Phase 2: 标准Harness（1-2个月）

**目标**：提升AI效率和可靠性，实现中等复杂度任务的自动化运行

#### 3.3.1 验证与纠错系统

```
backend/app/harness/verification/
├── __init__.py
├── self_verifier.py      # 自我验证循环
├── error_recovery.py      # 错误自动恢复
└── retry_policy.py        # 重试策略
```

**新增文件：`backend/app/harness/verification/self_verifier.py`**
```python
"""自我验证循环 - AI输出打分，不合格则重新执行"""
from typing import Dict, Any, Callable, Optional
from app.core.llm_client import LLMClient

class SelfVerifier:
    """AI输出自验证"""
    
    def __init__(self, llm_client: LLMClient):
        self.llm = llm_client
        self.verification_prompt = """
        你是一个验证专家。请评估以下AI输出是否满足要求：
        
        任务：{task_description}
        输出：{ai_output}
        
        评估维度：
        1. 格式正确性 - 输出是否符合指定格式
        2. 内容完整性 - 是否包含所有必要信息
        3. 逻辑一致性 - 内容是否自洽
        
        返回JSON：
        {{"passed": true/false, "score": 0-100, "issues": ["问题列表"], "suggestions": ["改进建议"]}}
        """
    
    async def verify(self, task: str, output: Any, schema: Optional[Dict] = None) -> Dict:
        """验证输出"""
        # 1. Schema验证（快速检查）
        if schema:
            schema_result = self._schema_validate(output, schema)
            if not schema_result["valid"]:
                return {"passed": False, "reason": "Schema验证失败", "issues": schema_result["errors"]}
        
        # 2. LLM验证（深度检查）
        prompt = self.verification_prompt.format(
            task_description=task,
            ai_output=str(output)
        )
        result = await self.llm.chat(prompt)
        return self._parse_verification_result(result)
    
    def _schema_validate(self, output: Any, schema: Dict) -> Dict:
        """Schema快速验证"""
        # 使用 Pydantic 或 JSON Schema 验证
        pass
```

#### 3.3.2 状态与记忆管理

```
backend/app/harness/memory/
├── __init__.py
├── session_state.py      # 会话状态管理
├── vector_store.py        # 向量记忆库（可选）
├── context_compressor.py  # 上下文压缩
└── checkpoint.py          # 断点恢复
```

**新增文件：`backend/app/harness/memory/session_state.py`**
```python
"""会话状态管理 - 支持断点恢复"""
from typing import Dict, Any, Optional
from datetime import datetime
import json

class SessionState:
    """会话状态"""
    
    def __init__(self, session_id: str):
        self.session_id = session_id
        self.created_at = datetime.now()
        self.updated_at = datetime.now()
        self._state: Dict[str, Any] = {}
        self._checkpoints: list = []
    
    def set(self, key: str, value: Any):
        """设置状态"""
        self._state[key] = value
        self.updated_at = datetime.now()
    
    def get(self, key: str, default: Any = None) -> Any:
        """获取状态"""
        return self._state.get(key, default)
    
    def create_checkpoint(self, name: str = ""):
        """创建断点"""
        checkpoint = {
            "name": name,
            "timestamp": datetime.now().isoformat(),
            "state": json.dumps(self._state)
        }
        self._checkpoints.append(checkpoint)
    
    def restore_checkpoint(self, checkpoint_index: int):
        """恢复到指定断点"""
        if 0 <= checkpoint_index < len(self._checkpoints):
            self._state = json.loads(self._checkpoints[checkpoint_index]["state"])


class SessionManager:
    """会话管理器"""
    
    def __init__(self):
        self._sessions: Dict[str, SessionState] = {}
    
    def get_or_create(self, session_id: str) -> SessionState:
        if session_id not in self._sessions:
            self._sessions[session_id] = SessionState(session_id)
        return self._sessions[session_id]
    
    def cleanup_old_sessions(self, max_age_hours: int = 24):
        """清理过期会话"""
        pass
```

#### 3.3.3 可观测性 - 日志与监控

```python
# backend/app/harness/observability/
class AgentLogger:
    """AI Agent运行日志"""
    
    def __init__(self):
        self.events: list = []
    
    def log(self, event_type: str, data: dict):
        self.events.append({
            "timestamp": datetime.now().isoformat(),
            "type": event_type,
            "data": data
        })
    
    def log_tool_call(self, tool: str, input_data: dict, output: Any):
        self.log("tool_call", {
            "tool": tool,
            "input": input_data,
            "output": str(output)[:500]  # 截断长输出
        })
    
    def log_llm_call(self, prompt: str, response: str, tokens: int):
        self.log("llm_call", {
            "prompt_length": len(prompt),
            "response_length": len(response),
            "tokens": tokens
        })
    
    def export_trace(self) -> list:
        """导出完整调用链"""
        return self.events
```

---

### 3.4 Phase 3: 企业级Harness（2-3个月）

**目标**：实现多AI智能体协作，支撑企业级大规模任务

#### 3.4.1 多Agent协作架构

```
backend/app/harness/multi_agent/
├── __init__.py
├── coordinator.py         # Agent协调器
├── planner_agent.py       # 规划Agent
├── executor_agent.py      # 执行Agent
├── verifier_agent.py      # 验证Agent
└── communication.py       # Agent间通信
```

**架构设计**：
```python
class AgentCoordinator:
    """多Agent协调器"""
    
    def __init__(self):
        self.planner = PlannerAgent()
        self.executor = ExecutorAgent()
        self.verifier = VerifierAgent()
    
    async def execute_task(self, user_request: str) -> Dict:
        """协调多个Agent完成任务"""
        # 1. Planner: 分析任务，拆分为子任务
        plan = await self.planner.create_plan(user_request)
        
        # 2. Executor: 按计划执行子任务
        results = []
        for subtask in plan["subtasks"]:
            result = await self.executor.execute(subtask)
            results.append(result)
        
        # 3. Verifier: 验证最终结果
        verified = await self.verifier.verify(plan, results)
        
        return {"success": verified, "results": results}
```

#### 3.4.2 熵管理系统

```
backend/app/harness/entropy/
├── __init__.py
├── consistency_checker.py  # 文档一致性检查
├── dead_code_finder.py    # 死代码检测
├── violation_scanner.py   # 违规扫描
└── cleanup_agent.py       # 清理Agent
```

---

## 四、具体重构实施计划

### 4.1 新增目录结构

```
backend/app/
├── harness/                    # 【新增】Harness核心
    ├── __init__.py
    ├── context/                # Phase 1: 上下文工程
    │   ├── __init__.py
    │   ├── agents_md.py
    │   ├── prompt_templates.py
    │   ├── schema_registry.py
    │   └── constraints.py
    ├── guardrails/             # Phase 1: 护栏系统
    │   ├── __init__.py
    │   ├── input_guard.py
    │   ├── output_guard.py
    │   ├── permission_guard.py
    │   └── registry.py
    ├── tools/                  # 增强现有工具编排
    │   ├── __init__.py
    │   ├── registry.py         # 增强
    │   ├── router.py
    │   └── permissions.py
    ├── verification/           # Phase 2: 验证纠错
    │   ├── __init__.py
    │   ├── self_verifier.py
    │   ├── error_recovery.py
    │   └── retry_policy.py
    ├── memory/                 # Phase 2: 状态记忆
    │   ├── __init__.py
    │   ├── session_state.py
    │   ├── vector_store.py
    │   ├── context_compressor.py
    │   └── checkpoint.py
    ├── observability/          # Phase 2: 可观测性
    │   ├── __init__.py
    │   ├── logger.py
    │   ├── metrics.py
    │   └── tracer.py
    ├── multi_agent/            # Phase 3: 多Agent协作
    │   ├── __init__.py
    │   ├── coordinator.py
    │   ├── planner_agent.py
    │   ├── executor_agent.py
    │   ├── verifier_agent.py
    │   └── communication.py
    ├── entropy/               # Phase 3: 熵管理
    │   ├── __init__.py
    │   ├── consistency_checker.py
    │   ├── dead_code_finder.py
    │   ├── violation_scanner.py
    │   └── cleanup_agent.py
    └── engine.py               # Harness引擎（统一入口）
```

### 4.2 核心接口设计

```python
# backend/app/harness/engine.py
"""Harness Engine - 统一入口"""

class HarnessEngine:
    """
    AI Harness 引擎
    
    统一管理六大组件，为AI Agent提供完整的运行环境
    """
    
    def __init__(self, config: HarnessConfig):
        # 初始化各组件
        self.context = ContextManager(config.context)
        self.guardrails = GuardrailRegistry(config.guardrails)
        self.tools = ToolRegistry(config.tools)
        self.verifier = SelfVerifier(config.verification)
        self.memory = SessionManager(config.memory)
        self.observability = AgentLogger()
    
    async def process(self, request: AgentRequest) -> AgentResponse:
        """处理AI Agent请求"""
        
        # 1. 护栏 - 输入检查
        guard_result = self.guardrails.check_input(request.user_input)
        if not guard_result.allowed:
            return AgentResponse(success=False, error=guard_result.reason)
        
        # 2. 上下文 - 注入项目知识
        enhanced_prompt = self.context.inject(request.prompt, request.task_type)
        
        # 3. 工具 - 准备可用工具
        available_tools = self.tools.get_tools_for_context(
            request.context, 
            request.user_level
        )
        
        # 4. 执行 - LLM调用
        llm_response = await self._call_llm(enhanced_prompt, available_tools)
        
        # 5. 护栏 - 输出校验
        guard_result = self.guardrails.check_output(llm_response)
        if not guard_result.allowed:
            # 触发验证纠错
            verified_response = await self.verifier.verify_and_fix(llm_response)
            llm_response = verified_response
        
        # 6. 记忆 - 保存状态
        self.memory.save(request.session_id, llm_response)
        
        # 7. 可观测性 - 记录日志
        self.observability.log_request(request, llm_response)
        
        return AgentResponse(success=True, data=llm_response)
```

---

## 五、重构优先级矩阵

| 组件 | 优先级 | 工作量 | 风险 | 收益 |
|------|-------|-------|------|------|
| 上下文工程基础 | P0 | 中 | 低 | 高 |
| 护栏系统 | P0 | 中 | 中 | 高 |
| 工具编排增强 | P0 | 低 | 低 | 中 |
| 验证纠错 | P1 | 高 | 中 | 高 |
| 状态记忆 | P1 | 中 | 低 | 高 |
| 可观测性 | P2 | 中 | 低 | 中 |
| 多Agent协作 | P2 | 高 | 高 | 高 |
| 熵管理 | P3 | 高 | 中 | 中 |

---

## 六、预期收益

### 6.1 量化指标

| 指标 | 当前 | Phase 1后 | Phase 2后 | Phase 3后 |
|------|------|----------|----------|----------|
| AI任务成功率 | ~60% | 75% | 85% | 90%+ |
| 人工干预率 | 高 | 中 | 低 | 最低 |
| 平均任务耗时 | 基准 | -20% | -40% | -50% |
| 上下文窗口利用率 | ~40% | 60% | 75% | 85% |

### 6.2 质量提升

- ✅ **可控性**：危险操作被拦截，输出符合Schema
- ✅ **可靠性**：自我验证减少错误自动恢复
- ✅ **可维护性**：熵管理保持系统健康
- ✅ **可观测性**：完整调用链追踪

---

## 七、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|-----|---------|
| 组件间耦合 | 中 | 使用依赖注入，保持接口稳定 |
| 性能开销 | 中 | 按需加载，可配置开关 |
| 用户体验变化 | 低 | 保持API兼容，逐步迁移 |
| 多Agent复杂性 | 高 | Phase 3再引入，先验证单Agent效果 |

---

**文档版本**：v1.0  
**下一步行动**：确认 Phase 1 实施细节，开始上下文工程和护栏系统开发
