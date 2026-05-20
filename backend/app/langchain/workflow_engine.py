"""
通用工作流执行引擎

支持通过配置定义工作流，无需硬编码业务逻辑：
1. JSON/YAML 配置定义工作流
2. 支持条件分支、循环、并行执行
3. 支持步骤跳过、重试、异常处理
4. 与现有业务组件无缝集成
"""
from typing import Optional, Dict, Any, List, Callable, AsyncGenerator, Union
from dataclasses import dataclass, field
from enum import Enum
from datetime import datetime
import json
import logging
import asyncio

logger = logging.getLogger("workflow_engine")


class WorkflowStatus(str, Enum):
    """工作流状态"""
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    CANCELLED = "cancelled"


class StepStatus(str, Enum):
    """步骤状态"""
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    SKIPPED = "skipped"
    FAILED = "failed"


class StepType(str, Enum):
    """步骤类型"""
    ACTION = "action"           # 执行动作
    CONDITIONAL = "conditional" # 条件分支
    LOOP = "loop"               # 循环
    PARALLEL = "parallel"       # 并行执行
    SUBWORKFLOW = "subworkflow" # 子工作流


@dataclass
class StepDefinition:
    """步骤定义（可序列化）"""
    id: str
    name: str
    type: StepType = StepType.ACTION
    action: Optional[str] = None           # 动作名称（对应注册的处理器）
    action_params: Dict[str, Any] = field(default_factory=dict)
    condition: Optional[str] = None        # 条件表达式
    next_step: Optional[str] = None        # 下一步骤ID
    next_steps: Dict[str, str] = field(default_factory=dict)  # 条件分支的下一步
    loop_count: int = 1                    # 循环次数
    loop_condition: Optional[str] = None   # 循环条件
    parallel_steps: List[str] = field(default_factory=list)  # 并行步骤ID列表
    subworkflow: Optional[str] = None      # 子工作流ID
    retry_count: int = 0                   # 重试次数
    retry_delay: int = 1                   # 重试延迟（秒）
    skip_if: Optional[str] = None          # 跳过条件
    timeout: Optional[int] = None          # 超时时间（秒）


@dataclass
class WorkflowDefinition:
    """工作流定义（可序列化）"""
    id: str
    name: str
    description: Optional[str] = None
    version: str = "1.0"
    start_step: str = "start"
    steps: Dict[str, StepDefinition] = field(default_factory=dict)
    variables: Dict[str, Any] = field(default_factory=dict)  # 全局变量


@dataclass
class ExecutionContext:
    """执行上下文"""
    workflow_id: str
    definition: WorkflowDefinition
    inputs: Dict[str, Any] = field(default_factory=dict)
    outputs: Dict[str, Any] = field(default_factory=dict)
    status: WorkflowStatus = WorkflowStatus.PENDING
    current_step_id: Optional[str] = None
    step_results: Dict[str, Any] = field(default_factory=dict)
    errors: List[Dict[str, Any]] = field(default_factory=list)
    created_at: datetime = field(default_factory=datetime.now)
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None


class WorkflowEngine:
    """
    通用工作流执行引擎
    
    核心能力：
    1. 解析工作流定义（JSON/YAML）
    2. 执行工作流步骤
    3. 处理条件分支和循环
    4. 管理执行状态和上下文
    5. 支持动作注册和扩展
    """
    
    def __init__(self):
        self._action_registry = {}  # 动作处理器注册表
        self._workflow_registry = {}  # 工作流定义注册表
    
    def register_action(self, action_name: str, handler: Callable):
        """注册动作处理器"""
        self._action_registry[action_name] = handler
        logger.info(f"[WorkflowEngine] 注册动作: {action_name}")
    
    def register_workflow(self, definition: WorkflowDefinition):
        """注册工作流定义"""
        self._workflow_registry[definition.id] = definition
        logger.info(f"[WorkflowEngine] 注册工作流: {definition.id}")
    
    def load_workflow_from_json(self, json_str: str) -> WorkflowDefinition:
        """从JSON加载工作流定义"""
        data = json.loads(json_str)
        return self._parse_workflow_definition(data)
    
    def load_workflow_from_file(self, file_path: str) -> WorkflowDefinition:
        """从文件加载工作流定义"""
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        return self._parse_workflow_definition(data)
    
    def _parse_workflow_definition(self, data: Dict[str, Any]) -> WorkflowDefinition:
        """解析工作流定义"""
        steps = {}
        for step_id, step_data in data.get("steps", {}).items():
            steps[step_id] = StepDefinition(
                id=step_id,
                name=step_data.get("name", step_id),
                type=StepType(step_data.get("type", "action")),
                action=step_data.get("action"),
                action_params=step_data.get("action_params", {}),
                condition=step_data.get("condition"),
                next_step=step_data.get("next_step"),
                next_steps=step_data.get("next_steps", {}),
                loop_count=step_data.get("loop_count", 1),
                loop_condition=step_data.get("loop_condition"),
                parallel_steps=step_data.get("parallel_steps", []),
                subworkflow=step_data.get("subworkflow"),
                retry_count=step_data.get("retry_count", 0),
                retry_delay=step_data.get("retry_delay", 1),
                skip_if=step_data.get("skip_if"),
                timeout=step_data.get("timeout")
            )
        
        return WorkflowDefinition(
            id=data["id"],
            name=data["name"],
            description=data.get("description"),
            version=data.get("version", "1.0"),
            start_step=data.get("start_step", "start"),
            steps=steps,
            variables=data.get("variables", {})
        )
    
    async def run(self, workflow_id: str, inputs: Optional[Dict[str, Any]] = None) -> AsyncGenerator[Dict[str, Any], None]:
        """执行工作流"""
        # 获取工作流定义
        if workflow_id not in self._workflow_registry:
            raise ValueError(f"工作流未注册: {workflow_id}")
        
        definition = self._workflow_registry[workflow_id]
        
        # 创建执行上下文
        context = ExecutionContext(
            workflow_id=f"exec_{datetime.now().strftime('%Y%m%d%H%M%S')}",
            definition=definition,
            inputs=inputs or {},
            outputs={},
            status=WorkflowStatus.RUNNING,
            started_at=datetime.now()
        )
        
        # 合并全局变量
        context.outputs.update(definition.variables)
        
        yield {"type": "workflow_start", "workflow_id": context.workflow_id, "definition_id": workflow_id}
        
        try:
            # 从开始步骤执行
            current_step_id = definition.start_step
            
            while current_step_id:
                context.current_step_id = current_step_id
                
                if current_step_id not in definition.steps:
                    break
                
                step_def = definition.steps[current_step_id]
                
                # 检查跳过条件
                if step_def.skip_if and self._eval_expression(step_def.skip_if, context):
                    yield {"type": "step_skipped", "step": current_step_id, "name": step_def.name}
                    current_step_id = step_def.next_step
                    continue
                
                yield {"type": "step_start", "step": current_step_id, "name": step_def.name}
                
                try:
                    # 根据步骤类型执行
                    result = await self._execute_step(step_def, context)
                    context.step_results[current_step_id] = result
                    
                    yield {"type": "step_complete", "step": current_step_id, "name": step_def.name, "result": result}
                    
                    # 确定下一步
                    current_step_id = self._determine_next_step(step_def, context)
                    
                except Exception as e:
                    logger.error(f"步骤执行失败 {current_step_id}: {e}")
                    context.errors.append({"step": current_step_id, "error": str(e)})
                    yield {"type": "step_failed", "step": current_step_id, "name": step_def.name, "error": str(e)}
                    
                    # 处理重试
                    if step_def.retry_count > 0:
                        retry_count = step_def.retry_count
                        while retry_count > 0:
                            await asyncio.sleep(step_def.retry_delay)
                            retry_count -= 1
                            try:
                                result = await self._execute_step(step_def, context)
                                context.step_results[current_step_id] = result
                                yield {"type": "step_retry_success", "step": current_step_id, "name": step_def.name}
                                current_step_id = self._determine_next_step(step_def, context)
                                break
                            except Exception as retry_e:
                                logger.warning(f"重试失败 {current_step_id}: {retry_e}")
                                if retry_count == 0:
                                    raise e
                    else:
                        raise e
            
            # 完成工作流
            context.status = WorkflowStatus.COMPLETED
            context.completed_at = datetime.now()
            
            yield {"type": "workflow_complete", "workflow_id": context.workflow_id, "outputs": context.outputs}
            
        except Exception as e:
            logger.error(f"工作流执行失败: {e}")
            context.status = WorkflowStatus.FAILED
            context.error = str(e)
            yield {"type": "workflow_failed", "workflow_id": context.workflow_id, "error": str(e)}
    
    async def _execute_step(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行单个步骤"""
        if step_def.type == StepType.ACTION:
            return await self._execute_action(step_def, context)
        elif step_def.type == StepType.CONDITIONAL:
            return await self._execute_conditional(step_def, context)
        elif step_def.type == StepType.LOOP:
            return await self._execute_loop(step_def, context)
        elif step_def.type == StepType.PARALLEL:
            return await self._execute_parallel(step_def, context)
        elif step_def.type == StepType.SUBWORKFLOW:
            return await self._execute_subworkflow(step_def, context)
        else:
            raise ValueError(f"未知步骤类型: {step_def.type}")
    
    async def _execute_action(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行动作步骤"""
        if not step_def.action:
            raise ValueError(f"步骤 {step_def.id} 未指定动作")
        
        if step_def.action not in self._action_registry:
            raise ValueError(f"动作未注册: {step_def.action}")
        
        # 解析参数（支持上下文变量引用）
        params = self._resolve_params(step_def.action_params, context)
        
        # 调用动作处理器
        handler = self._action_registry[step_def.action]
        
        # 支持同步和异步处理器
        if asyncio.iscoroutinefunction(handler):
            return await handler(context, **params)
        else:
            return handler(context, **params)
    
    async def _execute_conditional(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行条件分支步骤"""
        if not step_def.condition:
            raise ValueError(f"条件步骤 {step_def.id} 未指定条件")
        
        result = self._eval_expression(step_def.condition, context)
        return {"condition_result": result}
    
    async def _execute_loop(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行循环步骤"""
        results = []
        count = step_def.loop_count
        
        for i in range(count):
            # 更新循环变量
            context.outputs["loop_index"] = i
            context.outputs["loop_count"] = count
            
            # 检查动态循环条件
            if step_def.loop_condition and not self._eval_expression(step_def.loop_condition, context):
                break
            
            # 执行子步骤
            if step_def.next_step and step_def.next_step in context.definition.steps:
                sub_step = context.definition.steps[step_def.next_step]
                result = await self._execute_step(sub_step, context)
                results.append(result)
        
        return {"loop_results": results}
    
    async def _execute_parallel(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行并行步骤"""
        tasks = []
        
        for step_id in step_def.parallel_steps:
            if step_id in context.definition.steps:
                sub_step = context.definition.steps[step_id]
                tasks.append(self._execute_step(sub_step, context))
        
        results = await asyncio.gather(*tasks)
        return {"parallel_results": dict(zip(step_def.parallel_steps, results))}
    
    async def _execute_subworkflow(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行子工作流"""
        if not step_def.subworkflow:
            raise ValueError(f"子工作流步骤 {step_def.id} 未指定子工作流")
        
        # 递归执行子工作流
        async for event in self.run(step_def.subworkflow, context.inputs):
            if event["type"] == "workflow_complete":
                return event.get("outputs", {})
        
        return {}
    
    def _determine_next_step(self, step_def: StepDefinition, context: ExecutionContext) -> Optional[str]:
        """确定下一步骤"""
        # 条件分支
        if step_def.type == StepType.CONDITIONAL and step_def.next_steps:
            condition_result = context.step_results.get(step_def.id, {}).get("condition_result")
            return step_def.next_steps.get(str(condition_result), step_def.next_step)
        
        # 循环步骤继续执行循环体或退出
        if step_def.type == StepType.LOOP:
            loop_count = context.outputs.get("loop_count", 1)
            loop_index = context.outputs.get("loop_index", 0)
            if loop_index < loop_count - 1:
                return step_def.next_step  # 继续循环
            else:
                return step_def.next_steps.get("exit")  # 退出循环
        
        # 默认下一步
        return step_def.next_step
    
    def _eval_expression(self, expression: str, context: ExecutionContext) -> bool:
        """计算条件表达式"""
        try:
            # 构建安全的评估环境
            env = {
                **context.inputs,
                **context.outputs,
                **context.step_results,
                '__builtins__': {}
            }
            return bool(eval(expression, env))
        except Exception as e:
            logger.error(f"表达式计算失败 '{expression}': {e}")
            return False
    
    def _resolve_params(self, params: Dict[str, Any], context: ExecutionContext) -> Dict[str, Any]:
        """解析参数（支持变量引用）"""
        resolved = {}
        
        for key, value in params.items():
            if isinstance(value, str) and value.startswith("{{") and value.endswith("}}"):
                # 解析变量引用 {{variable_name}}
                var_name = value[2:-2].strip()
                resolved[key] = context.outputs.get(var_name, context.inputs.get(var_name, value))
            else:
                resolved[key] = value
        
        return resolved


# 全局工作流引擎实例
workflow_engine = WorkflowEngine()

