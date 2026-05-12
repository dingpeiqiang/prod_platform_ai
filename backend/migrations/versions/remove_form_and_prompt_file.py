"""remove form_code and action_prompt_file fields from scenes and scene_history tables

Revision ID: remove_form_fields
Revises: add_prompt_code
Create Date: 2026-05-12 02:00:00.000000

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'remove_form_fields'
down_revision = 'add_prompt_code'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.drop_column('scenes', 'form_code')
    op.drop_column('scenes', 'action_prompt_file')
    op.drop_column('scene_history', 'form_code')
    op.drop_column('scene_history', 'action_prompt_file')


def downgrade() -> None:
    op.add_column('scenes', sa.Column('form_code', sa.String(100), nullable=True))
    op.add_column('scenes', sa.Column('action_prompt_file', sa.String(255), nullable=True))
    op.add_column('scene_history', sa.Column('form_code', sa.String(100), nullable=True))
    op.add_column('scene_history', sa.Column('action_prompt_file', sa.String(255), nullable=True))