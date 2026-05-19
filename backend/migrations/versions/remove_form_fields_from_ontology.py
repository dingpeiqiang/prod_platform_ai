"""remove form_code and form_name from ontologies table

Revision ID: remove_form_fields_from_ontology
Revises: drop_forms_tables
Create Date: 2026-05-19 03:00:00.000000

说明：
- 从 ontologies 表中删除 form_code 和 form_name 字段
- AI 原生架构中，本体本身就是权威数据源，不需要引用"表单"
- ontology_code 和 ontology_name 已经足够标识本体
"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'remove_form_fields_from_ontology'
down_revision = 'drop_forms_tables'
branch_labels = None
depends_on = None


def upgrade() -> None:
    """升级：删除表单相关字段"""
    
    # 1. 删除 form_name 字段
    op.drop_column('ontologies', 'form_name')
    print("✅ 已删除 ontologies.form_name 字段")
    
    # 2. 删除 form_code 字段
    op.drop_column('ontologies', 'form_code')
    print("✅ 已删除 ontologies.form_code 字段")
    
    print("\n🎉 本体表清理完成：")
    print("   - 移除了所有 Form 相关字段")
    print("   - 本体现在是完全独立的权威数据源")


def downgrade() -> None:
    """降级：恢复字段"""
    
    # 1. 恢复 form_code 字段
    op.add_column('ontologies', 
        sa.Column('form_code', sa.String(length=100), nullable=True)
    )
    op.create_index(op.f('ix_ontologies_form_code'), 'ontologies', ['form_code'], unique=False)
    
    # 2. 恢复 form_name 字段
    op.add_column('ontologies',
        sa.Column('form_name', sa.String(length=200), nullable=True)
    )
    
    print("✅ 已恢复 form_code 和 form_name 字段（空值）")
