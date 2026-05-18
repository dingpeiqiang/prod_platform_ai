"""add mcp tool definitions table

Revision ID: add_mcp_tool_definitions
Revises: add_mcp_call_logs
Create Date: 2026-05-18 23:10:00.000000

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'add_mcp_tool_definitions'
down_revision = 'add_mcp_call_logs'
branch_labels = None
depends_on = None


def upgrade():
    """升级：创建 MCP 工具定义表"""
    
    op.create_table(
        'mcp_tool_definitions',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('tool_name', sa.String(length=100), nullable=False),
        sa.Column('tool_code', sa.String(length=100), nullable=True),
        sa.Column('description', sa.Text(), nullable=True),
        sa.Column('category', sa.String(length=50), nullable=True),
        sa.Column('is_enabled', sa.Boolean(), nullable=False, default=True),
        sa.Column('is_public', sa.Boolean(), nullable=False, default=True),
        sa.Column('input_schema', sa.JSON(), nullable=True),
        sa.Column('output_schema', sa.JSON(), nullable=True),
        sa.Column('config', sa.JSON(), nullable=True),
        sa.Column('metadata', sa.JSON(), nullable=True),
        sa.Column('total_calls', sa.Integer(), nullable=False, default=0),
        sa.Column('last_called_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('CURRENT_TIMESTAMP')),
        sa.Column('updated_at', sa.DateTime(timezone=True), onupdate=sa.text('CURRENT_TIMESTAMP')),
        sa.Column('created_by', sa.String(length=100), nullable=True),
        sa.Column('updated_by', sa.String(length=100), nullable=True),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('tool_name'),
        sa.UniqueConstraint('tool_code')
    )
    
    # 创建索引
    op.create_index('idx_tool_name', 'mcp_tool_definitions', ['tool_name'])
    op.create_index('idx_tool_code', 'mcp_tool_definitions', ['tool_code'])
    op.create_index('idx_tool_category', 'mcp_tool_definitions', ['category'])
    op.create_index('idx_tool_enabled', 'mcp_tool_definitions', ['is_enabled'])


def downgrade():
    """降级：删除 MCP 工具定义表"""
    op.drop_index('idx_tool_enabled', table_name='mcp_tool_definitions')
    op.drop_index('idx_tool_category', table_name='mcp_tool_definitions')
    op.drop_index('idx_tool_code', table_name='mcp_tool_definitions')
    op.drop_index('idx_tool_name', table_name='mcp_tool_definitions')
    op.drop_table('mcp_tool_definitions')
