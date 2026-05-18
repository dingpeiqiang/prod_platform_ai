"""add mcp call log tables

Revision ID: add_mcp_call_logs
Revises: 
Create Date: 2026-05-18 22:30:00.000000

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'add_mcp_call_logs'
down_revision = ('remove_form_fields', 'remove_deprecated_fields')  # 合并两个分支
branch_labels = None
depends_on = None


def upgrade():
    """升级：创建 MCP 调用日志表"""
    
    # 创建 mcp_call_logs 表
    op.create_table(
        'mcp_call_logs',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('tool_name', sa.String(length=100), nullable=False),
        sa.Column('tool_category', sa.String(length=50), nullable=True),
        sa.Column('success', sa.Boolean(), nullable=False, default=False),
        sa.Column('execution_time_ms', sa.Float(), nullable=True),
        sa.Column('error_message', sa.Text(), nullable=True),
        sa.Column('timestamp', sa.DateTime(timezone=True), server_default=sa.text('CURRENT_TIMESTAMP')),
        sa.Column('request_args', sa.Text(), nullable=True),
        sa.Column('response_data', sa.Text(), nullable=True),
        sa.PrimaryKeyConstraint('id')
    )
    
    # 创建索引
    op.create_index('idx_tool_name', 'mcp_call_logs', ['tool_name'])
    op.create_index('idx_tool_category', 'mcp_call_logs', ['tool_category'])
    op.create_index('idx_timestamp', 'mcp_call_logs', ['timestamp'])
    op.create_index('idx_tool_timestamp', 'mcp_call_logs', ['tool_name', 'timestamp'])
    
    # 创建 mcp_tool_stats 表
    op.create_table(
        'mcp_tool_stats',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('tool_name', sa.String(length=100), nullable=False),
        sa.Column('stat_date', sa.String(length=20), nullable=False),
        sa.Column('stat_hour', sa.Integer(), nullable=True),
        sa.Column('total_calls', sa.Integer(), nullable=False, default=0),
        sa.Column('success_calls', sa.Integer(), nullable=False, default=0),
        sa.Column('failed_calls', sa.Integer(), nullable=False, default=0),
        sa.Column('total_response_time_ms', sa.Float(), nullable=False, default=0.0),
        sa.Column('avg_response_time_ms', sa.Float(), nullable=False, default=0.0),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('CURRENT_TIMESTAMP')),
        sa.Column('updated_at', sa.DateTime(timezone=True), onupdate=sa.text('CURRENT_TIMESTAMP')),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('tool_name', 'stat_date', 'stat_hour', name='uq_tool_date_hour')
    )
    
    # 创建索引
    op.create_index('idx_stats_tool_name', 'mcp_tool_stats', ['tool_name'])
    op.create_index('idx_stats_date', 'mcp_tool_stats', ['stat_date'])
    op.create_index('idx_stats_tool_date_hour', 'mcp_tool_stats', ['tool_name', 'stat_date', 'stat_hour'], unique=True)


def downgrade():
    """降级：删除 MCP 调用日志表"""
    op.drop_index('idx_stats_tool_date_hour', table_name='mcp_tool_stats')
    op.drop_index('idx_stats_date', table_name='mcp_tool_stats')
    op.drop_index('idx_stats_tool_name', table_name='mcp_tool_stats')
    op.drop_table('mcp_tool_stats')
    
    op.drop_index('idx_tool_timestamp', table_name='mcp_call_logs')
    op.drop_index('idx_timestamp', table_name='mcp_call_logs')
    op.drop_index('idx_tool_category', table_name='mcp_call_logs')
    op.drop_index('idx_tool_name', table_name='mcp_call_logs')
    op.drop_table('mcp_call_logs')
