"""add session_id to form_instances

Revision ID: add_session_id_to_instances
Revises: add_form_code_to_instances
Create Date: 2026-05-12 11:56:00.000000

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'add_session_id_to_instances'
down_revision = 'add_form_code_to_instances'
branch_labels = None
depends_on = None


def upgrade() -> None:
    # 添加 session_id 字段到 form_instances 表
    op.add_column('form_instances', sa.Column('session_id', sa.String(100), nullable=True))
    
    # 为现有数据设置默认值
    op.execute("UPDATE form_instances SET session_id = NULL WHERE session_id IS NULL")
    
    # 添加索引
    op.create_index(op.f('ix_form_instances_session_id'), 'form_instances', ['session_id'], unique=False)


def downgrade() -> None:
    # 删除索引
    op.drop_index(op.f('ix_form_instances_session_id'), table_name='form_instances')
    
    # 删除字段
    op.drop_column('form_instances', 'session_id')
