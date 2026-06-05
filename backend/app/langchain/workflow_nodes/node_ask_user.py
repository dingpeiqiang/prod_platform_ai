"""
询问用户节点
"""
import json
from typing import Dict, Any, List, Optional
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema
from app.core.logger import get_logger

logger = get_logger(__name__)


@register_node
class AskUserNode(WorkflowNode):
    """询问用户节点

    通过 action=ask_user 信号通知引擎暂停工作流等待用户输入。
    支持：
    - 多种输入类型（文本、选择、确认）
    - 大模型解析用户输入
    - 输入提示词配置
    - 用户输入校验
    - 自定义输出参数
    """

    name = "workflow.ask_user"
    display_name = "询问用户"
    description = "向用户提问并等待输入，支持多种输入类型和校验"
    
    config_fields = {
        "message": ParamSchema(type="str", required=True, description="提示消息"),
        "prompt": ParamSchema(type="str", required=False, description="提示消息（兼容旧格式，推荐使用 message）"),
        
        "input_type": ParamSchema(
            type="str", 
            required=False, 
            description="输入类型：text（文本）、select（选择）、confirm（确认）", 
            default="text"
        ),
        "options": ParamSchema(
            type="list", 
            required=False, 
            description="选择类型的选项列表（JSON数组或换行分隔字符串）"
        ),
        
        "parse_with_llm": ParamSchema(
            type="bool", 
            required=False, 
            description="是否使用大模型解析用户输入", 
            default=False
        ),
        "parse_prompt": ParamSchema(
            type="str", 
            required=False, 
            description="大模型解析提示词，用于将用户输入转换为结构化数据"
        ),
        "parse_schema": ParamSchema(
            type="dict", 
            required=False, 
            description="大模型解析输出的JSON Schema，定义输出数据结构"
        ),
        
        "validation_enabled": ParamSchema(
            type="bool", 
            required=False, 
            description="是否启用输入校验", 
            default=False
        ),
        "validation_rules": ParamSchema(
            type="list", 
            required=False, 
            description="校验规则列表"
        ),
        "validation_error_message": ParamSchema(
            type="str", 
            required=False, 
            description="校验失败时的提示消息", 
            default="您的输入不符合要求，请重新输入"
        ),
        
        "output_var": ParamSchema(
            type="str", 
            required=False, 
            description="输出变量名称", 
            default="user_input"
        ),
        
        "required_fields": ParamSchema(
            type="list", 
            required=False, 
            description="必填字段列表", 
            default=[]
        ),
    }
    
    output_fields = {
        "action": ParamSchema(type="str", description="动作类型（ask_user/input_received）"),
        "message": ParamSchema(type="any", description="提示消息或用户输入内容"),
        "required_fields": ParamSchema(type="list", description="必填字段列表"),
        "waiting_for_input": ParamSchema(type="bool", description="是否等待用户输入"),
        "output": ParamSchema(type="any", description="用户输入内容"),
        "user_input": ParamSchema(type="any", description="用户输入内容"),
        "parsed_data": ParamSchema(type="any", description="大模型解析后的结构化数据"),
        "valid": ParamSchema(type="bool", description="校验是否通过"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        # 处理第一次执行的情况（等待用户输入）
        if not execution.is_resume:
            await self._handle_initial_execution(execution)
        else:
            # 处理用户输入后恢复执行的情况
            await self._handle_resume_execution(execution)
    
    async def _handle_initial_execution(self, execution: DelegateExecution) -> None:
        """处理节点第一次执行：准备等待用户输入"""
        message = execution.get("message") or execution.get("prompt") or "请提供信息"
        required_fields = execution.get("required_fields", [])
        input_type = execution.get("input_type", "text")
        options = execution.get("options", [])
        
        self._log_input(
            message=message, 
            input_type=input_type,
            options=options,
            required_fields=required_fields
        )
        
        processing = f"构建用户询问消息，要求用户提供 {len(required_fields)} 个必填字段"
        self._log_processing(processing)
        
        execution.set("action", "ask_user")
        execution.set("message", message)
        execution.set("required_fields", required_fields)
        execution.set("input_type", input_type)
        execution.set("options", options)
        execution.set("waiting_for_input", True)
        
        self._log_output(
            action="ask_user", 
            message=message, 
            required_fields=required_fields,
            input_type=input_type
        )
    
    async def _handle_resume_execution(self, execution: DelegateExecution) -> None:
        """处理节点恢复执行：处理用户输入、校验、解析"""
        # 获取用户输入
        user_input = execution.get("user_input") or execution.get("input") or execution.get("text")
        
        # 如果没有直接获取到，尝试从 step_results 中获取
        if not user_input:
            context = execution._context
            waiting_step_id = execution.step_id
            if waiting_step_id and waiting_step_id in context.step_results:
                step_result = context.step_results[waiting_step_id]
                user_input = step_result.get("output", {}).get("user_input")
        
        logger.info(f"[AskUserNode] 收到用户输入: {user_input}")
        
        # 1. 输入校验（如果启用）
        validation_enabled = execution.get("validation_enabled", False)
        valid = True
        validation_error = None
        
        if validation_enabled:
            valid, validation_error = await self._validate_input(user_input, execution)
            if not valid:
                # 校验失败，需要继续询问用户
                await self._handle_validation_failure(user_input, validation_error, execution)
                return
        
        # 2. 大模型解析（如果启用）
        parse_with_llm = execution.get("parse_with_llm", False)
        parsed_data = user_input
        
        if parse_with_llm and valid:
            parsed_data = await self._parse_with_llm(user_input, execution)
        
        # 3. 设置输出变量
        output_var = execution.get("output_var", "user_input")
        
        execution.set("action", "input_received")
        execution.set("message", user_input)
        execution.set("user_input", user_input)
        execution.set("output", user_input)
        execution.set("parsed_data", parsed_data)
        execution.set("valid", valid)
        execution.set(output_var, parsed_data)  # 设置到自定义输出变量
        
        self._log_output(
            action="input_received", 
            message=user_input, 
            user_input=user_input,
            parsed_data=parsed_data,
            valid=valid
        )
    
    async def _validate_input(self, user_input: Any, execution: DelegateExecution) -> tuple[bool, Optional[str]]:
        """校验用户输入，支持多种规则类型"""
        try:
            validation_rules = execution.get("validation_rules", [])
            
            if not validation_rules:
                return True, None
            
            # 输入值为空时的处理
            if not user_input or str(user_input).strip() == "":
                # 检查是否有 required 规则
                has_required = any(
                    r.get("type") == "required" 
                    for r in validation_rules 
                    if isinstance(r, dict)
                )
                if has_required:
                    return False, "输入不能为空"
                return True, None
            
            # 导入校验引擎
            from app.services.validation_service import ValidationEngine
            
            # 转换规则格式：将前端规则的 type/value/message 映射到引擎的 rule_type/rule_value/message
            engine_rules = []
            for rule in validation_rules:
                if not isinstance(rule, dict):
                    continue
                rule_type = rule.get("type", "")
                rule_value = rule.get("value")
                message = rule.get("message", f"校验失败: {rule_type}")
                
                # 跳过 required 类型（已在上方处理）
                if rule_type == "required":
                    continue
                
                engine_rules.append({
                    "rule_type": rule_type,
                    "rule_value": rule_value,
                    "message": message
                })
            
            if not engine_rules:
                return True, None
            
            # 执行校验
            result = ValidationEngine.validate_field(str(user_input), engine_rules)
            
            if not result.valid and result.errors:
                error_msg = result.errors[0]
                return False, error_msg
            
            return True, None
        except Exception as e:
            logger.error(f"[AskUserNode] 输入校验出错: {e}")
            return False, f"校验过程出错: {str(e)}"
    
    async def _handle_validation_failure(self, user_input: Any, error_msg: str, execution: DelegateExecution) -> None:
        """处理校验失败：继续询问用户"""
        validation_error_message = execution.get("validation_error_message", "您的输入不符合要求，请重新输入")
        
        # 重新设置 ask_user 动作，但这次包含错误提示
        original_message = execution.get("message") or "请提供信息"
        new_message = f"{validation_error_message}\n\n{original_message}"
        
        execution.set("action", "ask_user")
        execution.set("message", new_message)
        execution.set("waiting_for_input", True)
        execution.set("validation_error", error_msg)
        
        logger.info(f"[AskUserNode] 校验失败，继续询问用户: {error_msg}")
        self._log_output(
            action="ask_user", 
            message=new_message, 
            validation_error=error_msg
        )
        
        # 标记为需要继续询问，这样 workflow_engine 知道不要继续执行，而是再次等待
        execution._need_ask_again = True
    
    async def _parse_with_llm(self, user_input: Any, execution: DelegateExecution) -> Any:
        """使用大模型解析用户输入"""
        try:
            parse_prompt = execution.get("parse_prompt")
            parse_schema = execution.get("parse_schema")
            
            if not parse_prompt and not parse_schema:
                # 没有解析配置，直接返回原始输入
                return user_input
            
            # 构建解析提示词
            final_prompt = self._build_parse_prompt(user_input, parse_prompt, parse_schema)
            
            # 这里我们会调用 LLM 来解析用户输入
            # 为了演示，我们先做一个简单的实现
            # 实际项目中会调用 LLM service
            parsed_data = self._simulate_llm_parse(user_input, parse_schema)
            
            logger.info(f"[AskUserNode] LLM解析完成: {parsed_data}")
            return parsed_data
        except Exception as e:
            logger.error(f"[AskUserNode] LLM解析出错: {e}")
            # 解析失败时返回原始输入
            return user_input
    
    def _build_parse_prompt(self, user_input: Any, parse_prompt: Optional[str], parse_schema: Optional[Dict]) -> str:
        """构建LLM解析提示词"""
        prompt_parts = []
        
        if parse_prompt:
            prompt_parts.append(parse_prompt)
        else:
            prompt_parts.append("请将以下用户输入转换为结构化数据。")
        
        prompt_parts.append(f"\n用户输入: {user_input}")
        
        if parse_schema:
            prompt_parts.append(f"\n请按以下JSON Schema格式输出: {json.dumps(parse_schema, ensure_ascii=False)}")
        
        return "\n".join(prompt_parts)
    
    def _simulate_llm_parse(self, user_input: Any, parse_schema: Optional[Dict]) -> Any:
        """模拟LLM解析（实际项目中替换为真实LLM调用）"""
        # 简单实现：如果是字符串，直接返回
        # 可以根据 parse_schema 做更复杂的处理
        if parse_schema:
            try:
                # 尝试解析为JSON
                if isinstance(user_input, str):
                    try:
                        return json.loads(user_input)
                    except:
                        pass
            except:
                pass
        
        return user_input
