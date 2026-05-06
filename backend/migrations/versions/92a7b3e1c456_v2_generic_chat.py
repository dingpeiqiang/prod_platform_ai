"""v2_generic_chat - 通用聊天架构

Revision ID: v2_generic
Revises: 91e5166dcf37
Create Date: 2026-05-07 02:20:00

- chat_sessions_v2: 通用会话表（支持 metadata / context_tags / parent_session）
- chat_messages_v2: 通用消息表（只存核心字段，业务全入 metadata）
- chat_message_metadata: 消息 KV 扩展表（插入式业务字段）
"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa


revision: str = 'v2_generic'
down_revision: Union[str, None] = '91e5166dcf37'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # ── 通用会话表 ──────────────────────────────────────────
    op.create_table(
        'chat_sessions_v2',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('session_id', sa.String(length=64), unique=True, nullable=False),
        sa.Column('user_id', sa.String(length=100), nullable=True),
        sa.Column('title', sa.String(length=200), nullable=True),
        sa.Column('context_tags', sa.JSON(), nullable=True),          # ["sales", "urgent"]
        sa.Column('metadata', sa.JSON(), nullable=True),               # {source: "web"}
        sa.Column('status', sa.String(length=20), default='active'),  # active / archived
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=True),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_sessions_v2_id'),          'chat_sessions_v2', ['id'], unique=False)
    op.create_index(op.f('ix_sessions_v2_session_id'),  'chat_sessions_v2', ['session_id'], unique=True)
    op.create_index(op.f('ix_sessions_v2_user_id'),     'chat_sessions_v2', ['user_id'], unique=False)
    op.create_index(op.f('ix_sessions_v2_updated'),     'chat_sessions_v2', ['updated_at'], unique=False)

    # ── 通用消息表 ──────────────────────────────────────────
    op.create_table(
        'chat_messages_v2',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('message_id', sa.String(length=64), unique=True, nullable=False),  # 外部 UUID
        sa.Column('session_id', sa.String(length=64), nullable=False),
        sa.Column('role', sa.String(length=20), nullable=False),       # user / assistant / system
        sa.Column('content', sa.Text(), nullable=False),
        sa.Column('content_type', sa.String(length=20), default='text'),  # text / markdown / json / form
        sa.Column('parent_id', sa.String(length=64), nullable=True),   # 父消息（多轮树状）
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=True),
        sa.ForeignKeyConstraint(['session_id'], ['chat_sessions_v2.session_id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_messages_v2_id'),         'chat_messages_v2', ['id'], unique=False)
    op.create_index(op.f('ix_messages_v2_message_id'), 'chat_messages_v2', ['message_id'], unique=True)
    op.create_index(op.f('ix_messages_v2_session_id'), 'chat_messages_v2', ['session_id'], unique=False)
    op.create_index(op.f('ix_messages_v2_created'),    'chat_messages_v2', ['created_at'], unique=False)

    # ── 消息元数据表 ─────────────────────────────────────────
    op.create_table(
        'chat_message_metadata',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('message_id', sa.String(length=64), nullable=False),
        sa.Column('key', sa.String(length=100), nullable=False),
        sa.Column('value', sa.Text(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=True),
        sa.ForeignKeyConstraint(['message_id'], ['chat_messages_v2.message_id'], ondelete='CASCADE'),
        sa.UniqueConstraint('message_id', 'key'),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_meta_id'),         'chat_message_metadata', ['id'], unique=False)
    op.create_index(op.f('ix_meta_message_id'), 'chat_message_metadata', ['message_id'], unique=False)
    op.create_index(op.f('ix_meta_key'),        'chat_message_metadata', ['key'], unique=False)


def downgrade() -> None:
    op.drop_index(op.f('ix_meta_key'),        table_name='chat_message_metadata')
    op.drop_index(op.f('ix_meta_message_id'), table_name='chat_message_metadata')
    op.drop_index(op.f('ix_meta_id'),         table_name='chat_message_metadata')
    op.drop_table('chat_message_metadata')

    op.drop_index(op.f('ix_messages_v2_created'),    table_name='chat_messages_v2')
    op.drop_index(op.f('ix_messages_v2_session_id'), table_name='chat_messages_v2')
    op.drop_index(op.f('ix_messages_v2_message_id'), table_name='chat_messages_v2')
    op.drop_index(op.f('ix_messages_v2_id'),         table_name='chat_messages_v2')
    op.drop_table('chat_messages_v2')

    op.drop_index(op.f('ix_sessions_v2_updated'),    table_name='chat_sessions_v2')
    op.drop_index(op.f('ix_sessions_v2_user_id'),    table_name='chat_sessions_v2')
    op.drop_index(op.f('ix_sessions_v2_session_id'), table_name='chat_sessions_v2')
    op.drop_index(op.f('ix_sessions_v2_id'),         table_name='chat_sessions_v2')
    op.drop_table('chat_sessions_v2')