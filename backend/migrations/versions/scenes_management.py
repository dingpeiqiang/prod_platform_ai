"""scenes_management - 场景管理功能

Revision ID: scenes_management
Revises: v2_generic
Create Date: 2026-05-10

- scenes: 场景配置表（场景管理核心表）
"""
from typing import Sequence, Union
import json
from pathlib import Path
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'scenes_management'
down_revision: Union[str, None] = 'v2_generic'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # 创建 scenes 表
    op.create_table('scenes',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('scene_code', sa.String(length=100), nullable=False),
        sa.Column('scene_name', sa.String(length=200), nullable=False),
        sa.Column('description', sa.Text(), nullable=True),
        sa.Column('keywords', sa.JSON(), nullable=False),
        sa.Column('priority', sa.Integer(), nullable=True),
        sa.Column('is_active', sa.Boolean(), nullable=True),
        sa.Column('intent_type', sa.String(length=50), nullable=True),
        sa.Column('form_code', sa.String(length=100), nullable=True),
        sa.Column('action_type', sa.String(length=50), nullable=True),
        sa.Column('action_prompt_file', sa.String(length=255), nullable=True),
        sa.Column('required_tools', sa.JSON(), nullable=True),
        sa.Column('available_tools', sa.JSON(), nullable=True),
        sa.Column('pre_action_steps', sa.JSON(), nullable=True),
        sa.Column('post_action_steps', sa.JSON(), nullable=True),
        sa.Column('version', sa.Integer(), nullable=True),
        sa.Column('created_by', sa.String(length=100), nullable=True),
        sa.Column('updated_by', sa.String(length=100), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=True),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_scenes_id'), 'scenes', ['id'], unique=False)
    op.create_index(op.f('ix_scenes_scene_code'), 'scenes', ['scene_code'], unique=True)

    # 从 scene_mapping.json 迁移现有数据
    migrate_existing_scenes()


def downgrade() -> None:
    op.drop_index(op.f('ix_scenes_scene_code'), table_name='scenes')
    op.drop_index(op.f('ix_scenes_id'), table_name='scenes')
    op.drop_table('scenes')


def migrate_existing_scenes():
    """从 scene_mapping.json 迁移现有场景数据"""
    try:
        config_path = Path(__file__).parent.parent.parent / 'config' / 'scenes' / 'scene_mapping.json'
        if not config_path.exists():
            return

        with open(config_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        scene_mappings = data.get('sceneMappings', [])
        if not scene_mappings:
            return

        # 使用SQLAlchemy的connection直接插入数据
        conn = op.get_bind()
        
        for scene_data in scene_mappings:
            # 检查场景是否已存在
            existing = conn.execute(
                sa.text("SELECT id FROM scenes WHERE scene_code = :code"),
                {"code": scene_data.get('sceneCode')}
            ).fetchone()
            
            if existing:
                continue

            # 插入新场景
            conn.execute(
                sa.text("""
                    INSERT INTO scenes 
                    (scene_code, scene_name, description, keywords, priority, is_active, 
                     intent_type, form_code, action_type, action_prompt_file,
                     required_tools, available_tools, pre_action_steps, post_action_steps,
                     version, created_at, updated_at)
                    VALUES 
                    (:scene_code, :scene_name, :description, :keywords, :priority, :is_active,
                     :intent_type, :form_code, :action_type, :action_prompt_file,
                     :required_tools, :available_tools, :pre_action_steps, :post_action_steps,
                     :version, NOW(), NOW())
                """),
                {
                    "scene_code": scene_data.get('sceneCode'),
                    "scene_name": scene_data.get('sceneName', scene_data.get('sceneCode')),
                    "description": scene_data.get('description'),
                    "keywords": json.dumps(scene_data.get('keywords', [])),
                    "priority": scene_data.get('priority', 10),
                    "is_active": scene_data.get('isActive', True),
                    "intent_type": scene_data.get('intentType'),
                    "form_code": scene_data.get('formCode'),
                    "action_type": scene_data.get('actionType', 'form_generation'),
                    "action_prompt_file": scene_data.get('actionPrompt'),
                    "required_tools": json.dumps(scene_data.get('requiredTools', [])),
                    "available_tools": json.dumps(scene_data.get('availableTools', [])),
                    "pre_action_steps": json.dumps(scene_data.get('preActionSteps', [])),
                    "post_action_steps": json.dumps(scene_data.get('postActionSteps', [])),
                    "version": 1
                }
            )

    except Exception as e:
        print(f"Warning: Failed to migrate scenes from file: {e}")