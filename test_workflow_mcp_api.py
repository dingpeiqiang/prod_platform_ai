"""
测试工作流 MCP 工具的 API 调用
"""
import requests
import json

BASE_URL = "http://localhost:8000"


def test_list_workflows():
    """测试列出工作流"""
    print("=" * 60)
    print("测试 1: 列出所有工作流")
    print("=" * 60)
    
    response = requests.post(
        f"{BASE_URL}/api/v1/mcp/tools/call",
        json={
            "tool_name": "list_workflows",
            "arguments": {
                "active_only": True
            }
        }
    )
    
    if response.status_code == 200:
        result = response.json()
        # MCP 工具返回格式: {success, result: {result: {...}}, error}
        inner_result = result.get('result', {}).get('result', {})
        print(f"✅ 调用成功")
        print(f"工作流数量: {inner_result.get('total', 0)}")
        
        workflows = inner_result.get('workflows', [])
        for wf in workflows:
            print(f"\n- {wf.get('workflowCode')}: {wf.get('workflowName')}")
            print(f"  分类: {wf.get('category')}")
            print(f"  描述: {wf.get('description', 'N/A')[:50]}")
        
        return workflows
    else:
        print(f"❌ 调用失败: {response.status_code}")
        print(response.text)
        return []


def test_get_workflow_detail(workflow_code):
    """测试获取工作流详情"""
    print("\n" + "=" * 60)
    print(f"测试 2: 获取工作流详情 - {workflow_code}")
    print("=" * 60)
    
    response = requests.post(
        f"{BASE_URL}/api/v1/mcp/tools/call",
        json={
            "tool_name": "get_workflow_detail",
            "arguments": {
                "workflow_code": workflow_code
            }
        }
    )
    
    if response.status_code == 200:
        result = response.json()
        if result.get('success'):
            # MCP 工具返回格式: {success, result: {result: {...}}, error}
            detail = result.get('result', {}).get('result', {})
            print(f"✅ 调用成功")
            print(f"工作流名称: {detail.get('workflowName')}")
            print(f"描述: {detail.get('description', 'N/A')}")
            print(f"分类: {detail.get('category')}")
            print(f"节点数: {detail.get('nodeCount')}")
            print(f"连接数: {detail.get('edgeCount')}")
            
            input_params = detail.get('inputParams', [])
            if input_params:
                print(f"\n输入参数:")
                for param in input_params:
                    required = "必填" if param.get('required') else "可选"
                    print(f"  - {param.get('name')} ({param.get('type')}) [{required}]")
                    if param.get('description'):
                        print(f"    {param.get('description')}")
            
            output_params = detail.get('outputParams', [])
            if output_params:
                print(f"\n输出参数:")
                for param in output_params:
                    print(f"  - {param.get('name')}: {param.get('description', 'N/A')}")
            
            return detail
        else:
            print(f"❌ 获取详情失败: {result.get('error')}")
            return None
    else:
        print(f"❌ 调用失败: {response.status_code}")
        print(response.text)
        return None


def test_execute_workflow(workflow_code, inputs=None):
    """测试执行工作流"""
    print("\n" + "=" * 60)
    print(f"测试 3: 执行工作流 - {workflow_code}")
    print("=" * 60)
    
    if inputs is None:
        inputs = {}
    
    response = requests.post(
        f"{BASE_URL}/api/v1/mcp/tools/call",
        json={
            "tool_name": "execute_workflow",
            "arguments": {
                "workflow_code": workflow_code,
                "inputs": inputs
            }
        },
        timeout=30  # 设置超时时间
    )
    
    if response.status_code == 200:
        result = response.json()
        if result.get('success'):
            # MCP 工具返回格式: {success, result: {result: {...}}, error}
            exec_result = result.get('result', {}).get('result', {})
            print(f"✅ 执行成功")
            print(f"执行ID: {exec_result.get('execution_id')}")
            print(f"状态: {exec_result.get('status')}")
            
            outputs = exec_result.get('outputs', {})
            if outputs:
                print(f"\n输出结果:")
                for key, value in outputs.items():
                    print(f"  - {key}: {value}")
            
            error = exec_result.get('error')
            if error:
                print(f"\n⚠️ 警告: {error}")
            
            return exec_result
        else:
            print(f"❌ 执行失败: {result.get('error')}")
            return None
    else:
        print(f"❌ 调用失败: {response.status_code}")
        print(response.text)
        return None


def test_execution_history(workflow_code):
    """测试查询执行历史"""
    print("\n" + "=" * 60)
    print(f"测试 4: 查询执行历史 - {workflow_code}")
    print("=" * 60)
    
    response = requests.post(
        f"{BASE_URL}/api/v1/mcp/tools/call",
        json={
            "tool_name": "get_workflow_execution_history",
            "arguments": {
                "workflow_code": workflow_code,
                "limit": 5
            }
        }
    )
    
    if response.status_code == 200:
        result = response.json()
        if result.get('success'):
            # MCP 工具返回格式: {success, result: {result: {...}}, error}
            history = result.get('result', {}).get('result', {})
            print(f"✅ 查询成功")
            print(f"历史记录数: {history.get('total', 0)}")
            
            executions = history.get('executions', [])
            for exec_data in executions:
                status_icon = "✅" if exec_data.get('status') == 'completed' else "❌"
                print(f"\n{status_icon} 执行ID: {exec_data.get('executionId')}")
                print(f"  状态: {exec_data.get('status')}")
                print(f"  开始时间: {exec_data.get('startTime', 'N/A')}")
                print(f"  耗时: {exec_data.get('durationSeconds', 'N/A')}秒")
                print(f"  触发方式: {exec_data.get('triggerType', 'N/A')}")
            
            return executions
        else:
            print(f"❌ 查询失败: {result.get('error')}")
            return []
    else:
        print(f"❌ 调用失败: {response.status_code}")
        print(response.text)
        return []


def main():
    """主测试函数"""
    print("\n🚀 开始测试工作流 MCP 工具\n")
    
    # 测试 1: 列出工作流
    workflows = test_list_workflows()
    
    if not workflows:
        print("\n⚠️ 没有找到工作流，请先创建工作流")
        return
    
    # 使用第一个工作流进行后续测试
    first_workflow = workflows[0]
    workflow_code = first_workflow.get('workflowCode')
    
    print(f"\n📋 使用工作流进行测试: {workflow_code}")
    
    # 测试 2: 获取工作流详情
    detail = test_get_workflow_detail(workflow_code)
    
    # 测试 3: 执行工作流（可选，需要确保工作流可以执行）
    if detail:
        print("\n⚠️ 跳过执行测试，因为可能需要特定的输入参数")
        print("如需测试执行，请手动调用 execute_workflow 并提供正确的参数")
    
    # 测试 4: 查询执行历史
    test_execution_history(workflow_code)
    
    print("\n" + "=" * 60)
    print("✅ 测试完成！")
    print("=" * 60)
    print("\n💡 提示：现在可以在聊天界面测试 LLM 调用这些工具")
    print("   尝试发送以下消息：")
    print("   - '列出所有可用的工作流'")
    print("   - f'{first_workflow.get(\"workflowName\")} 的详细信息'")
    print("   - '查看工作流执行历史'")


if __name__ == "__main__":
    try:
        main()
    except requests.exceptions.ConnectionError:
        print("❌ 无法连接到后端服务，请确保后端正在运行在 http://localhost:8000")
    except Exception as e:
        print(f"❌ 测试过程中发生错误: {e}")
        import traceback
        traceback.print_exc()
