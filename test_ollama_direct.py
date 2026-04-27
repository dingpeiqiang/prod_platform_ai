"""
直接测试 Ollama API 调用
"""
import requests
import json


def test_ollama_direct():
    """直接测试 Ollama API"""
    print("=" * 60)
    print("直接测试 Ollama API")
    print("=" * 60)
    
    base_url = "http://localhost:11434"
    model = "qwen2.5:14b-instruct-q4_K_M"
    
    # 测试 1: 检查服务是否运行
    print("\n测试 1: 检查 Ollama 服务")
    try:
        response = requests.get(f"{base_url}/api/tags", timeout=5)
        if response.status_code == 200:
            data = response.json()
            models = data.get('models', [])
            print(f"✅ Ollama 服务正常运行")
            print(f"   可用模型数量: {len(models)}")
            for m in models:
                print(f"   - {m['name']}")
            
            # 检查目标模型是否存在
            model_exists = any(m['name'] == model for m in models)
            if model_exists:
                print(f"✅ 目标模型 {model} 已加载")
            else:
                print(f"⚠️  目标模型 {model} 未找到")
                return False
        else:
            print(f"❌ Ollama 服务异常: {response.status_code}")
            return False
    except Exception as e:
        print(f"❌ 无法连接到 Ollama 服务: {e}")
        print("   请确保 Ollama 正在运行: ollama serve")
        return False
    
    # 测试 2: 测试聊天 API
    print("\n测试 2: 测试聊天 API")
    try:
        url = f"{base_url}/api/chat"
        payload = {
            "model": model,
            "messages": [
                {"role": "user", "content": "你好，请简单介绍一下自己"}
            ],
            "stream": False,
            "options": {
                "temperature": 0.3,
                "num_predict": 100
            }
        }
        
        print(f"请求 URL: {url}")
        print(f"请求模型: {model}")
        print(f"请求内容: {json.dumps(payload, ensure_ascii=False, indent=2)}")
        
        response = requests.post(url, json=payload, timeout=60)
        
        print(f"\n响应状态码: {response.status_code}")
        
        if response.status_code != 200:
            print(f"❌ API 调用失败: {response.status_code}")
            print(f"响应内容: {response.text}")
            return False
        
        result = response.json()
        content = result.get('message', {}).get('content', '')
        
        print(f"✅ API 调用成功")
        print(f"响应内容长度: {len(content)}")
        print(f"响应预览: {content[:100]}...")
        
        return True
        
    except Exception as e:
        print(f"❌ API 调用异常: {e}")
        import traceback
        traceback.print_exc()
        return False


if __name__ == "__main__":
    success = test_ollama_direct()
    print("\n" + "=" * 60)
    if success:
        print("✅ 所有测试通过！Ollama 集成正常")
    else:
        print("❌ 测试失败，请检查配置")
    print("=" * 60)
