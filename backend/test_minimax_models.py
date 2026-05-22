"""
测试公司内部部署的 MiniMax 支持的模型列表
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import requests

def test_available_models():
    """测试获取可用模型列表"""
    print("=" * 60)
    print("测试公司内部部署的 MiniMax 模型")
    print("=" * 60)
    
    # 公司内部部署的 API 地址
    base_url = "https://aicp.teamshub.com/openai/api/v1"
    
    # 尝试获取模型列表
    url = f"{base_url}/models"
    api_key = "cb3a5cb469de1d0820d25a1e6349306dc4482f90"
    
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    print(f"请求 URL: {url}")
    print()
    
    try:
        response = requests.get(url, headers=headers)
        print(f"状态码: {response.status_code}")
        
        if response.status_code == 200:
            try:
                data = response.json()
                print("响应数据:")
                print(json.dumps(data, indent=2, ensure_ascii=False))
            except:
                print(f"响应内容: {response.text[:2000]}")
        else:
            print(f"请求失败")
            try:
                error_data = response.json()
                print(f"错误信息: {error_data}")
            except:
                print(f"响应内容: {response.text[:500]}")
                
    except Exception as e:
        print(f"请求异常: {e}")

def test_model_call():
    """测试调用模型"""
    print("\n" + "=" * 60)
    print("测试模型调用")
    print("=" * 60)
    
    base_url = "https://aicp.teamshub.com/openai/api/v1"
    api_key = "cb3a5cb469de1d0820d25a1e6349306dc4482f90"
    
    # 尝试不同的模型名称
    models_to_test = [
        "minimax-m2.7",
        "abab6.5-chat",
        "m2.7",
        "minimax-m2",
        "gpt-4",
        "qwen-plus"
    ]
    
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    for model_name in models_to_test:
        print(f"\n测试模型: {model_name}")
        print("-" * 40)
        
        url = f"{base_url}/chat/completions"
        payload = {
            "model": model_name,
            "messages": [
                {"role": "user", "content": "Hello"}
            ],
            "temperature": 0.1,
            "max_tokens": 100
        }
        
        try:
            response = requests.post(url, json=payload, headers=headers, timeout=30)
            
            if response.status_code == 200:
                data = response.json()
                
                # 检查是否有错误
                if data.get('flag') is False or data.get('resultCode'):
                    print(f"❌ 模型 {model_name}: {data.get('message', 'Unknown error')}")
                else:
                    content = data.get('choices', [{}])[0].get('message', {}).get('content', '')
                    if content:
                        print(f"✅ 模型 {model_name}: 调用成功")
                        print(f"   响应: {content[:100]}...")
                    else:
                        print(f"⚠️  模型 {model_name}: 响应为空")
            else:
                print(f"❌ 模型 {model_name}: HTTP {response.status_code}")
                
        except Exception as e:
            print(f"❌ 模型 {model_name}: {e}")

if __name__ == "__main__":
    import json
    test_available_models()
    test_model_call()
    print("\n" + "=" * 60)
