"""refactor form to ontology - rename tables and fields

Revision ID: refactor_form_to_ontology
Revises: add_session_id_to_instances
Create Date: 2026-05-19 00:00:00.000000

说明：
- 将 form_instances 表重命名为 ontology_instances
- 将 form_history 表重命名为 ontology_instance_history  
- 将 form_code 字段重命名为 ontology_code
- Forms 表标记为废弃（不删除，保留兼容）
"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'refactor_form_to_ontology'
down_revision = ('add_is_in_library_field', 'add_llm_config')  # 合并多个分支
branch_labels = None
depends_on = None


def upgrade() -> None:
    """升级：重命名表和字段"""
    
    # 1. 重命名 form_instances → ontology_instances
    op.rename_table('form_instances', 'ontology_instances')
    
    # 2. 重命名 form_history → ontology_instance_history
    op.rename_table('form_history', 'ontology_instance_history')
    
    # 3. 先清理 NULL 值（如果有）
    op.execute("UPDATE ontology_instances SET form_code = '' WHERE form_code IS NULL")
    
    # 4. 重命名 ontology_instances 表的 form_code → ontology_code
    op.alter_column('ontology_instances', 'form_code', 
                    new_column_name='ontology_code',
                    existing_type=sa.String(100),
                    nullable=False)
    
    # 5. 更新索引名称
    op.drop_index('ix_form_instances_form_code', table_name='ontology_instances')
    op.create_index(op.f('ix_ontology_instances_ontology_code'), 
                    'ontology_instances', ['ontology_code'], unique=False)
    
    # 6. 更新外键引用（如果有）
    # form_history.form_instance_id → ontology_instance_history.form_instance_id
    # 外键字段名不变，但需要更新外键约束名称
    try:
        op.drop_constraint('form_history_form_instance_id_fkey', 
                          'ontology_instance_history', type_='foreignkey')
        op.create_foreign_key('ontology_instance_history_form_instance_id_fkey',
                             'ontology_instance_history', 'ontology_instances',
                             ['form_instance_id'], ['id'])
    except Exception as e:
        # 如果外键不存在或名称不同，忽略错误
        print(f"Warning: Could not update foreign key constraint: {e}")
    
    # 7. Forms 表保持不变，但添加注释标记为废弃
    # 在 SQLAlchemy 模型层面标记，不在数据库层面操作
    
    print("✅ 数据库迁移完成：")
    print("   - form_instances → ontology_instances")
    print("   - form_history → ontology_instance_history")
    print("   - form_code → ontology_code")


def downgrade() -> None:
    """降级：恢复原表名和字段名"""
    
    # 1. 恢复外键约束名称
    try:
        op.drop_constraint('ontology_instance_history_form_instance_id_fkey',
                          'ontology_instance_history', type_='foreignkey')
        op.create_foreign_key('form_history_form_instance_id_fkey',
                             'ontology_instance_history', 'ontology_instances',
                             ['form_instance_id'], ['id'])
    except Exception as e:
        print(f"Warning: Could not restore foreign key constraint: {e}")
    
    # 2. 恢复索引名称
    op.drop_index(op.f('ix_ontology_instances_ontology_code'), 
                  table_name='ontology_instances')
    op.create_index('ix_form_instances_form_code', 
                    'ontology_instances', ['ontology_code'], unique=False)
    
    # 3. 恢复字段名 ontology_code → form_code
    op.alter_column('ontology_instances', 'ontology_code',
                    new_column_name='form_code',
                    existing_type=sa.String(100),
                    nullable=False)
    
    # 4. 恢复表名
    op.rename_table('ontology_instance_history', 'form_history')
    op.rename_table('ontology_instances', 'form_instances')
    
    print("✅ 数据库回滚完成")
