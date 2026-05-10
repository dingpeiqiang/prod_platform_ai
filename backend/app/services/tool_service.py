import logging
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from sqlalchemy import desc
from app.models.tool import Tool

logger = logging.getLogger("tool_service")


class ToolService:
    
    @classmethod
    def get_categories(cls) -> List[Dict[str, str]]:
        return [
            {"code": "general", "name": "通用工具"},
            {"code": "api", "name": "API工具"},
            {"code": "database", "name": "数据库工具"},
            {"code": "file", "name": "文件工具"},
            {"code": "mcp", "name": "MCP工具"},
            {"code": "custom", "name": "自定义工具"}
        ]
    
    @classmethod
    def list_tools(cls, db: Session, category: Optional[str] = None, is_active: Optional[bool] = None) -> Dict[str, Any]:
        try:
            query = db.query(Tool)
            if category:
                query = query.filter(Tool.category == category)
            if is_active is not None:
                query = query.filter(Tool.is_active == is_active)
            
            tools = query.order_by(desc(Tool.created_at)).all()
            return {
                "success": True,
                "data": [t.to_dict() for t in tools]
            }
        except Exception as e:
            logger.exception(f"Failed to list tools: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def get_tool(cls, db: Session, tool_code: str) -> Dict[str, Any]:
        try:
            tool = db.query(Tool).filter(Tool.tool_code == tool_code).first()
            if not tool:
                return {"success": False, "message": f"工具 {tool_code} 不存在"}
            return {"success": True, "data": tool.to_dict()}
        except Exception as e:
            logger.exception(f"Failed to get tool: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def create_tool(cls, db: Session, tool_data: Dict[str, Any], user: Optional[str] = None) -> Dict[str, Any]:
        try:
            tool_code = tool_data.get("toolCode")
            if not tool_code:
                return {"success": False, "message": "工具编码不能为空"}
            
            existing = db.query(Tool).filter(Tool.tool_code == tool_code).first()
            if existing:
                return {"success": False, "message": f"工具 {tool_code} 已存在"}
            
            tool = Tool(
                tool_code=tool_code,
                tool_name=tool_data.get("toolName", tool_code),
                description=tool_data.get("description"),
                category=tool_data.get("category", "general"),
                tool_type=tool_data.get("toolType", "custom"),
                config=tool_data.get("config", {}),
                parameters=tool_data.get("parameters", []),
                return_schema=tool_data.get("returnSchema", {}),
                endpoint=tool_data.get("endpoint"),
                handler=tool_data.get("handler"),
                is_async=tool_data.get("isAsync", True)
            )
            db.add(tool)
            db.commit()
            db.refresh(tool)
            
            return {"success": True, "data": tool.to_dict(), "message": "创建成功"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to create tool: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def update_tool(cls, db: Session, tool_code: str, tool_data: Dict[str, Any], user: Optional[str] = None) -> Dict[str, Any]:
        try:
            tool = db.query(Tool).filter(Tool.tool_code == tool_code).first()
            if not tool:
                return {"success": False, "message": f"工具 {tool_code} 不存在"}
            
            if "toolName" in tool_data:
                tool.tool_name = tool_data["toolName"]
            if "description" in tool_data:
                tool.description = tool_data["description"]
            if "category" in tool_data:
                tool.category = tool_data["category"]
            if "toolType" in tool_data:
                tool.tool_type = tool_data["toolType"]
            if "config" in tool_data:
                tool.config = tool_data["config"]
            if "parameters" in tool_data:
                tool.parameters = tool_data["parameters"]
            if "returnSchema" in tool_data:
                tool.return_schema = tool_data["returnSchema"]
            if "endpoint" in tool_data:
                tool.endpoint = tool_data["endpoint"]
            if "handler" in tool_data:
                tool.handler = tool_data["handler"]
            if "isAsync" in tool_data:
                tool.is_async = tool_data["isAsync"]
            if "isActive" in tool_data:
                tool.is_active = tool_data["isActive"]
            
            tool.version += 1
            db.commit()
            db.refresh(tool)
            
            return {"success": True, "data": tool.to_dict(), "message": "更新成功"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to update tool: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def delete_tool(cls, db: Session, tool_code: str) -> Dict[str, Any]:
        try:
            tool = db.query(Tool).filter(Tool.tool_code == tool_code).first()
            if not tool:
                return {"success": False, "message": f"工具 {tool_code} 不存在"}
            db.delete(tool)
            db.commit()
            return {"success": True, "message": "删除成功"}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to delete tool: {e}")
            return {"success": False, "message": str(e)}
    
    @classmethod
    def toggle_active(cls, db: Session, tool_code: str) -> Dict[str, Any]:
        try:
            tool = db.query(Tool).filter(Tool.tool_code == tool_code).first()
            if not tool:
                return {"success": False, "message": f"工具 {tool_code} 不存在"}
            tool.is_active = not tool.is_active
            db.commit()
            return {"success": True, "data": tool.to_dict()}
        except Exception as e:
            db.rollback()
            logger.exception(f"Failed to toggle tool: {e}")
            return {"success": False, "message": str(e)}
