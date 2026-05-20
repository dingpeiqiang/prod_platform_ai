"""
工作流配置格式转换器

将前端编辑器生成的配置格式转换为后端 WorkflowEngine 可执行的格式
支持从本体配置中获取业务规则
"""
from typing import Dict, Any, List, Optional
import logging

from app.services.ontology_service import OntologyService
from sqlalchemy.orm import Session

logger = logging.getLogger("workflow_converter")


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
            'action_params': cls._extract_params(node_data),
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
            step['condition'] = node_data.get('condition') or 'true'
            # 获取条件分支的下一个节点
            true_edges = [e for e in edges if e['source'] == node_id and e.get('sourceHandle') == 'true']
            false_edges = [e for e in edges if e['source'] == node_id and e.get('sourceHandle') == 'false']
            
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
            normal_edges = [
                e for e in edges 
                if e['source'] == node_id 
                and e.get('sourceHandle') in (None, '', 'default')
            ]
            
            if normal_edges:
                step['next_step'] = normal_edges[0]['target']
        
        # 添加重试配置
        if node_type in ['tool', 'http']:
            step['retry_count'] = int(node_data.get('retryCount') or 0)
            step['retry_delay'] = int(node_data.get('retryDelay') or 1)
        
        return step
    
    @classmethod
    def _extract_params(cls, node_data: Dict[str, Any]) -> Dict[str, Any]:
        """提取节点参数"""
        params = {}
        
        # 通用参数
        if 'prompt' in node_data:
            params['prompt'] = node_data['prompt']
        
        if 'model' in node_data:
            params['model'] = node_data['model']
        
        if 'temperature' in node_data:
            params['temperature'] = float(node_data['temperature'])
        
        if 'url' in node_data:
            params['url'] = node_data['url']
        
        if 'method' in node_data:
            params['method'] = node_data['method']
        
        if 'code' in node_data:
            params['code'] = node_data['code']
        
        if 'condition' in node_data:
            params['condition'] = node_data['condition']
        
        if 'loopCount' in node_data:
            params['loop_count'] = int(node_data['loopCount'])
        
        if 'variableName' in node_data:
            params['variable_name'] = node_data['variableName']
        
        if 'variableValue' in node_data:
            params['variable_value'] = node_data['variableValue']
        
        if 'outputVar' in node_data:
            params['output_var'] = node_data['outputVar']
        
        if 'toolType' in node_data:
            params['tool_type'] = node_data['toolType']
        
        if 'toolName' in node_data:
            params['tool_name'] = node_data['toolName']
        
        if 'knowledgeBase' in node_data:
            params['knowledge_base'] = node_data['knowledgeBase']
        
        if 'queryMode' in node_data:
            params['query_mode'] = node_data['queryMode']
        
        if 'queryText' in node_data:
            params['query_text'] = node_data['queryText']
        
        if 'required' in node_data:
            params['required'] = node_data['required']
        
        if 'inputType' in node_data:
            params['input_type'] = node_data['inputType']
        
        if 'options' in node_data:
            params['options'] = node_data['options']
        
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
