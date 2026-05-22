"""
模拟工作流编辑器调用 LLM 节点
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import requests
import json

def test_workflow_llm():
    """模拟工作流编辑器调用 LLM 节点"""
    print("=" * 60)
    print("模拟工作流编辑器调用 LLM 节点")
    print("=" * 60)
    
    # 工作流编辑器调用的 API
    url = "http://localhost:8000/api/v1/chat/completion"
    
    # 模拟工作流节点配置
    payload = {
        "model": "custom-minimax-m2.7",  # 前端传递的模型名称
        "prompt": "你是谁",
        "system_prompt": "",
        "temperature": 0.1,
        "max_tokens": 1024,
        "top_k": 0,
        "top_p": 1.0
    }
    
    print(f"API URL: {url}")
    print(f"模型: {payload['model']}")
    print(f"提示词: {payload['prompt']}")
    print()
    
    try:
        response = requests.post(url, json=payload, timeout=60)
        
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            
            print(f"成功: {result.get('success')}")
            print(f"模拟响应: {result.get('simulated', False)}")
            
            if result.get('success'):
                response_text = result.get('result', '')
                print(f"\n响应结果 ({len(response_text)} 字符):")
                print("-" * 60)
                print(response_text)
                print("-" * 60)
                
                if "这是模拟的 LLM 响应" in response_text:
                    print("\n❌ 警告：返回模拟响应")
                else:
                    print("\n✅ 成功！已调用真实的 MiniMax 模型")
            else:
                print(f"\n❌ 失败: {result.get('message', 'Unknown error')}")
                
        else:
            print(f"❌ HTTP {response.status_code}")
            try:
                error_data = response.json()
                print(f"错误信息: {error_data}")
            except:
                print(f"响应内容: {response.text}")
                
    except Exception as e:
        print(f"❌ 请求异常: {e}")
        print("⚠️  请确保后端服务已启动")

if __name__ == "__main__":
    test_workflow_llm()
    print("\n" + "=" * 60)
