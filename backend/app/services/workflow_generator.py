"""
工作流生成服务 - 根据用户需求自动生成工作流
"""

import json
import logging
import re
from typing import Dict, Any, Optional

from app.services.llm_service import llm_service
from app.core.config_loader import config_loader

logger = logging.getLogger("workflow_generator")


class WorkflowGenerator:
    """工作流生成服务"""
    
    def __init__(self):
        self.prompt_template = config_loader.get_prompt('workflow_generation')
    
    def generate_workflow(self, user_requirement: str) -> Dict[str, Any]:
        """
        根据用户需求生成工作流
        
        Args:
            user_requirement: 用户的业务需求描述
            
        Returns:
            dict: 包含工作流定义的字典
        """
        if not self.prompt_template:
            logger.error("工作流生成提示词模板未找到")
            return {"success": False, "message": "工作流生成提示词模板未找到"}
        
        # 构建提示词（使用字符串替换避免与JSON中的花括号冲突）
        prompt = self.prompt_template.replace('{{user_requirement}}', user_requirement)
        
        # 调用LLM生成工作流
        response = llm_service._call_llm_sync(prompt)
        
        if not response:
            logger.error("LLM调用失败或返回为空")
            return {"success": False, "message": "LLM调用失败"}
        
        # 解析响应
        try:
            workflow_data = self._parse_response(response)
            return {"success": True, "data": workflow_data}
        except Exception as e:
            logger.error(f"解析工作流响应失败: {e}")
            return {"success": False, "message": f"解析工作流响应失败: {str(e)}"}
    
    def _parse_response(self, response: str) -> Dict[str, Any]:
        """解析LLM响应"""
        # 清理Markdown代码块标记
        cleaned = re.sub(r'^```json\s*', '', response.strip())
        cleaned = re.sub(r'^```\s*', '', cleaned)
        cleaned = re.sub(r'\s*```$', '', cleaned)
        
        # 解析JSON
        result = json.loads(cleaned)
        
        # 验证基本结构
        if 'nodes' not in result or 'connections' not in result:
            raise ValueError("工作流格式不正确，缺少nodes或connections字段")
        
        # 确保有开始和结束节点
        node_types = [node.get('type') for node in result.get('nodes', [])]
        if 'start' not in node_types:
            raise ValueError("工作流缺少开始节点")
        if 'end' not in node_types:
            raise ValueError("工作流缺少结束节点")
        
        return result
    
    def validate_workflow(self, workflow_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        验证生成的工作流
        
        Args:
            workflow_data: 工作流数据
            
        Returns:
            dict: 验证结果
        """
        errors = []
        warnings = []
        
        nodes = workflow_data.get('nodes', [])
        connections = workflow_data.get('connections', [])
        
        # 检查节点数量
        if len(nodes) == 0:
            errors.append("工作流为空")
        
        # 检查开始节点
        start_nodes = [n for n in nodes if n.get('type') == 'start']
        if len(start_nodes) == 0:
            errors.append("缺少开始节点")
        elif len(start_nodes) > 1:
            errors.append(f"存在{len(start_nodes)}个开始节点，应该只有一个")
        
        # 检查结束节点
        end_nodes = [n for n in nodes if n.get('type') == 'end']
        if len(end_nodes) == 0:
            errors.append("缺少结束节点")
        
        # 检查连接
        node_ids = {n.get('id') for n in nodes}
        for conn in connections:
            if conn.get('from') not in node_ids:
                errors.append(f"连接源节点不存在: {conn.get('from')}")
            if conn.get('to') not in node_ids:
                errors.append(f"连接目标节点不存在: {conn.get('to')}")
        
        return {
            "valid": len(errors) == 0,
            "errors": errors,
            "warnings": warnings
        }
    
    def format_for_editor(self, workflow_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        将生成的工作流格式化为编辑器可用的格式
        
        Args:
            workflow_data: 工作流数据
            
        Returns:
            dict: 包含nodes和edges的格式
        """
        nodes = []
        edges = []
        
        for node in workflow_data.get('nodes', []):
            nodes.append({
                "id": node.get('id'),
                "type": node.get('type'),
                "position": {
                    "x": node.get('x', 0),
                    "y": node.get('y', 0)
                },
                "data": node.get('data', {})
            })
        
        for conn in workflow_data.get('connections', []):
            edge = {
                "id": f"edge-{conn.get('from')}-{conn.get('to')}",
                "source": conn.get('from'),
                "target": conn.get('to'),
                "markerEnd": {
                    "type": "arrowclosed",
                    "color": "#94a3b8"
                }
            }
            
            if conn.get('sourceHandle'):
                edge['sourceHandle'] = conn.get('sourceHandle')
            if conn.get('targetHandle'):
                edge['targetHandle'] = conn.get('targetHandle')
            
            edges.append(edge)
        
        return {
            "nodes": nodes,
            "edges": edges,
            "version": "2.0",
            "description": workflow_data.get('description', '')
        }


# 全局实例
_workflow_generator = None


def get_workflow_generator() -> WorkflowGenerator:
    """获取工作流生成器单例"""
    global _workflow_generator
    if _workflow_generator is None:
        _workflow_generator = WorkflowGenerator()
    return _workflow_generator
