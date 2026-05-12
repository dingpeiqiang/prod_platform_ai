"""sync_scene_model - 同步场景模型与数据库结构

Revision ID: sync_scene_model
Revises: add_scene_domains
Create Date: 2026-05-12

- 删除不需要的 domain1, domain2, domain3 字段
- 添加 type 和 parent_id 字段的索引
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'sync_scene_model'
down_revision: Union[str, None] = 'add_scene_domains'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # 删除不需要的域字段
    op.drop_column('scenes', 'domain3')
    op.drop_column('scenes', 'domain2')
    op.drop_column('scenes', 'domain1')
    
    # 添加 type 和 parent_id 字段的索引（如果不存在）
    try:
        op.create_index(op.f('ix_scenes_type'), 'scenes', ['type'], unique=False)
    except Exception:
        pass
    
    try:
        op.create_index(op.f('ix_scenes_parent_id'), 'scenes', ['parent_id'], unique=False)
    except Exception:
        pass


def downgrade() -> None:
    # 恢复域字段
    op.add_column('scenes', sa.Column('domain1', sa.String(length=100), nullable=True))
    op.add_column('scenes', sa.Column('domain2', sa.String(length=100), nullable=True))
    op.add_column('scenes', sa.Column('domain3', sa.String(length=100), nullable=True))
    
    # 删除索引（如果存在）
    try:
        op.drop_index(op.f('ix_scenes_type'), table_name='scenes')
    except Exception:
        pass
    
    try:
        op.drop_index(op.f('ix_scenes_parent_id'), table_name='scenes')
    except Exception:
        pass