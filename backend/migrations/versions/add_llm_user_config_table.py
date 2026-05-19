"""add llm user config table

Revision ID: add_llm_config
Revises: 
Create Date: 2026-05-19 20:15:00.000000

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'add_llm_config'
down_revision = 'rename_metadata_field'  # 依赖上一个迁移
branch_labels = None
depends_on = None


def upgrade():
    # 创建 LLM 用户配置表
    op.create_table(
        'llm_user_configs',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('user_identifier', sa.String(length=100), nullable=False),
        sa.Column('provider', sa.String(length=50), nullable=False, server_default='custom'),
        sa.Column('model', sa.String(length=100), nullable=False),
        sa.Column('api_key', sa.Text(), nullable=True),
        sa.Column('base_url', sa.Text(), nullable=True),
        sa.Column('temperature', sa.Float(), nullable=False, server_default='0.3'),
        sa.Column('max_tokens', sa.Integer(), nullable=False, server_default='2048'),
        sa.Column('thinking', sa.Boolean(), nullable=False, server_default='false'),
        sa.Column('max_input_tokens', sa.Integer(), nullable=True, server_default='180000'),
        sa.Column('is_active', sa.Boolean(), nullable=False, server_default='true'),
        sa.Column('config_name', sa.String(length=100), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.func.now()),
        sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.func.now(), onupdate=sa.func.now()),
        sa.Column('last_used_at', sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint('id')
    )
    
    # 创建索引
    op.create_index(op.f('ix_llm_user_configs_id'), 'llm_user_configs', ['id'], unique=False)
    op.create_index(op.f('ix_llm_user_configs_user_identifier'), 'llm_user_configs', ['user_identifier'], unique=False)


def downgrade():
    op.drop_index(op.f('ix_llm_user_configs_user_identifier'), table_name='llm_user_configs')
    op.drop_index(op.f('ix_llm_user_configs_id'), table_name='llm_user_configs')
    op.drop_table('llm_user_configs')
