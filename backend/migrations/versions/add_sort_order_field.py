"""add_sort_order_field - 添加消息排序字段

Revision ID: add_sort_order
Revises: v2_generic
Create Date: 2026-05-07 10:00:00

- 为 chat_messages 表添加 sort_order 字段，用于消息排序
"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa


revision: str = 'add_sort_order'
down_revision: Union[str, None] = 'v2_generic'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # 为已有数据设置初始 sort_order 值（如果字段已存在）
    op.execute('UPDATE chat_messages SET sort_order = id WHERE sort_order IS NULL')
    
    # 设置字段为 NOT NULL（MySQL需要指定existing_type）
    op.alter_column(
        'chat_messages', 
        'sort_order',
        existing_type=sa.Integer(),
        nullable=False
    )
    
    # 添加索引（如果不存在）
    op.create_index(op.f('ix_messages_sort_order'), 'chat_messages', ['sort_order'], unique=False)


def downgrade() -> None:
    op.drop_index(op.f('ix_messages_sort_order'), table_name='chat_messages')
    op.drop_column('chat_messages', 'sort_order')