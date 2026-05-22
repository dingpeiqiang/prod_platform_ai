"""
测试 LLM Completion API
验证工作流编辑器的 LLM 节点是否能正常调用真实模型
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import logging
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s.%(msecs)03d [%(levelname)-8s] %(name)-20s: %(message)s',
    datefmt='%H:%M:%S'
)

import requests

def test_llm_completion():
    """测试 /api/v1/chat/completion API"""
    print("=" * 60)
    print("测试 LLM Completion API")
    print("=" * 60)
    
    # 模拟工作流编辑器调用
    url = "http://localhost:8000/api/v1/chat/completion"
    
    payload = {
        "model": "custom-minimax-m2.7",
        "prompt": "你是谁",
        "system_prompt": "",
        "temperature": 0.1,
        "max_tokens": 1024,
        "top_k": 0,
        "top_p": 1.0
    }
    
    print(f"请求 URL: {url}")
    print(f"模型: {payload['model']}")
    print(f"提示词: {payload['prompt']}")
    print()
    
    try:
        response = requests.post(url, json=payload)
        response.raise_for_status()
        
        data = response.json()
        
        print(f"状态码: {response.status_code}")
        print(f"成功: {data.get('success')}")
        print(f"模拟响应: {data.get('simulated', False)}")
        
        if 'message' in data:
            print(f"消息: {data['message']}")
        
        if data.get('success'):
            result = data.get('result', '')
            print(f"\n响应结果 ({len(result)} 字符):")
            print("-" * 60)
            print(result)
            print("-" * 60)
            
            # 检查是否是模拟响应
            if "这是模拟的 LLM 响应" in result:
                print("\n❌ 警告：仍然返回模拟响应，请检查配置")
            else:
                print("\n✅ 成功！已调用真实的 LLM 模型")
        
    except requests.exceptions.RequestException as e:
        print(f"❌ 请求失败: {e}")
        if response:
            try:
                error_data = response.json()
                print(f"错误详情: {error_data}")
            except:
                print(f"响应内容: {response.text}")

if __name__ == "__main__":
    test_llm_completion()
    print("=" * 60)
