"""drop deprecated forms and form_templates tables

Revision ID: drop_forms_tables
Revises: refactor_form_to_ontology
Create Date: 2026-05-19 02:00:00.000000

说明：
- 删除 forms 表（已废弃，本体定义在 ontologies 表中）
- 删除 form_templates 表（已废弃，FormTemplate 模型已移除）
- AI 原生架构中不再需要"表单"概念
"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'drop_forms_tables'
down_revision = 'refactor_form_to_ontology'
branch_labels = None
depends_on = None


def upgrade() -> None:
    """升级：删除废弃的表"""
    from sqlalchemy import text
    
    # 1. 删除外键约束（如果有）
    try:
        op.drop_constraint('form_instances_form_id_fkey', 'ontology_instances', type_='foreignkey')
    except Exception:
        pass  # 外键可能不存在
    
    # 2. 使用原生 SQL 删除表（IF EXISTS）
    op.get_bind().execute(text('DROP TABLE IF EXISTS form_templates'))
    print("✅ 已删除 form_templates 表（如果存在）")
    
    op.get_bind().execute(text('DROP TABLE IF EXISTS forms'))
    print("✅ 已删除 forms 表（如果存在）")
    
    print("\n🎉 AI 原生架构重构完成：")
    print("   - 删除了所有 Form 相关的表")
    print("   - 保留了 Ontology（本体定义）和 OntologyInstance（本体实例）")


def downgrade() -> None:
    """降级：恢复表（需要手动重建数据结构）"""
    
    print("⚠️  警告：降级操作无法自动恢复数据")
    print("   如果需要恢复，请从备份中还原")
    
    # 重新创建 forms 表（空表）
    op.create_table(
        'forms',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('form_code', sa.String(length=100), nullable=False),
        sa.Column('form_name', sa.String(length=200), nullable=False),
        sa.Column('description', sa.Text(), nullable=True),
        sa.Column('category', sa.String(length=50), nullable=True),
        sa.Column('entities', sa.JSON(), nullable=True),
        sa.Column('layout', sa.JSON(), nullable=True),
        sa.Column('validation_rules', sa.JSON(), nullable=True),
        sa.Column('ontology_code', sa.String(length=100), nullable=True),
        sa.Column('is_active', sa.Boolean(), nullable=True),
        sa.Column('version', sa.Integer(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=True),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_forms_form_code'), 'forms', ['form_code'], unique=True)
    op.create_index(op.f('ix_forms_id'), 'forms', ['id'], unique=False)
    
    # 重新创建 form_templates 表（空表）
    op.create_table(
        'form_templates',
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('form_code', sa.String(length=100), nullable=False),
        sa.Column('form_name', sa.String(length=200), nullable=True),
        sa.Column('schema', sa.JSON(), nullable=True),
        sa.Column('version', sa.Integer(), nullable=True),
        sa.Column('is_active', sa.Boolean(), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=True),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint('id')
    )
    op.create_index(op.f('ix_form_templates_form_code'), 'form_templates', ['form_code'], unique=False)
    op.create_index(op.f('ix_form_templates_id'), 'form_templates', ['id'], unique=False)
    
    print("✅ 已重新创建空表（无数据）")
