"""
工作流配置格式转换器

将前端编辑器生成的配置格式转换为后端 WorkflowEngine 可执行的格式
支持从本体配置中获取业务规则
"""
from typing import Dict, Any, List, Optional
from app.core.logger import get_logger

logger = get_logger(__name__)

from app.services.ontology_service import OntologyService
from sqlalchemy.orm import Session



class WorkflowConverter:
    """
    前端工作流配置 → 后端工作流配置 转换器
    
    前端格式 (Vue Flow):
    {
      "nodes": [...],
      "edges": [...]
    }
    
    后端格式 (WorkflowEngine):
    {
      "id": "...",
      "name": "...",
      "steps": {
        "step_id": {
          "type": "action|conditional|loop|parallel",
          "action": "...",
          "condition": "...",
          "next_step": "...",
          "next_steps": {...}
        }
      },
      "business_rules": {...}  # 从本体加载的业务规则
    }
    """
    
    # 节点类型映射
    NODE_TYPE_MAP = {
        'start': 'start',
        'end': 'end',
        'prompt': 'action',
        'llm': 'action',
        'userInput': 'action',
        'tool': 'action',
        'condition': 'conditional',
        'loop': 'loop',
        'variable': 'action',
        'http': 'action',
        'code': 'action',
        'parser': 'action',
        'knowledgeBase': 'action',
        'form': 'action'
    }
    
    # 节点动作映射
    NODE_ACTION_MAP = {
        'start': 'workflow.start',
        'end': 'workflow.end',
        'prompt': 'workflow.set_prompt',
        'llm': 'workflow.call_llm',
        'userInput': 'workflow.ask_user',
        'tool': 'workflow.call_tool',
        'variable': 'workflow.set_variable',
        'http': 'workflow.http_request',
        'code': 'workflow.execute_code',
        'parser': 'workflow.parse_output',
        'knowledgeBase': 'workflow.query_knowledge',
        'form': 'workflow.generate_form'
    }
    
    _ontology_rules_cache = {}  # 本体规则缓存
    
    @classmethod
    def _load_ontology_rules(cls, ontology_code: str, db: Optional[Session] = None) -> Dict[str, Any]:
        """从本体加载业务规则（带缓存）"""
        if ontology_code in cls._ontology_rules_cache:
            return cls._ontology_rules_cache[ontology_code]
        
        # 尝试从数据库获取本体规则
        if db:
            result = OntologyService.get_business_rules(db, ontology_code)
            if result["success"]:
                cls._ontology_rules_cache[ontology_code] = result["data"]
                return result["data"]
        
        # 返回空规则
        return {
            "default_values": {},
            "validation_rules": {},
            "field_mappings": {},
            "business_rules": []
        }
    
    @classmethod
    def clear_cache(cls):
        """清除规则缓存"""
        cls._ontology_rules_cache.clear()
    
    @classmethod
    def convert(cls, frontend_data: Dict[str, Any], workflow_id: str, workflow_name: str, 
                db: Optional[Session] = None) -> Dict[str, Any]:
        """将前端格式转换为后端格式
        
        Args:
            frontend_data: 前端编辑器生成的工作流配置
            workflow_id: 工作流编码
            workflow_name: 工作流名称
            db: 数据库会话（用于获取节点级本体规则）
        """
        nodes = frontend_data.get('nodes', [])
        edges = frontend_data.get('edges', [])
        
        # 构建节点映射
        node_map = {node['id']: node for node in nodes}
        
        # 构建步骤配置
        steps = {}
        start_step = None
        
        for node in nodes:
            node_id = node['id']
            node_type = node.get('type', 'generic')
            node_data = node.get('data', {})
            
            # 构建步骤配置（传入db用于节点级本体加载）
            step = cls._build_step(node, edges, node_map, db)
            
            # 记录开始节点
            if node_type == 'start':
                start_step = node_id
            
            steps[node_id] = step
        
        # 构建后端配置
        backend_config = {
            'id': workflow_id,
            'name': workflow_name,
            'version': '1.0',
            'start_step': start_step or 'start',
            'steps': steps
        }
        
        logger.info(f"[WorkflowConverter] 转换完成: {len(nodes)} 个节点, {len(edges)} 条边")
        return backend_config
    
    @classmethod
    def _build_step(cls, node: Dict[str, Any], edges: List[Dict[str, Any]], 
                    node_map: Dict[str, Dict], db: Optional[Session] = None) -> Dict[str, Any]:
        """构建单个步骤配置
        
        Args:
            node: 节点数据
            edges: 连接线数据
            node_map: 节点映射
            db: 数据库会话（用于加载节点级本体规则）
        """
        node_id = node['id']
        node_type = node.get('type', 'generic')
        node_data = node.get('data', {})
        
        step = {
            'id': node_id,
            'name': node_data.get('label') or node_type,
            'type': cls.NODE_TYPE_MAP.get(node_type, 'action'),
            'action': cls.NODE_ACTION_MAP.get(node_type),
            'action_params': cls._extract_params(node_data, node_type),
            'next_step': None,
            'next_steps': {}
        }
        
        # 处理表单生成节点，本体作为节点属性
        if node_type == 'form':
            # 从节点属性获取本体编码
            ontology_code = node_data.get('ontologyCode')
            if ontology_code and db:
                # 加载该节点对应的本体规则
                result = OntologyService.get_business_rules(db, ontology_code)
                if result["success"]:
                    rules = result["data"]
                    # 合并本体默认值
                    default_values = rules.get('default_values', {})
                    existing_defaults = step['action_params'].get('defaultValues', {})
                    merged_defaults = {**default_values, **existing_defaults}
                    step['action_params']['defaultValues'] = merged_defaults
                    
                    # 添加字段映射
                    step['action_params']['fieldMappings'] = rules.get('field_mappings', {})
                    
                    # 添加校验规则
                    step['action_params']['validationRules'] = rules.get('validation_rules', {})
        
        # 处理条件分支
        if node_type == 'condition':
            # 优先使用 branches 格式（前端 Vue Flow 风格）
            branches = node_data.get('branches', [])
            if branches:
                # 将 branches 格式转换为条件表达式字符串
                condition_parts = []
                has_else = False
                for branch in branches:
                    branch_type = branch.get('type', '')
                    conditions = branch.get('conditions', [])
                    if branch_type == 'else':
                        has_else = True
                    elif conditions:
                        cond_strings = []
                        for cond in conditions:
                            var_name = cond.get('variable', '')
                            operator = cond.get('operator', '==')
                            value_type = cond.get('valueType', 'input')
                            value = cond.get('value', '')
                            if var_name and operator:
                                if value_type == 'reference':
                                    cond_strings.append(f"{var_name} {operator} {value}")
                                else:
                                    cond_strings.append(f"{var_name} {operator} '{value}'")
                        if cond_strings:
                            condition_parts.append('(' + ' and '.join(cond_strings) + ')')
                
                if condition_parts:
                    step['condition'] = ' or '.join(condition_parts)
                elif has_else:
                    # 如果只有 else 分支，条件总是为 True
                    step['condition'] = 'true'
                else:
                    step['condition'] = 'true'
            else:
                step['condition'] = node_data.get('condition') or 'true'
            
            # 获取条件分支的下一个节点
            # 支持多种 sourceHandle 格式: 'true'/'false' 或 'branch_0'/'branch_else'
            true_edges = [e for e in edges if e['source'] == node_id and e.get('sourceHandle') in ('true', 'branch_0', '0')]
            false_edges = [e for e in edges if e['source'] == node_id and e.get('sourceHandle') in ('false', 'branch_else', 'else', '1')]
            
            if true_edges:
                step['next_steps']['True'] = true_edges[0]['target']
            if false_edges:
                step['next_steps']['False'] = false_edges[0]['target']
        
        # 处理循环
        elif node_type == 'loop':
            step['loop_count'] = int(node_data.get('loopCount') or 3)
            step['loop_condition'] = node_data.get('loopCondition')
            
            # 获取循环体和结束分支
            body_edges = [e for e in edges if e['source'] == node_id and e.get('sourceHandle') == 'body']
            end_edges = [e for e in edges if e['source'] == node_id and e.get('sourceHandle') == 'end']
            
            if body_edges:
                step['next_step'] = body_edges[0]['target']
            if end_edges:
                step['next_steps']['exit'] = end_edges[0]['target']
        
        # 处理普通节点
        else:
            # 获取下一个节点（不包含条件分支的边）
            # 支持多种 sourceHandle 格式: None, '', 'default', 'source'
            normal_edges = [
                e for e in edges 
                if e['source'] == node_id 
                and e.get('sourceHandle') in (None, '', 'default', 'source')
            ]
            
            if normal_edges:
                step['next_step'] = normal_edges[0]['target']
        
        # 添加重试配置
        if node_type in ['tool', 'http']:
            step['retry_count'] = int(node_data.get('retryCount') or 0)
            step['retry_delay'] = int(node_data.get('retryDelay') or 1)
        
        return step
    
    @classmethod
    def _extract_params(cls, node_data: Dict[str, Any], node_type: str) -> Dict[str, Any]:
        """提取节点参数（动态提取所有非内部字段）"""
        params = {}
        
        # 前端编辑器内部字段（不传递到节点）
        internal_fields = {
            'label', 'type', 'id', 'position',
            'sourcePosition', 'targetPosition',
            'measured', 'dragging', 'selected',
        }
        
        # 动态提取所有非内部字段
        for key, value in node_data.items():
            if key not in internal_fields and value is not None:
                params[key] = value
        

        
        # userInput 节点：字段映射
        if node_type == 'userInput':
            # 消息字段映射
            if 'prompt' in params and 'message' not in params:
                params['message'] = params['prompt']
            # 输出变量映射
            if 'outputVar' in params and 'output_var' not in params:
                params['output_var'] = params['outputVar']
            # 输入类型映射
            if 'inputType' in params and 'input_type' not in params:
                params['input_type'] = params['inputType']
            # 必填字段映射
            if 'required' in params and 'required_fields' not in params:
                params['required_fields'] = []
                if params['required']:
                    params['required_fields'] = ['user_input']
            # 校验配置映射
            if 'validationEnabled' in params and 'validation_enabled' not in params:
                params['validation_enabled'] = params['validationEnabled']
            if 'validationErrorMessage' in params and 'validation_error_message' not in params:
                params['validation_error_message'] = params['validationErrorMessage']
            if 'validationRules' in params and 'validation_rules' not in params:
                params['validation_rules'] = params['validationRules']
            # 大模型解析配置映射
            if 'parseWithLLM' in params and 'parse_with_llm' not in params:
                params['parse_with_llm'] = params['parseWithLLM']
            if 'parsePrompt' in params and 'parse_prompt' not in params:
                params['parse_prompt'] = params['parsePrompt']
            if 'parseSchema' in params and 'parse_schema' not in params:
                params['parse_schema'] = params['parseSchema']
            
            # 处理选项列表：将换行分隔的字符串转换为数组
            if 'options' in params and isinstance(params['options'], str):
                options_str = params['options']
                options_list = [opt.strip() for opt in options_str.split('\n') if opt.strip()]
                if options_list:
                    params['options'] = options_list
        
        return params
    
    @classmethod
    def validate(cls, frontend_data: Dict[str, Any]) -> Dict[str, Any]:
        """验证前端配置"""
        errors = []
        warnings = []
        
        nodes = frontend_data.get('nodes', [])
        edges = frontend_data.get('edges', [])
        
        # 检查节点数量
        if not nodes:
            errors.append({'type': 'empty', 'message': '工作流为空'})
        
        # 检查开始节点
        start_nodes = [n for n in nodes if n.get('type') == 'start']
        if not start_nodes:
            errors.append({'type': 'missing_start', 'message': '缺少开始节点'})
        elif len(start_nodes) > 1:
            errors.append({'type': 'multiple_start', 'message': '只能有一个开始节点'})
        
        # 检查结束节点
        end_nodes = [n for n in nodes if n.get('type') == 'end']
        if not end_nodes:
            errors.append({'type': 'missing_end', 'message': '缺少结束节点'})
        
        # 检查连接线
        if not edges:
            warnings.append({'type': 'no_edges', 'message': '没有连接线'})
        
        return {
            'valid': len(errors) == 0,
            'errors': errors,
            'warnings': warnings
        }
