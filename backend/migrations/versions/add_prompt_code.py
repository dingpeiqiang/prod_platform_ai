"""add prompt_code field to scenes and scene_history tables

Revision ID: add_prompt_code
Revises: scenes_management
Create Date: 2026-05-12 01:50:00.000000

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'add_prompt_code'
down_revision = 'scenes_management'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column('scenes', sa.Column('prompt_code', sa.String(100), nullable=True))
    op.create_index('ix_scenes_prompt_code', 'scenes', ['prompt_code'])
    
    op.add_column('scene_history', sa.Column('prompt_code', sa.String(100), nullable=True))


def downgrade() -> None:
    op.drop_index('ix_scenes_prompt_code', table_name='scenes')
    op.drop_column('scenes', 'prompt_code')
    op.drop_column('scene_history', 'prompt_code')