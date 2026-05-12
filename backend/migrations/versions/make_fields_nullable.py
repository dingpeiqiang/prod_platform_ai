"""remove deprecated form_id and template_id from form_instances

Revision ID: remove_deprecated_fields
Revises: add_form_code_to_instances, add_session_id_to_instances
Create Date: 2026-05-12 16:42:00.000000

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'remove_deprecated_fields'
down_revision = ('add_form_code_to_instances', 'add_session_id_to_instances')
branch_labels = None
depends_on = None


def upgrade() -> None:
    # 删除已废弃的 form_id 字段
    op.drop_column('form_instances', 'form_id')
    
    # 删除已废弃的 template_id 字段
    op.drop_column('form_instances', 'template_id')


def downgrade() -> None:
    # 恢复 form_id 字段
    op.add_column('form_instances', sa.Column('form_id', sa.String(100), nullable=True))
    
    # 恢复 template_id 字段
    op.add_column('form_instances', sa.Column('template_id', sa.Integer(), nullable=True))
