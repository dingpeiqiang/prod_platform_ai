"""
MCP 工具注册管理器
负责从代码注解和数据库配置中注册工具到 ToolHub
"""
import logging
from typing import Dict, Any, List
from sqlalchemy.orm import Session
from app.mcp_tools import get_toolhub
from app.models.mcp_call_log import MCPToolDefinition
from app.mcp_tools.external_api_executor import create_external_tool_handler

logger = logging.getLogger("tool_registry_manager")


class ToolRegistryManager:
    """工具注册管理器"""
    
    def __init__(self, db_session: Session):
        self.db = db_session
        self.toolhub = get_toolhub()
    
    def sync_tools_from_database(self) -> int:
        """
        从数据库同步外部 API 工具到 ToolHub
        
        Returns:
            注册的工具数量
        """
        # 查询所有启用的外部 API 工具
        external_tools = self.db.query(MCPToolDefinition).filter(
            MCPToolDefinition.is_enabled == True,
            MCPToolDefinition.config.isnot(None)  # 有 config 字段表示是外部 API 工具
        ).all()
        
        registered_count = 0
        
        for tool_def in external_tools:
            try:
                # 检查是否已注册
                if self.toolhub.has_tool(tool_def.tool_name):
                    logger.info(f"Tool {tool_def.tool_name} already registered, skipping")
                    continue
                
                # 创建外部 API 处理函数
                handler = create_external_tool_handler(tool_def.to_dict())
                
                # 注册到 ToolHub
                self.toolhub.register(
                    name=tool_def.tool_name,
                    func=handler,
                    description=tool_def.description or "",
                    category=tool_def.category or "external",
                    input_schema=tool_def.input_schema,
                    metadata={
                        "type": "external_api",
                        "tool_code": tool_def.tool_code,
                        **{k: v for k, v in (tool_def.metadata or {}).items()}
                    }
                )
                
                registered_count += 1
                logger.info(f"Registered external tool: {tool_def.tool_name}")
                
            except Exception as e:
                logger.error(f"Failed to register tool {tool_def.tool_name}: {e}", exc_info=True)
        
        return registered_count
    
    def toggle_tool(self, tool_name: str, enabled: bool) -> bool:
        """
        启用/禁用工具
        
        Args:
            tool_name: 工具名称
            enabled: 是否启用
            
        Returns:
            是否成功
        """
        tool = self.db.query(MCPToolDefinition).filter(
            MCPToolDefinition.tool_name == tool_name
        ).first()
        
        if not tool:
            logger.warning(f"Tool {tool_name} not found in database")
            return False
        
        old_status = tool.is_enabled
        tool.is_enabled = enabled
        self.db.commit()
        
        # 如果禁用，从 ToolHub 移除
        if enabled == False and self.toolhub.has_tool(tool_name):
            # 注意：当前 ToolHub 可能没有 unregister 方法，需要添加
            logger.info(f"Tool {tool_name} disabled")
        
        # 如果启用，重新注册
        if enabled == True and not old_status:
            self.sync_tools_from_database()
            logger.info(f"Tool {tool_name} enabled and re-registered")
        
        return True
    
    def get_tool_definition(self, tool_name: str) -> Dict[str, Any]:
        """
        获取工具定义
        
        Args:
            tool_name: 工具名称
            
        Returns:
            工具定义字典，不存在返回 None
        """
        tool = self.db.query(MCPToolDefinition).filter(
            MCPToolDefinition.tool_name == tool_name
        ).first()
        
        if tool:
            return tool.to_dict()
        return None
    
    def update_tool_config(self, tool_name: str, config: Dict[str, Any]) -> bool:
        """
        更新工具配置
        
        Args:
            tool_name: 工具名称
            config: 新配置
            
        Returns:
            是否成功
        """
        tool = self.db.query(MCPToolDefinition).filter(
            MCPToolDefinition.tool_name == tool_name
        ).first()
        
        if not tool:
            return False
        
        tool.config = config
        self.db.commit()
        
        # 重新注册以应用新配置
        if self.toolhub.has_tool(tool_name):
            # 先移除旧的工具（需要实现 unregister）
            # self.toolhub.unregister(tool_name)
            pass
        
        # 重新注册
        self.sync_tools_from_database()
        
        return True
    
    def list_external_tools(self) -> List[Dict[str, Any]]:
        """
        列出所有外部 API 工具
        
        Returns:
            工具列表
        """
        tools = self.db.query(MCPToolDefinition).filter(
            MCPToolDefinition.config.isnot(None)
        ).all()
        
        return [tool.to_dict() for tool in tools]
    
    def create_external_tool(self, tool_data: Dict[str, Any]) -> bool:
        """
        创建新的外部 API 工具
        
        Args:
            tool_data: 工具数据字典
            
        Returns:
            是否成功
        """
        try:
            # 检查是否已存在
            existing = self.db.query(MCPToolDefinition).filter(
                MCPToolDefinition.tool_name == tool_data["tool_name"]
            ).first()
            
            if existing:
                logger.warning(f"Tool {tool_data['tool_name']} already exists")
                return False
            
            # 创建新记录
            new_tool = MCPToolDefinition(
                tool_name=tool_data["tool_name"],
                tool_code=tool_data.get("tool_code"),
                description=tool_data.get("description"),
                category=tool_data.get("category", "external"),
                is_enabled=tool_data.get("is_enabled", True),
                is_public=tool_data.get("is_public", True),
                input_schema=tool_data.get("input_schema"),
                output_schema=tool_data.get("output_schema"),
                config=tool_data.get("config"),
                metadata=tool_data.get("metadata")
            )
            
            self.db.add(new_tool)
            self.db.commit()
            
            # 如果启用，立即注册
            if new_tool.is_enabled:
                self.sync_tools_from_database()
            
            logger.info(f"Created external tool: {new_tool.tool_name}")
            return True
            
        except Exception as e:
            self.db.rollback()
            logger.error(f"Failed to create tool: {e}", exc_info=True)
            return False
    
    def delete_tool(self, tool_name: str) -> bool:
        """
        删除工具
        
        Args:
            tool_name: 工具名称
            
        Returns:
            是否成功
        """
        tool = self.db.query(MCPToolDefinition).filter(
            MCPToolDefinition.tool_name == tool_name
        ).first()
        
        if not tool:
            return False
        
        # 从 ToolHub 移除
        if self.toolhub.has_tool(tool_name):
            # self.toolhub.unregister(tool_name)
            pass
        
        # 从数据库删除
        self.db.delete(tool)
        self.db.commit()
        
        logger.info(f"Deleted tool: {tool_name}")
        return True


def init_external_tools(db_session: Session) -> int:
    """
    初始化外部工具（应用启动时调用）
    
    Args:
        db_session: 数据库会话
        
    Returns:
        注册的工具数量
    """
    manager = ToolRegistryManager(db_session)
    count = manager.sync_tools_from_database()
    logger.info(f"Initialized {count} external tools from database")
    return count
