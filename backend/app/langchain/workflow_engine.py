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
    START = "start"
    END = "end"


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
    input_params: Dict[str, Any] = field(default_factory=dict)
    output_params: List[str] = field(default_factory=list)

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
    logs: List[Dict[str, Any]] = field(default_factory=list)
    created_at: datetime = field(default_factory=datetime.now)
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    waiting_step_id: Optional[str] = None
    waiting_form: Optional[Dict[str, Any]] = None
    
    def add_log(self, **kwargs):
        """添加执行日志"""
        log_entry = {
            'timestamp': datetime.now().isoformat(),
            **kwargs
        }
        self.logs.append(log_entry)

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
            'logs': self.logs,
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
            logs=data.get('logs', []),
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
        self._workflow_registry = {}
        self._context_storage = {}
    
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
        
        log_event = {"type": "workflow_start", "workflow_id": context.workflow_id, "definition_id": workflow_id}
        context.add_log(**log_event)
        logger.info(f"[WorkflowEngine] 开始执行工作流: {workflow_id}, 执行ID: {context.workflow_id}, 输入参数: {inputs}")
        yield log_event
        
        try:
            current_step_id = definition.start_step
            
            while current_step_id:
                context.current_step_id = current_step_id
                
                if current_step_id not in definition.steps:
                    break
                
                step_def = definition.steps[current_step_id]
                
                if step_def.skip_if and self._eval_expression(step_def.skip_if, context):
                    log_event = {"type": "step_skipped", "step": current_step_id, "name": step_def.name}
                    context.add_log(**log_event)
                    logger.info(f"[WorkflowEngine] 跳过步骤: {current_step_id} ({step_def.name})")
                    yield log_event
                    current_step_id = step_def.next_step
                    continue
                
                log_event = {"type": "step_start", "step": current_step_id, "name": step_def.name}
                context.add_log(**log_event)
                logger.info(f"[WorkflowEngine] 执行步骤: {current_step_id} ({step_def.name}), 类型: {step_def.type}")
                yield log_event
                
                try:
                    result = await self._execute_step(step_def, context)
                    
                    if isinstance(result, dict) and result.get("action") == "ask_user":
                        context.status = WorkflowStatus.WAITING
                        context.waiting_step_id = current_step_id
                        context.waiting_form = result.get("form_schema")
                        
                        self.save_context(context)
                        
                        log_event = {
                            "type": "workflow_waiting",
                            "workflow_id": context.workflow_id,
                            "step": current_step_id,
                            "waiting_form": result.get("form_schema"),
                            "message": result.get("message", "请填写表单")
                        }
                        context.add_log(**log_event)
                        logger.info(f"[WorkflowEngine] 工作流等待用户输入: {context.workflow_id}, 当前步骤: {current_step_id}")
                        yield log_event
                        return
                    
                    context.step_results[current_step_id] = result
                    
                    # 将步骤的输出参数添加到上下文中
                    step_output = result.get('output', result)
                    if isinstance(step_output, dict):
                        if step_def.output_params:
                            # 如果配置了 output_params，只添加指定的参数
                            for param_name in step_def.output_params:
                                if param_name in step_output:
                                    context.outputs[param_name] = step_output[param_name]
                                    logger.debug(f"[WorkflowEngine] 步骤 [{current_step_id}] 输出参数 '{param_name}' 已添加到上下文")
                        else:
                            # 如果没有配置 output_params，自动将所有输出字段添加到上下文
                            for key, value in step_output.items():
                                context.outputs[key] = value
                                logger.debug(f"[WorkflowEngine] 步骤 [{current_step_id}] 自动添加输出字段 '{key}' 到上下文")
                    
                    log_event = {"type": "step_complete", "step": current_step_id, "name": step_def.name, "result": result}
                    context.add_log(**log_event)
                    logger.info(f"[WorkflowEngine] 步骤完成: {current_step_id} ({step_def.name}), 结果: {str(result)[:100]}")
                    yield log_event
                    
                    current_step_id = self._determine_next_step(step_def, context)
                    
                except Exception as e:
                    logger.error(f"步骤执行失败 {current_step_id}: {e}")
                    context.errors.append({"step": current_step_id, "error": str(e)})
                    log_event = {"type": "step_failed", "step": current_step_id, "name": step_def.name, "error": str(e)}
                    context.add_log(**log_event)
                    yield log_event
                    
                    if step_def.retry_count > 0:
                        retry_count = step_def.retry_count
                        while retry_count > 0:
                            await asyncio.sleep(step_def.retry_delay)
                            retry_count -= 1
                            try:
                                result = await self._execute_step(step_def, context)
                                context.step_results[current_step_id] = result
                                
                                # 将步骤的输出参数添加到上下文中
                                step_output = result.get('output', result)
                                if isinstance(step_output, dict):
                                    if step_def.output_params:
                                        # 如果配置了 output_params，只添加指定的参数
                                        for param_name in step_def.output_params:
                                            if param_name in step_output:
                                                context.outputs[param_name] = step_output[param_name]
                                                logger.debug(f"[WorkflowEngine] 步骤 [{current_step_id}] 输出参数 '{param_name}' 已添加到上下文")
                                    else:
                                        # 如果没有配置 output_params，自动将所有输出字段添加到上下文
                                        for key, value in step_output.items():
                                            context.outputs[key] = value
                                            logger.debug(f"[WorkflowEngine] 步骤 [{current_step_id}] 自动添加输出字段 '{key}' 到上下文")
                                
                                log_event = {"type": "step_retry_success", "step": current_step_id, "name": step_def.name}
                                context.add_log(**log_event)
                                yield log_event
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
            
            log_event = {"type": "workflow_complete", "workflow_id": context.workflow_id, "outputs": context.outputs}
            context.add_log(**log_event)
            
            # 组装节点执行日志汇总
            execution_summary = self._build_execution_summary(context)
            logger.info(f"[WorkflowEngine] 工作流执行完成: {workflow_id}, 执行ID: {context.workflow_id}, 耗时: {(context.completed_at - context.started_at).total_seconds():.2f}s")
            logger.info(f"[WorkflowEngine] 节点执行日志汇总:\n{execution_summary}")
            yield log_event
            
        except Exception as e:
            logger.error(f"工作流执行失败: {e}")
            context.status = WorkflowStatus.FAILED
            context.error = str(e)
            log_event = {"type": "workflow_failed", "workflow_id": context.workflow_id, "error": str(e)}
            context.add_log(**log_event)
            yield log_event
    
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
                    log_event = {"type": "step_skipped", "step": current_step_id, "name": step_def.name}
                    context.add_log(**log_event)
                    logger.info(f"[WorkflowEngine] 跳过步骤: {current_step_id} ({step_def.name})")
                    yield log_event
                    current_step_id = step_def.next_step
                    continue
                
                log_event = {"type": "step_start", "step": current_step_id, "name": step_def.name}
                context.add_log(**log_event)
                logger.info(f"[WorkflowEngine] 执行步骤: {current_step_id} ({step_def.name}), 类型: {step_def.type}")
                yield log_event
                
                try:
                    result = await self._execute_step(step_def, context)
                    
                    if isinstance(result, dict) and result.get("action") == "ask_user":
                        context.status = WorkflowStatus.WAITING
                        context.waiting_step_id = current_step_id
                        context.waiting_form = result.get("form_schema")
                        
                        self.save_context(context)
                        
                        log_event = {
                            "type": "workflow_waiting",
                            "workflow_id": context.workflow_id,
                            "step": current_step_id,
                            "waiting_form": result.get("form_schema"),
                            "message": result.get("message", "请填写表单")
                        }
                        context.add_log(**log_event)
                        logger.info(f"[WorkflowEngine] 工作流等待用户输入: {context.workflow_id}, 当前步骤: {current_step_id}")
                        yield log_event
                        return
                    
                    context.step_results[current_step_id] = result
                    
                    # 将步骤的输出参数添加到上下文中
                    step_output = result.get('output', result)
                    if isinstance(step_output, dict):
                        if step_def.output_params:
                            # 如果配置了 output_params，只添加指定的参数
                            for param_name in step_def.output_params:
                                if param_name in step_output:
                                    context.outputs[param_name] = step_output[param_name]
                                    logger.debug(f"[WorkflowEngine] 步骤 [{current_step_id}] 输出参数 '{param_name}' 已添加到上下文")
                        else:
                            # 如果没有配置 output_params，自动将所有输出字段添加到上下文
                            for key, value in step_output.items():
                                context.outputs[key] = value
                                logger.debug(f"[WorkflowEngine] 步骤 [{current_step_id}] 自动添加输出字段 '{key}' 到上下文")
                    
                    log_event = {"type": "step_complete", "step": current_step_id, "name": step_def.name, "result": result}
                    context.add_log(**log_event)
                    logger.info(f"[WorkflowEngine] 步骤完成: {current_step_id} ({step_def.name}), 结果: {str(result)[:100]}")
                    yield log_event
                    
                    current_step_id = self._determine_next_step(step_def, context)
                    
                except Exception as e:
                    logger.error(f"步骤执行失败 {current_step_id}: {e}")
                    context.errors.append({"step": current_step_id, "error": str(e)})
                    log_event = {"type": "step_failed", "step": current_step_id, "name": step_def.name, "error": str(e)}
                    context.add_log(**log_event)
                    yield log_event
                    raise e
            
            context.status = WorkflowStatus.COMPLETED
            context.completed_at = datetime.now()
            self.remove_context(workflow_id)
            
            logger.info(f"[WorkflowEngine] 工作流恢复执行完成: {workflow_id}, 耗时: {(context.completed_at - context.started_at).total_seconds():.2f}s")
            yield {"type": "workflow_complete", "workflow_id": context.workflow_id, "outputs": context.outputs}
            
        except Exception as e:
            logger.error(f"工作流执行失败: {e}")
            context.status = WorkflowStatus.FAILED
            context.error = str(e)
            self.remove_context(workflow_id)
            yield {"type": "workflow_failed", "workflow_id": context.workflow_id, "error": str(e)}
    
    def _build_execution_summary(self, context: ExecutionContext) -> str:
        """组装节点执行日志汇总"""
        if not context.logs:
            return "  无执行日志"
        
        lines = []
        for log_entry in context.logs:
            log_type = log_entry.get("type", "unknown")
            timestamp = log_entry.get("timestamp", "")
            step = log_entry.get("step", "")
            name = log_entry.get("name", "")
            step_type = log_entry.get("type", "")
            
            if log_type == "workflow_start":
                lines.append(f"  🚀 [{timestamp}] 工作流开始: {log_entry.get('definition_id', '')}")
            elif log_type == "workflow_complete":
                outputs = log_entry.get("outputs", {})
                lines.append(f"  ✅ [{timestamp}] 工作流完成")
                if outputs:
                    lines.append(f"         └── 输出结果: {json.dumps(outputs, ensure_ascii=False)[:100]}")
            elif log_type == "workflow_failed":
                lines.append(f"  ❌ [{timestamp}] 工作流失败: {log_entry.get('error', '')}")
            elif log_type == "workflow_waiting":
                input_data = log_entry.get("input", {})
                processing = log_entry.get("processing", "")
                output_data = log_entry.get("output", {})
                
                lines.append(f"  ⏳ [{timestamp}] 等待用户输入: {step} ({name})")
                if input_data:
                    lines.append(f"         ├── 📥 输入: {json.dumps(input_data, ensure_ascii=False)[:80]}")
                if processing:
                    lines.append(f"         ├── ⚙️ 处理逻辑: {processing}")
                if output_data:
                    lines.append(f"         └── 📤 输出: {json.dumps(output_data, ensure_ascii=False)[:80]}")
            elif log_type == "step_start":
                input_data = log_entry.get("input", {})
                lines.append(f"    ▶ [{timestamp}] 开始执行: {step} ({name}) [{step_type}]")
                if input_data:
                    input_str = json.dumps(input_data, ensure_ascii=False)
                    input_str = input_str[:80] + "..." if len(input_str) > 80 else input_str
                    lines.append(f"         └── 📥 输入: {input_str}")
            elif log_type == "step_complete":
                result = log_entry.get("result", {})
                input_data = log_entry.get("input", {})
                processing = log_entry.get("processing", "")
                output_data = log_entry.get("output", {})
                
                lines.append(f"    ✅ [{timestamp}] 执行完成: {step} ({name}) [{step_type}]")
                
                if input_data:
                    input_str = json.dumps(input_data, ensure_ascii=False)
                    input_str = input_str[:80] + "..." if len(input_str) > 80 else input_str
                    lines.append(f"         ├── 📥 输入: {input_str}")
                
                if processing:
                    lines.append(f"         ├── ⚙️ 处理逻辑: {processing}")
                
                if output_data:
                    output_str = json.dumps(output_data, ensure_ascii=False)
                    output_str = output_str[:80] + "..." if len(output_str) > 80 else output_str
                    lines.append(f"         └── 📤 输出: {output_str}")
            elif log_type == "step_failed":
                input_data = log_entry.get("input", {})
                error = log_entry.get("error", "")
                
                lines.append(f"    ❌ [{timestamp}] 执行失败: {step} ({name})")
                if input_data:
                    input_str = json.dumps(input_data, ensure_ascii=False)[:80]
                    lines.append(f"         ├── 📥 输入: {input_str}")
                lines.append(f"         └── ❌ 错误: {error}")
            elif log_type == "step_skipped":
                lines.append(f"    ⏭️ [{timestamp}] 跳过步骤: {step} ({name})")
            elif log_type == "step_retry_success":
                lines.append(f"    🔄 [{timestamp}] 重试成功: {step} ({name})")
            else:
                lines.append(f"    📝 [{timestamp}] {log_type}: {step}")
        
        return "\n".join(lines)
    
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
        elif step_def.type == StepType.START:
            return await self._execute_start(step_def, context)
        elif step_def.type == StepType.END:
            return await self._execute_end(step_def, context)
        else:
            raise ValueError(f"未知步骤类型: {step_def.type}")
    
    async def _execute_start(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行开始步骤"""
        # 构建输入信息
        input_context = {
            "initial_inputs": context.inputs,
            "defined_params": step_def.input_params,
            "workflow_variables": context.definition.variables
        }
        
        # 记录输入日志
        logger.info(f"[WorkflowEngine] 开始步骤 [{step_def.id}] 输入信息:")
        logger.info(f"  ├── 初始输入: {json.dumps(context.inputs, ensure_ascii=False)}")
        logger.info(f"  ├── 定义参数: {json.dumps(step_def.input_params, ensure_ascii=False)}")
        logger.info(f"  └── 工作流变量: {json.dumps(context.definition.variables, ensure_ascii=False)}")
        
        # 构建处理逻辑描述
        processing = f"初始化工作流，设置输入参数 {list(step_def.input_params.keys())} 和工作流变量 {list(context.definition.variables.keys())}"
        
        # 记录处理日志
        logger.info(f"[WorkflowEngine] 开始步骤 [{step_def.id}] 处理逻辑:")
        logger.info(f"  └── {processing}")
        
        # 初始化输入参数到上下文中
        if step_def.input_params:
            for param_name, param_value in step_def.input_params.items():
                if param_name not in context.outputs:
                    context.outputs[param_name] = param_value
        
        # 记录输出日志
        logger.info(f"[WorkflowEngine] 开始步骤 [{step_def.id}] 输出信息:")
        logger.info(f"  └── 初始化后上下文: {json.dumps(context.outputs, ensure_ascii=False)[:200]}")
        
        return {
            "status": "started",
            "inputs": context.inputs,
            "outputs": context.outputs,
            "input": input_context,
            "processing": processing,
            "output": context.outputs
        }
    
    async def _execute_end(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行结束步骤"""
        # 构建输入信息
        input_context = {
            "requested_outputs": step_def.output_params,
            "available_context": context.outputs
        }
        
        # 记录输入日志
        logger.info(f"[WorkflowEngine] 结束步骤 [{step_def.id}] 输入信息:")
        logger.info(f"  ├── 请求输出参数: {step_def.output_params}")
        logger.info(f"  └── 当前上下文变量数: {len(context.outputs)}")
        
        # 构建处理逻辑描述
        processing = f"收集输出参数 {step_def.output_params}，从上下文中提取并组装最终输出"
        
        # 记录处理日志
        logger.info(f"[WorkflowEngine] 结束步骤 [{step_def.id}] 处理逻辑:")
        logger.info(f"  └── {processing}")
        
        # 收集输出参数
        outputs = {}
        if step_def.output_params:
            for param_name in step_def.output_params:
                if param_name in context.outputs:
                    outputs[param_name] = context.outputs[param_name]
        
        context.status = WorkflowStatus.COMPLETED
        
        # 记录输出日志
        logger.info(f"[WorkflowEngine] 结束步骤 [{step_def.id}] 输出信息:")
        logger.info(f"  └── 最终输出: {json.dumps(outputs, ensure_ascii=False)}")
        
        return {
            "status": "completed",
            "outputs": outputs,
            "input": input_context,
            "processing": processing,
            "output": outputs
        }
    
    async def _execute_action(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行动作步骤 - 使用新节点架构"""
        if not step_def.action:
            raise ValueError(f"步骤 {step_def.id} 未指定动作")
        
        from app.langchain.workflow_nodes import get_node
        
        node_class = get_node(step_def.action)
        if not node_class:
            raise ValueError(f"节点未注册: {step_def.action}")
        
        # 记录原始参数和解析后的参数
        original_params = step_def.action_params.copy()
        params = self._resolve_params(step_def.action_params, context)
        
        # 记录详细的输入日志
        logger.info(f"[WorkflowEngine] 步骤 [{step_def.id}] 输入信息:")
        logger.info(f"  ├── 动作名称: {step_def.action}")
        logger.info(f"  ├── 原始参数: {json.dumps(original_params, ensure_ascii=False)}")
        logger.info(f"  └── 解析后参数: {json.dumps(params, ensure_ascii=False)}")
        
        # 创建节点实例并执行
        node = node_class()
        result = await node.execute(context, **params)
        
        # 记录详细的输出日志
        logger.info(f"[WorkflowEngine] 步骤 [{step_def.id}] 输出信息:")
        logger.info(f"  ├── 处理逻辑: {result.get('processing', '')}")
        logger.info(f"  └── 执行结果: {json.dumps(result, ensure_ascii=False, default=str)[:200]}")
        
        return result
    
    async def _execute_conditional(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行条件分支步骤"""
        if not step_def.condition:
            raise ValueError(f"条件步骤 {step_def.id} 未指定条件")
        
        # 构建输入信息
        condition_expr = step_def.condition
        input_context = {
            "condition_expression": condition_expr,
            "available_variables": {k: v for k, v in {**context.inputs, **context.outputs}.items() if k in condition_expr}
        }
        
        # 记录输入日志
        logger.info(f"[WorkflowEngine] 条件分支 [{step_def.id}] 输入信息:")
        logger.info(f"  ├── 条件表达式: {condition_expr}")
        logger.info(f"  └── 相关变量: {json.dumps(input_context['available_variables'], ensure_ascii=False)}")
        
        # 执行条件判断
        result = self._eval_expression(condition_expr, context)
        
        # 构建详细的条件分支日志信息
        branch_info = []
        
        # 解析 next_steps 获取分支信息
        if step_def.next_steps:
            for branch_value, next_step_id in step_def.next_steps.items():
                branch_label = ""
                if branch_value == "true":
                    branch_label = "分支 1: 如果"
                elif branch_value == "false":
                    branch_label = "分支 2: 否则"
                else:
                    branch_label = f"分支: {branch_value}"
                
                branch_info.append({
                    "branch_value": branch_value,
                    "branch_label": branch_label,
                    "next_step": next_step_id,
                    "matched": str(result) == branch_value
                })
        
        # 构建处理逻辑描述
        processing = f"评估条件表达式 '{condition_expr}'，变量上下文包含 {list(input_context['available_variables'].keys())}"
        
        # 记录处理日志
        logger.info(f"[WorkflowEngine] 条件分支 [{step_def.id}] 处理逻辑:")
        logger.info(f"  └── {processing}")
        
        # 记录详细输出日志
        logger.info(f"[WorkflowEngine] 条件分支 [{step_def.id}] 输出信息:")
        logger.info(f"  ├── 执行结果: {result}")
        logger.info(f"  └── 分支路由:")
        for branch in branch_info:
            match_mark = "✓" if branch["matched"] else "✗"
            logger.info(f"      {match_mark} {branch['branch_label']} -> {branch['next_step']}")
        
        return {
            "condition_result": result,
            "condition_expression": condition_expr,
            "branches": branch_info,
            "matched_branch": str(result) if result is not None else None,
            "input": input_context,
            "processing": processing,
            "output": {"result": result, "matched_branch": str(result) if result is not None else None}
        }
    
    async def _execute_loop(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行循环步骤"""
        # 构建输入信息
        input_context = {
            "loop_count": step_def.loop_count,
            "loop_condition": step_def.loop_condition,
            "loop_step": step_def.next_step,
            "initial_context": {k: v for k, v in context.outputs.items()}
        }
        
        # 记录输入日志
        logger.info(f"[WorkflowEngine] 循环步骤 [{step_def.id}] 输入信息:")
        logger.info(f"  ├── 循环次数: {step_def.loop_count}")
        logger.info(f"  ├── 循环条件: {step_def.loop_condition or '无'}")
        logger.info(f"  └── 循环体步骤: {step_def.next_step}")
        
        results = []
        count = step_def.loop_count
        
        # 构建处理逻辑描述
        processing = f"执行循环 {count} 次，循环体为步骤 '{step_def.next_step}'"
        if step_def.loop_condition:
            processing += f"，循环条件为 '{step_def.loop_condition}'"
        
        # 记录处理日志
        logger.info(f"[WorkflowEngine] 循环步骤 [{step_def.id}] 处理逻辑:")
        logger.info(f"  └── {processing}")
        
        for i in range(count):
            context.outputs["loop_index"] = i
            context.outputs["loop_count"] = count
            
            logger.info(f"[WorkflowEngine] 循环步骤 [{step_def.id}] 第 {i+1}/{count} 次迭代")
            
            if step_def.loop_condition:
                condition_result = self._eval_expression(step_def.loop_condition, context)
                logger.info(f"[WorkflowEngine] 循环步骤 [{step_def.id}] 迭代 {i+1} 条件判断: {step_def.loop_condition} = {condition_result}")
                if not condition_result:
                    logger.info(f"[WorkflowEngine] 循环步骤 [{step_def.id}] 条件不满足，提前退出循环")
                    break
            
            if step_def.next_step and step_def.next_step in context.definition.steps:
                sub_step = context.definition.steps[step_def.next_step]
                result = await self._execute_step(sub_step, context)
                results.append(result)
                logger.info(f"[WorkflowEngine] 循环步骤 [{step_def.id}] 迭代 {i+1} 完成，结果: {str(result)[:100]}")
        
        # 记录输出日志
        logger.info(f"[WorkflowEngine] 循环步骤 [{step_def.id}] 输出信息:")
        logger.info(f"  ├── 实际迭代次数: {len(results)}")
        logger.info(f"  └── 迭代结果数量: {len(results)}")
        
        return {
            "loop_results": results,
            "actual_iterations": len(results),
            "input": input_context,
            "processing": processing,
            "output": {"results": results, "actual_iterations": len(results)}
        }
    
    async def _execute_parallel(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行并行步骤"""
        # 构建输入信息
        input_context = {
            "parallel_steps": step_def.parallel_steps,
            "step_count": len(step_def.parallel_steps),
            "initial_context": {k: v for k, v in context.outputs.items()}
        }
        
        # 记录输入日志
        logger.info(f"[WorkflowEngine] 并行步骤 [{step_def.id}] 输入信息:")
        logger.info(f"  ├── 并行步骤列表: {step_def.parallel_steps}")
        logger.info(f"  └── 步骤数量: {len(step_def.parallel_steps)}")
        
        # 构建处理逻辑描述
        processing = f"并行执行 {len(step_def.parallel_steps)} 个步骤: {', '.join(step_def.parallel_steps)}"
        
        # 记录处理日志
        logger.info(f"[WorkflowEngine] 并行步骤 [{step_def.id}] 处理逻辑:")
        logger.info(f"  └── {processing}")
        
        tasks = []
        for step_id in step_def.parallel_steps:
            if step_id in context.definition.steps:
                sub_step = context.definition.steps[step_id]
                tasks.append(self._execute_step(sub_step, context))
                logger.info(f"[WorkflowEngine] 并行步骤 [{step_def.id}] 启动子步骤: {step_id}")
        
        results = await asyncio.gather(*tasks)
        parallel_results = dict(zip(step_def.parallel_steps, results))
        
        # 记录输出日志
        logger.info(f"[WorkflowEngine] 并行步骤 [{step_def.id}] 输出信息:")
        logger.info(f"  ├── 完成步骤数量: {len(results)}")
        logger.info(f"  └── 结果摘要: {json.dumps({k: str(v)[:50] for k, v in parallel_results.items()}, ensure_ascii=False)}")
        
        return {
            "parallel_results": parallel_results,
            "completed_count": len(results),
            "input": input_context,
            "processing": processing,
            "output": parallel_results
        }
    
    async def _execute_subworkflow(self, step_def: StepDefinition, context: ExecutionContext) -> Any:
        """执行子工作流"""
        if not step_def.subworkflow:
            raise ValueError(f"子工作流步骤 {step_def.id} 未指定子工作流")
        
        # 构建输入信息
        input_context = {
            "subworkflow_id": step_def.subworkflow,
            "inputs": context.inputs,
            "current_context": {k: v for k, v in context.outputs.items()}
        }
        
        # 记录输入日志
        logger.info(f"[WorkflowEngine] 子工作流步骤 [{step_def.id}] 输入信息:")
        logger.info(f"  ├── 子工作流ID: {step_def.subworkflow}")
        logger.info(f"  └── 输入参数: {json.dumps(context.inputs, ensure_ascii=False)}")
        
        # 构建处理逻辑描述
        processing = f"调用子工作流 '{step_def.subworkflow}'，传入输入参数 {list(context.inputs.keys())}"
        
        # 记录处理日志
        logger.info(f"[WorkflowEngine] 子工作流步骤 [{step_def.id}] 处理逻辑:")
        logger.info(f"  └── {processing}")
        
        outputs = {}
        async for event in self.run(step_def.subworkflow, context.inputs):
            if event["type"] == "workflow_complete":
                outputs = event.get("outputs", {})
                break
        
        # 记录输出日志
        logger.info(f"[WorkflowEngine] 子工作流步骤 [{step_def.id}] 输出信息:")
        logger.info(f"  └── 子工作流输出: {json.dumps(outputs, ensure_ascii=False)[:200]}")
        
        return {
            "subworkflow_id": step_def.subworkflow,
            "subworkflow_outputs": outputs,
            "input": input_context,
            "processing": processing,
            "output": outputs
        }
    
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
            expr = expression.strip()
            if expr == 'true':
                return True
            elif expr == 'false':
                return False
            return bool(eval(expr, env))
        except Exception as e:
            logger.error(f"表达式计算失败 '{expression}': {e}")
            return False
    
    def _resolve_params(self, params: Dict[str, Any], context: ExecutionContext) -> Dict[str, Any]:
        """解析参数（支持变量引用）
        
        支持的变量引用方式：
        1. {{variable_name}} - 从 context.outputs 或 context.inputs 获取
        2. {{variable_name.field_name}} - 获取变量的字段值
        3. {{__node_output__}} - 获取前一个节点的输出
        4. {{__output__.field_name}} - 获取前一个节点输出的字段
        """
        resolved = {}
        
        # 获取前一个节点的输出
        previous_output = self._get_previous_node_output(context)
        
        for key, value in params.items():
            if isinstance(value, str) and value.startswith("{{") and value.endswith("}}"):
                var_expr = value[2:-2].strip()
                
                # 处理 __node_output__ 引用（前一个节点输出）
                if var_expr == "__node_output__":
                    resolved[key] = previous_output
                    logger.debug(f"[_resolve_params] 参数 {key} 解析为前一个节点输出: {type(previous_output).__name__}")
                # 处理 __output__.field_name 引用
                elif var_expr.startswith("__output__."):
                    # __output__. 是 11 个字符
                    field_name = var_expr[11:]
                    if isinstance(previous_output, dict) and field_name in previous_output:
                        resolved[key] = previous_output[field_name]
                        logger.debug(f"[_resolve_params] 参数 {key} 解析为前一个节点输出字段 '{field_name}': {resolved[key]}")
                    else:
                        resolved[key] = value
                        logger.warning(f"[_resolve_params] 前一个节点输出中不存在字段 '{field_name}'")
                else:
                    # 检查是否包含字段访问（如 {{variable.field}}）
                    if '.' in var_expr:
                        parts = var_expr.split('.', 1)
                        var_name = parts[0]
                        field_path = parts[1]
                        
                        # 先从 outputs 获取变量，然后是 inputs
                        var_value = context.outputs.get(var_name, context.inputs.get(var_name))
                        
                        if var_value is not None:
                            # 解析字段路径
                            resolved_value = self._resolve_field_path(var_value, field_path)
                            if resolved_value is not None:
                                resolved[key] = resolved_value
                                logger.debug(f"[_resolve_params] 参数 {key} 解析为变量 '{var_name}' 的字段 '{field_path}': {resolved_value}")
                            else:
                                resolved[key] = value
                                logger.warning(f"[_resolve_params] 变量 '{var_name}' 中不存在字段路径 '{field_path}'")
                        else:
                            # 尝试从前一个节点的输出中查找
                            if field_path in previous_output:
                                resolved[key] = previous_output[field_path]
                                logger.debug(f"[_resolve_params] 参数 {key} 从节点输出获取字段 '{field_path}': {resolved[key]}")
                            else:
                                resolved[key] = value
                                logger.warning(f"[_resolve_params] 未找到变量 '{var_name}'")
                    else:
                        # 从 outputs 获取，然后是 inputs
                        resolved_value = context.outputs.get(var_expr, context.inputs.get(var_expr))
                        if resolved_value is not None:
                            resolved[key] = resolved_value
                            logger.debug(f"[_resolve_params] 参数 {key} 解析为变量 '{var_expr}': {resolved_value}")
                        else:
                            # 尝试从前一个节点的输出中查找
                            if var_expr in previous_output:
                                resolved[key] = previous_output[var_expr]
                                logger.debug(f"[_resolve_params] 参数 {key} 从节点输出获取字段 '{var_expr}': {resolved[key]}")
                            else:
                                resolved[key] = value
                                logger.warning(f"[_resolve_params] 未找到变量 '{var_expr}'")
            else:
                resolved[key] = value
        
        return resolved
    
    def _resolve_field_path(self, obj: Any, field_path: str) -> Any:
        """解析字段路径（支持嵌套访问）
        
        例如：field_path = "user.name" 会返回 obj["user"]["name"] 或 obj.user.name
        
        Args:
            obj: 要访问的对象（dict 或 object）
            field_path: 字段路径，如 "field" 或 "nested.field"
        
        Returns:
            字段值，如果路径不存在返回 None
        """
        try:
            parts = field_path.split('.')
            value = obj
            
            for part in parts:
                if isinstance(value, dict):
                    if part in value:
                        value = value[part]
                    else:
                        return None
                elif hasattr(value, part):
                    value = getattr(value, part)
                else:
                    return None
            
            return value
        except Exception as e:
            logger.debug(f"[_resolve_field_path] 解析字段路径失败: {e}")
            return None
    
    def _get_previous_node_output(self, context: ExecutionContext) -> Any:
        """获取前一个节点的输出
        
        从 context.step_results 中获取最近执行完成的节点输出
        """
        if not context.step_results:
            return {}
        
        # 获取最近的步骤结果
        steps = list(context.step_results.keys())
        if steps:
            last_step_id = steps[-1]
            result = context.step_results.get(last_step_id, {})
            return result
        
        return {}
