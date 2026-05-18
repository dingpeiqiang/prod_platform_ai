"""
测试工作流 MCP 工具
"""
import sys
import os

# 添加项目根目录到路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'backend'))

from app.mcp_tools import register_all_tools, get_toolhub


def test_workflow_tools():
    """测试工作流工具注册"""
    print("=" * 60)
    print("测试工作流 MCP 工具")
    print("=" * 60)
    
    # 注册所有工具
    hub = register_all_tools()
    
    print(f"\n已注册工具总数: {hub.get_tool_count()}")
    print(f"可用分类: {hub.get_categories()}")
    
    # 检查工作流相关工具
    workflow_tools = [
        "execute_workflow",
        "list_workflows",
        "get_workflow_detail",
        "get_workflow_execution_history"
    ]
    
    print("\n" + "-" * 60)
    print("检查工作流工具:")
    print("-" * 60)
    
    for tool_name in workflow_tools:
        exists = hub.has_tool(tool_name)
        status = "✅ 已注册" if exists else "❌ 未注册"
        print(f"{tool_name:35} {status}")
        
        if exists:
            tool = hub.get_tool(tool_name)
            print(f"  - 描述: {tool.description[:80]}...")
            print(f"  - 分类: {tool.category}")
    
    # 获取工作流工具的 schema
    print("\n" + "-" * 60)
    print("工作流工具 Schema (用于 LLM):")
    print("-" * 60)
    
    schemas_str = hub.get_tool_schemas_for_llm()
    print(schemas_str)


if __name__ == "__main__":
    test_workflow_tools()
