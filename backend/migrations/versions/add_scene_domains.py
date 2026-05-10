"""add_scene_domains - 添加场景三层域字段

Revision ID: add_scene_domains
Revises: scenes_management
Create Date: 2026-05-10

- 添加 domain1, domain2, domain3 三个域字段到 scenes 表
"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'add_scene_domains'
down_revision: Union[str, None] = 'scenes_management'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # 添加三个域字段
    op.add_column('scenes', sa.Column('domain1', sa.String(length=100), nullable=True))
    op.add_column('scenes', sa.Column('domain2', sa.String(length=100), nullable=True))
    op.add_column('scenes', sa.Column('domain3', sa.String(length=100), nullable=True))


def downgrade() -> None:
    # 删除三个域字段
    op.drop_column('scenes', 'domain3')
    op.drop_column('scenes', 'domain2')
    op.drop_column('scenes', 'domain1')
