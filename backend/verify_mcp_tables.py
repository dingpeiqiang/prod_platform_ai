"""验证 MCP 调用日志表是否创建成功"""
from app.core.database import engine
from sqlalchemy import inspect

inspector = inspect(engine)

print("=" * 60)
print("数据库表列表:")
print("=" * 60)
tables = inspector.get_table_names()
for table in tables:
    print(f"  ✓ {table}")

print("\n" + "=" * 60)
print("mcp_call_logs 表结构:")
print("=" * 60)
if 'mcp_call_logs' in tables:
    columns = inspector.get_columns('mcp_call_logs')
    for col in columns:
        print(f"  - {col['name']:30s} {str(col['type']):20s} nullable={col['nullable']}")
    
    indexes = inspector.get_indexes('mcp_call_logs')
    print(f"\n索引 ({len(indexes)} 个):")
    for idx in indexes:
        print(f"  - {idx['name']}: {idx['column_names']}")
else:
    print("  ✗ 表不存在!")

print("\n" + "=" * 60)
print("mcp_tool_stats 表结构:")
print("=" * 60)
if 'mcp_tool_stats' in tables:
    columns = inspector.get_columns('mcp_tool_stats')
    for col in columns:
        print(f"  - {col['name']:30s} {str(col['type']):20s} nullable={col['nullable']}")
    
    indexes = inspector.get_indexes('mcp_tool_stats')
    print(f"\n索引 ({len(indexes)} 个):")
    for idx in indexes:
        print(f"  - {idx['name']}: {idx['column_names']}")
else:
    print("  ✗ 表不存在!")

print("\n" + "=" * 60)
print("mcp_tool_definitions 表结构:")
print("=" * 60)
if 'mcp_tool_definitions' in tables:
    columns = inspector.get_columns('mcp_tool_definitions')
    for col in columns:
        print(f"  - {col['name']:30s} {str(col['type']):20s} nullable={col['nullable']}")
    
    indexes = inspector.get_indexes('mcp_tool_definitions')
    print(f"\n索引 ({len(indexes)} 个):")
    for idx in indexes:
        print(f"  - {idx['name']}: {idx['column_names']}")
else:
    print("  ✗ 表不存在!")

print("\n" + "=" * 60)
print("✓ 验证完成!")
print("=" * 60)
