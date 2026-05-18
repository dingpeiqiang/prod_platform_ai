"""
MCP 工具调用日志模型
用于持久化存储 MCP 工具的调用历史和统计数据
"""
from sqlalchemy import Column, Integer, String, Boolean, Float, DateTime, Text, Index, JSON
from sqlalchemy.sql import func
from app.core.database import Base


class MCPToolDefinition(Base):
    """MCP 工具定义表 - 存储工具的元数据和配置"""
    __tablename__ = "mcp_tool_definitions"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    
    # 工具基本信息
    tool_name = Column(String(100), unique=True, nullable=False, index=True, comment="工具名称（唯一）")
    tool_code = Column(String(100), unique=True, nullable=True, index=True, comment="工具编码（可选）")
    description = Column(Text, nullable=True, comment="工具描述")
    category = Column(String(50), nullable=True, index=True, comment="工具分类")
    
    # 工具状态
    is_enabled = Column(Boolean, nullable=False, default=True, index=True, comment="是否启用")
    is_public = Column(Boolean, nullable=False, default=True, comment="是否公开（对工作流可见）")
    
    # Schema 定义（JSON 格式）
    input_schema = Column(JSON, nullable=True, comment="输入参数 Schema")
    output_schema = Column(JSON, nullable=True, comment="输出结果 Schema")
    
    # 配置信息
    config = Column(JSON, nullable=True, comment="工具配置（超时、重试等）")
    extra_metadata = Column(JSON, nullable=True, comment="扩展元数据")
    
    # 统计信息（冗余字段，便于快速查询）
    total_calls = Column(Integer, nullable=False, default=0, comment="总调用次数")
    last_called_at = Column(DateTime(timezone=True), nullable=True, comment="最后调用时间")
    
    # 时间戳
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    created_by = Column(String(100), nullable=True, comment="创建者")
    updated_by = Column(String(100), nullable=True, comment="更新者")
    
    # 索引
    __table_args__ = (
        Index('idx_tool_category', 'category'),
        Index('idx_tool_enabled', 'is_enabled'),
    )

    def to_dict(self):
        """转换为字典"""
        return {
            "id": self.id,
            "tool_name": self.tool_name,
            "tool_code": self.tool_code,
            "description": self.description,
            "category": self.category,
            "is_enabled": self.is_enabled,
            "is_public": self.is_public,
            "input_schema": self.input_schema,
            "output_schema": self.output_schema,
            "config": self.config,
            "extra_metadata": self.extra_metadata,
            "total_calls": self.total_calls,
            "last_called_at": self.last_called_at.isoformat() if self.last_called_at else None,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None,
        }


class MCPCallLog(Base):
    """MCP 工具调用日志表"""
    __tablename__ = "mcp_call_logs"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    
    # 工具信息
    tool_name = Column(String(100), nullable=False, index=True, comment="工具名称")
    tool_category = Column(String(50), nullable=True, index=True, comment="工具分类")
    
    # 调用结果
    success = Column(Boolean, nullable=False, default=False, comment="是否成功")
    execution_time_ms = Column(Float, nullable=True, comment="执行耗时（毫秒）")
    error_message = Column(Text, nullable=True, comment="错误信息")
    
    # 时间戳
    timestamp = Column(DateTime(timezone=True), server_default=func.now(), index=True, comment="调用时间")
    
    # 可选：请求参数和响应结果（JSON 格式，可能较大）
    request_args = Column(Text, nullable=True, comment="请求参数 JSON")
    response_data = Column(Text, nullable=True, comment="响应数据 JSON")
    
    # 索引：优化查询性能
    __table_args__ = (
        Index('idx_tool_timestamp', 'tool_name', 'timestamp'),
        Index('idx_timestamp_desc', 'timestamp', mysql_length=None, postgresql_ops={'timestamp': 'DESC'}),
    )

    def to_dict(self):
        """转换为字典"""
        return {
            "id": self.id,
            "tool_name": self.tool_name,
            "tool_category": self.tool_category,
            "success": self.success,
            "execution_time_ms": self.execution_time_ms,
            "error_message": self.error_message,
            "timestamp": self.timestamp.isoformat() if self.timestamp else None,
            "request_args": self.request_args,
            "response_data": self.response_data,
        }


class MCPToolStats(Base):
    """MCP 工具聚合统计表（每小时/每天汇总）"""
    __tablename__ = "mcp_tool_stats"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    
    # 工具和周期标识
    tool_name = Column(String(100), nullable=False, index=True, comment="工具名称")
    stat_date = Column(String(20), nullable=False, index=True, comment="统计日期 YYYY-MM-DD")
    stat_hour = Column(Integer, nullable=True, comment="统计小时 0-23，NULL 表示日统计")
    
    # 统计数据
    total_calls = Column(Integer, nullable=False, default=0, comment="总调用次数")
    success_calls = Column(Integer, nullable=False, default=0, comment="成功调用次数")
    failed_calls = Column(Integer, nullable=False, default=0, comment="失败调用次数")
    total_response_time_ms = Column(Float, nullable=False, default=0.0, comment="总响应时间")
    avg_response_time_ms = Column(Float, nullable=False, default=0.0, comment="平均响应时间")
    
    # 时间戳
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())
    
    # 唯一约束：同一工具同一时段只有一条记录
    __table_args__ = (
        Index('idx_tool_date_hour', 'tool_name', 'stat_date', 'stat_hour', unique=True),
    )

    def to_dict(self):
        """转换为字典"""
        return {
            "id": self.id,
            "tool_name": self.tool_name,
            "stat_date": self.stat_date,
            "stat_hour": self.stat_hour,
            "total_calls": self.total_calls,
            "success_calls": self.success_calls,
            "failed_calls": self.failed_calls,
            "avg_response_time_ms": self.avg_response_time_ms,
        }
