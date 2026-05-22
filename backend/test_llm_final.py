"""
最终测试 LLM 配置
请确保您的 API Key 是有效的
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import requests

def test_llm():
    """测试 LLM 配置"""
    print("=" * 60)
    print("测试公司内部部署的 MiniMax 配置")
    print("=" * 60)
    
    # 配置信息
    base_url = "https://aicp.teamshub.com/openai/api/v1"
    api_key = "cb3a5cb469de1d0820d25a1e6349306dc4482f90"  # 请替换为有效的 API Key
    model_name = "minimax-m2.7"
    
    print(f"Base URL: {base_url}")
    print(f"Model: {model_name}")
    print(f"API Key: {'***' if api_key else '(空)'}")
    print()
    
    # 构建请求
    url = f"{base_url}/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    payload = {
        "model": model_name,
        "messages": [
            {"role": "user", "content": "你是谁"}
        ],
        "temperature": 0.1,
        "max_tokens": 200
    }
    
    print("发送请求...")
    print()
    
    try:
        response = requests.post(url, json=payload, headers=headers, timeout=60)
        
        print(f"状态码: {response.status_code}")
        print()
        
        if response.status_code == 200:
            result = response.json()
            
            # 检查错误格式1
            if result.get('flag') is False or result.get('resultCode'):
                print(f"❌ 错误: {result.get('message')}")
                print(f"   错误码: {result.get('resultCode')}")
                return
            
            # 检查错误格式2
            if result.get('code'):
                print(f"❌ 错误: {result.get('message')}")
                print(f"   错误码: {result.get('code')}")
                print()
                print("💡 提示: API Key 无效或已过期，请联系管理员获取新的 API Key")
                return
            
            # 检查响应内容
            content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
            
            if content:
                print("✅ 成功！")
                print("-" * 40)
                print(content)
                print("-" * 40)
            else:
                print("⚠️  响应内容为空")
                print(f"完整响应: {result}")
                
        else:
            print(f"❌ HTTP 错误: {response.status_code}")
            try:
                error_data = response.json()
                print(f"错误信息: {error_data}")
            except:
                print(f"响应内容: {response.text}")
                
    except Exception as e:
        print(f"❌ 请求异常: {e}")

if __name__ == "__main__":
    test_llm()
    print("\n" + "=" * 60)
