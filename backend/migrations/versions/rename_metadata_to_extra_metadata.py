"""rename metadata to extra_metadata in mcp_tool_definitions

Revision ID: rename_metadata_field
Revises: 
Create Date: 2026-05-18 00:15:00.000000

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'rename_metadata_field'
down_revision = 'add_mcp_tool_definitions'
branch_labels = None
depends_on = None


def upgrade():
    # 将 metadata 字段重命名为 extra_metadata
    # MySQL 不支持直接重命名 JSON 字段，需要先删除再添加
    try:
        # 尝试删除旧字段
        op.drop_column('mcp_tool_definitions', 'metadata')
    except Exception:
        # 如果字段不存在，忽略错误
        pass
    
    # 添加新字段
    op.add_column('mcp_tool_definitions', 
        sa.Column('extra_metadata', sa.JSON(), nullable=True, comment='扩展元数据')
    )


def downgrade():
    # 回滚：删除新字段，恢复旧字段
    try:
        op.drop_column('mcp_tool_definitions', 'extra_metadata')
    except Exception:
        pass
    
    op.add_column('mcp_tool_definitions',
        sa.Column('metadata', sa.JSON(), nullable=True, comment='扩展元数据')
    )
