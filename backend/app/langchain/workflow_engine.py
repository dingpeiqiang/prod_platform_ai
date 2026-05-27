"""
通用工作流执行引擎

支持通过配置定义工作流，无需硬编码业务逻辑：
1. JSON/YAML 配置定义工作流
2. 支持条件分支、循环、并行执行
3. 支持步骤跳过、重试、异常处理
4. 支持等待用户输入（如表单提交）
5. 与现有业务组件无缝集成
"""
from typing import Optional, Dict, Any, List, Callable, AsyncGenerator, Union
from dataclasses import dataclass, field, asdict
from enum import Enum
from datetime import datetime
import json
from app.core.logger import get_logger

logger = get_logger(__name__)
import asyncio



class WorkflowStatus(str, Enum):
    """工作流状态"""
    PENDING = "pending"
    RUNNING = "running"
    WAITING = "waiting"
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
    ACTION = "action"
    CONDITIONAL = "conditional"
    LOOP = "loop"
    PARALLEL = "parallel"
    SUBWORKFLOW = "subworkflow"


@dataclass
class StepDefinition:
    """步骤定义（可序列化）"""
    id: str
    name: str
    type: StepType = StepType.ACTION
    action: Optional[str] = None
    action_params: Dict[str, Any] = field(default_factory=dict)
    condition: Optional[str] = None
    next_step: Optional[str] = None
    next_steps: Dict[str, str] = field(default_factory=dict)
    loop_count: int = 1
    loop_condition: Optional[str] = None
    parallel_steps: List[str] = field(default_factory=list)
    subworkflow: Optional[str] = None
    retry_count: int = 0
    retry_delay: int = 1
    skip_if: Optional[str] = None
    timeout: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return asdict(self)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'StepDefinition':
        """从字典创建"""
        return cls(**data)


@dataclass
class WorkflowDefinition:
    """工作流定义（可序列化）"""
    id: str
    name: str
    description: Optional[str] = None
    version: str = "1.0"
    start_step: str = "start"
    steps: Dict[str, StepDefinition] = field(default_factory=dict)
    variables: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return {
            'id': self.id,
            'name': self.name,
            'description': self.description,
            'version': self.version,
            'start_step': self.start_step,
            'steps': {k: v.to_dict() for k, v in self.steps.items()},
            'variables': self.variables
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'WorkflowDefinition':
        """从字典创建"""
        steps = {
            k: StepDefinition.from_dict(v) 
            for k, v in data.get("steps", {}).items()
        }
        return cls(
            id=data["id"],
            name=data["name"],
            description=data.get("description"),
            version=data.get("version", "1.0"),
            start_step=data.get("start_step", "start"),
            steps=steps,
            variables=data.get("variables", {})
        )


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
    waiting_step_id: Optional[str] = None
    waiting_form: Optional[Dict[str, Any]] = None

    def to_dict(self) -> Dict[str, Any]:
        """转换为字典（用于持久化）"""
        return {
            'workflow_id': self.workflow_id,
            'definition': self.definition.to_dict(),
            'inputs': self.inputs,
            'outputs': self.outputs,
            'status': self.status.value if isinstance(self.status, WorkflowStatus) else self.status,
            'current_step_id': self.current_step_id,
            'step_results': self.step_results,
            'errors': self.errors,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'started_at': self.started_at.isoformat() if self.started_at else None,
            'completed_at': self.completed_at.isoformat() if self.completed_at else None,
            'waiting_step_id': self.waiting_step_id,
            'waiting_form': self.waiting_form
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'ExecutionContext':
        """从字典创建（用于恢复）"""
        definition = WorkflowDefinition.from_dict(data['definition'])
        created_at = datetime.fromisoformat(data['created_at']) if data.get('created_at') else datetime.now()
        started_at = datetime.fromisoformat(data['started_at']) if data.get('started_at') else None
        completed_at = datetime.fromisoformat(data['completed_at']) if data.get('completed_at') else None
        status = WorkflowStatus(data['status']) if isinstance(data['status'], str) else data['status']
        
        return cls(
            workflow_id=data['workflow_id'],
            definition=definition,
            inputs=data.get('inputs', {}),
            outputs=data.get('outputs', {}),
            status=status,
            current_step_id=data.get('current_step_id'),
            step_results=data.get('step_results', {}),
            errors=data.get('errors', []),
            created_at=created_at,
            started_at=started_at,
            completed_at=completed_at,
            waiting_step_id=data.get('waiting_step_id'),
            waiting_form=data.get('waiting_form')
        )


class WorkflowEngine:
    """
    通用工作流执行引擎
    
    核心能力：
    1. 解析工作流定义（JSON/YAML）
    2. 执行工作流步骤
    3. 处理条件分支和循环
    4. 管理执行状态和上下文
    5. 支持动作注册和扩展
    6. 支持等待用户输入（如表单提交）
    """
    
    def __init__(self):
        self._action_registry = {}
        self._workflow_registry = {}
        self._context_storage = {}
    
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
    
    def save_context(self, context: ExecutionContext):
        """保存执行上下文（持久化）"""
        self._context_storage[context.workflow_id] = context.to_dict()
        logger.info(f"[WorkflowEngine] 保存上下文: {context.workflow_id}")
    
    def get_context(self, workflow_id: str) -> Optional[ExecutionContext]:
        """获取执行上下文"""
        data = self._context_storage.get(workflow_id)
        if data:
            return ExecutionContext.from_dict(data)
        return None
    
    def remove_context(self, workflow_id: str):
        """移除执行上下文"""
        if workflow_id in self._context_storage:
            del self._context_storage[workflow_id]
            logger.info(f"[WorkflowEngine] 移除上下文: {workflow_id}")
    
    async def run(self, workflow_id: str, inputs: Optional[Dict[str, Any]] = None) -> AsyncGenerator[Dict[str, Any], None]:
        """执行工作流"""
        if workflow_id not in self._workflow_registry:
            raise ValueError(f"工作流未注册: {workflow_id}")
        
        definition = self._workflow_registry[workflow_id]
        
        context = ExecutionContext(
            workflow_id=f"exec_{datetime.now().strftime('%Y%m%d%H%M%S%f')}",
            definition=definition,
            inputs=inputs or {},
            outputs={},
            status=WorkflowStatus.RUNNING,
            started_at=datetime.now()
        )
        
        context.outputs.update(definition.variables)
        
        yield {"type": "workflow_start", "workflow_id": context.workflow_id, "definition_id": workflow_id}
        
        try:
            current_step_id = definition.start_step
            
            while current_step_id:
                context.current_step_id = current_step_id
                
                if current_step_id not in definition.steps:
                    break
                
                step_def = definition.steps[current_step_id]
                
                if step_def.skip_if and self._eval_expression(step_def.skip_if, context):
                    yield {"type": "step_skipped", "step": current_step_id, "name": step_def.name}
                    current_step_id = step_def.next_step
                    continue
                
                yield {"type": "step_start", "step": current_step_id, "name": step_def.name}
                
                try:
                    result = await self._execute_step(step_def, context)
                    
                    if isinstance(result, dict) and result.get("action") == "ask_user":
                        context.status = WorkflowStatus.WAITING
                        context.waiting_step_id = current_step_id
                        context.waiting_form = result.get("form_schema")
                        
                        self.save_context(context)
                        
                        yield {
                            "type": "workflow_waiting",
                            "workflow_id": context.workflow_id,
                            "step": current_step_id,
                            "waiting_form": result.get("form_schema"),
                            "message": result.get("message", "请填写表单")
                        }
                        return
                    
                    context.step_results[current_step_id] = result
                    
                    yield {"type": "step_complete", "step": current_step_id, "name": step_def.name, "result": result}
                    
                    current_step_id = self._determine_next_step(step_def, context)
                    
                except Exception as e:
                    logger.error(f"步骤执行失败 {current_step_id}: {e}")
                    context.errors.append({"step": current_step_id, "error": str(e)})
                    yield {"type": "step_failed", "step": current_step_id, "name": step_def.name, "error": str(e)}
                    
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
            
            context.status = WorkflowStatus.COMPLETED
            context.completed_at = datetime.now()
            
            yield {"type": "workflow_complete", "workflow_id": context.workflow_id, "outputs": context.outputs}
            
        except Exception as e:
            logger.error(f"工作流执行失败: {e}")
            context.status = WorkflowStatus.FAILED
            context.error = str(e)
            yield {"type": "workflow_failed", "workflow_id": context.workflow_id, "error": str(e)}
    
    async def resume(self, workflow_id: str, user_input: Dict[str, Any]) -> AsyncGenerator[Dict[str, Any], None]:
        """恢复执行（用户提交表单后调用）"""
        context = self.get_context(workflow_id)
        
        if not context:
            raise ValueError(f"未找到执行上下文: {workflow_id}")
        
        if context.status != WorkflowStatus.WAITING:
            raise ValueError(f"工作流不在等待状态: {context.status}")
        
        waiting_step_id = context.waiting_step_id
        
        context.step_results[waiting_step_id] = {
            "action": "form_submit",
            "form_data": user_input,
            "submitted_at": datetime.now().isoformat()
        }
        
        context.inputs.update(user_input)
        context.outputs.update(user_input)
        
        context.status = WorkflowStatus.RUNNING
        context.waiting_step_id = None
        context.waiting_form = None
        
        self.save_context(context)
        
        yield {
            "type": "workflow_resumed",
            "workflow_id": context.workflow_id,
            "step": waiting_step_id,
            "user_input": user_input
        }
        
        definition = context.definition
        step_def = definition.steps[waiting_step_id]
        current_step_id = self._determine_next_step(step_def, context)
        
        try:
            while current_step_id:
                context.current_step_id = current_step_id
                
                if current_step_id not in definition.steps:
                    break
                
                step_def = definition.steps[current_step_id]
                
                if step_def.skip_if and self._eval_expression(step_def.skip_if, context):
                    yield {"type": "step_skipped", "step": current_step_id, "name": step_def.name}
                    current_step_id = step_def.next_step
                    continue
                
                yield {"type": "step_start", "step": current_step_id, "name": step_def.name}
                
                try:
                    result = await self._execute_step(step_def, context)
                    
                    if isinstance(result, dict) and result.get("action") == "ask_user":
                        context.status = WorkflowStatus.WAITING
                        context.waiting_step_id = current_step_id
                        context.waiting_form = result.get("form_schema")
                        
                        self.save_context(context)
                        
                        yield {
                            "type": "workflow_waiting",
                            "workflow_id": context.workflow_id,
                            "step": current_step_id,
                            "waiting_form": result.get("form_schema"),
                            "message": result.get("message", "请填写表单")
                        }
                        return
                    
                    context.step_results[current_step_id] = result
                    
                    yield {"type": "step_complete", "step": current_step_id, "name": step_def.name, "result": result}
                    
                    current_step_id = self._determine_next_step(step_def, context)
                    
                except Exception as e:
                    logger.error(f"步骤执行失败 {current_step_id}: {e}")
                    context.errors.append({"step": current_step_id, "error": str(e)})
                    yield {"type": "step_failed", "step": current_step_id, "name": step_def.name, "error": str(e)}
                    raise e
            
            context.status = WorkflowStatus.COMPLETED
            context.completed_at = datetime.now()
            self.remove_context(workflow_id)
            
            yield {"type": "workflow_complete", "workflow_id": context.workflow_id, "outputs": context.outputs}
            
        except Exception as e:
            logger.error(f"工作流执行失败: {e}")
            context.status = WorkflowStatus.FAILED
            context.error = str(e)
            self.remove_context(workflow_id)
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
        
        params = self._resolve_params(step_def.action_params, context)
        
        handler = self._action_registry[step_def.action]
        
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
            context.outputs["loop_index"] = i
            context.outputs["loop_count"] = count
            
            if step_def.loop_condition and not self._eval_expression(step_def.loop_condition, context):
                break
            
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
        
        async for event in self.run(step_def.subworkflow, context.inputs):
            if event["type"] == "workflow_complete":
                return event.get("outputs", {})
        
        return {}
    
    def _determine_next_step(self, step_def: StepDefinition, context: ExecutionContext) -> Optional[str]:
        """确定下一步骤"""
        if step_def.type == StepType.CONDITIONAL and step_def.next_steps:
            condition_result = context.step_results.get(step_def.id, {}).get("condition_result")
            return step_def.next_steps.get(str(condition_result), step_def.next_step)
        
        if step_def.type == StepType.LOOP:
            loop_count = context.outputs.get("loop_count", 1)
            loop_index = context.outputs.get("loop_index", 0)
            if loop_index < loop_count - 1:
                return step_def.next_step
            else:
                return step_def.next_steps.get("exit")
        
        return step_def.next_step
    
    def _eval_expression(self, expression: str, context: ExecutionContext) -> bool:
        """计算条件表达式"""
        try:
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
                var_name = value[2:-2].strip()
                resolved[key] = context.outputs.get(var_name, context.inputs.get(var_name, value))
            else:
                resolved[key] = value
        
        return resolved


workflow_engine = WorkflowEngine()
