"""add form_code to form_instances

Revision ID: add_form_code_to_instances
Revises: 
Create Date: 2026-05-12 11:50:00.000000

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'add_form_code_to_instances'
down_revision = ('sync_scene_model', 'remove_form_fields', 'add_sort_order')
branch_labels = None
depends_on = None


def upgrade() -> None:
    # 添加 form_code 字段到 form_instances 表
    op.add_column('form_instances', sa.Column('form_code', sa.String(100), nullable=True))
    
    # 为现有数据设置默认值（从 template_id 关联获取，或者设置为空）
    # 注意：这里设置为 NULL，后续需要根据实际情况更新
    op.execute("UPDATE form_instances SET form_code = NULL WHERE form_code IS NULL")
    
    # 添加索引
    op.create_index(op.f('ix_form_instances_form_code'), 'form_instances', ['form_code'], unique=False)
    
    # 如果需要设置为 NOT NULL，需要先确保所有记录都有值
    # op.alter_column('form_instances', 'form_code', existing_type=sa.String(100), nullable=False)


def downgrade() -> None:
    # 删除索引
    op.drop_index(op.f('ix_form_instances_form_code'), table_name='form_instances')
    
    # 删除字段
    op.drop_column('form_instances', 'form_code')
