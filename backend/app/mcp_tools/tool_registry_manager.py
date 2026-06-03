"""
MCP 工具注册管理器
负责从代码注解和数据库配置中注册工具到 ToolHub
"""
from app.core.logger import get_logger

logger = get_logger(__name__)
from typing import Dict, Any, List
from sqlalchemy.orm import Session
from app.mcp_tools import get_toolhub
from app.models.mcp_call_log import MCPToolDefinition
from app.mcp_tools.external_api_executor import create_external_tool_handler



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
        # 清除会话缓存，确保从数据库获取最新数据
        self.db.expire_all()
        
        # 查询所有启用的外部 API 工具
        # 新逻辑：工具类型为 url 或有 url 字段表示是外部 API 工具
        external_tools = self.db.query(MCPToolDefinition).filter(
            MCPToolDefinition.is_enabled == True,
            (MCPToolDefinition.tool_type == "url") | (MCPToolDefinition.url.isnot(None))
        ).all()
        
        registered_count = 0
        
        for tool_def in external_tools:
            try:
                # 如果工具已注册，先注销旧版本以应用新配置
                if self.toolhub.has_tool(tool_def.tool_name):
                    logger.info(f"Tool {tool_def.tool_name} already registered, unregistering old version")
                    self.toolhub.unregister(tool_def.tool_name)
                
                # 创建外部 API 处理函数
                handler = create_external_tool_handler(tool_def.to_dict())
                
                # 注册到 ToolHub（包含外部工具配置字段）
                self.toolhub.register(
                    name=tool_def.tool_name,
                    handler=handler,
                    description=tool_def.description or "",
                    category=tool_def.category or "external",
                    input_schema=tool_def.input_schema,
                    output_schema=tool_def.output_schema,
                    url=tool_def.url or "",
                    request_method=tool_def.request_method or "POST",
                    protocol=tool_def.protocol or "http",
                    auth_type=tool_def.auth_type or "none"
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
            self.toolhub.unregister(tool_name)
            logger.info(f"Tool {tool_name} disabled and unregistered")
        
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
            config: 新配置（包含 url, request_method 等字段）
            
        Returns:
            是否成功
        """
        tool = self.db.query(MCPToolDefinition).filter(
            MCPToolDefinition.tool_name == tool_name
        ).first()
        
        if not tool:
            return False
        
        # 更新明确字段
        if "url" in config:
            tool.url = config["url"]
        if "request_method" in config:
            tool.request_method = config["request_method"].upper()
        if "tool_type" in config:
            tool.tool_type = config["tool_type"]
        if "protocol" in config:
            tool.protocol = config["protocol"]
        if "auth_type" in config:
            tool.auth_type = config["auth_type"]
        if "auth_info" in config:
            tool.auth_info = config["auth_info"]
        if "need_summary" in config:
            tool.need_summary = config["need_summary"]
        if "prompt" in config:
            tool.prompt = config["prompt"]
        
        # 更新其他字段
        if "description" in config:
            tool.description = config["description"]
        if "category" in config:
            tool.category = config["category"]
        if "input_schema" in config:
            tool.input_schema = config["input_schema"]
        if "output_schema" in config:
            tool.output_schema = config["output_schema"]
        if "is_enabled" in config:
            tool.is_enabled = config["is_enabled"]
        if "is_public" in config:
            tool.is_public = config["is_public"]
        
        self.db.commit()
        
        # 重新注册以应用新配置
        self.sync_tools_from_database()
        
        return True
    
    def list_external_tools(self) -> List[Dict[str, Any]]:
        """
        列出所有外部 API 工具
        
        Returns:
            工具列表
        """
        tools = self.db.query(MCPToolDefinition).filter(
            (MCPToolDefinition.tool_type == "url") | (MCPToolDefinition.url.isnot(None))
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
                # 外部工具配置字段
                tool_type=tool_data.get("tool_type", "url"),
                protocol=tool_data.get("protocol", "http"),
                request_method=tool_data.get("request_method", "POST").upper(),
                url=tool_data.get("url"),
                auth_type=tool_data.get("auth_type", "none"),
                auth_info=tool_data.get("auth_info"),
                need_summary=tool_data.get("need_summary", False),
                prompt=tool_data.get("prompt"),
                # 保留兼容性字段
                config=tool_data.get("config"),
                extra_metadata=tool_data.get("extra_metadata")
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
            self.toolhub.unregister(tool_name)
        
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
