"""
测试修复效果：
1. URL规范化修复（重复路径段自动去除）
2. 空响应诊断日志
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import json


def test_url_normalization():
    """测试 URL 规范化功能"""
    print("=" * 60)
    print("测试 URL 规范化修复")
    print("=" * 60)
    
    from app.services.llm.base import normalize_base_url
    
    test_cases = [
        ("https://aicp.teamshub.com/openai/api/v1/openai/v1", "https://aicp.teamshub.com/openai/api/v1"),
        ("https://api.example.com/v1/v1", "https://api.example.com/v1"),
        ("https://api.example.com/openai/openai/v1", "https://api.example.com/openai/v1"),
        ("https://api.example.com/api/v1/chat/completions", "https://api.example.com/api/v1"),
        ("https://api.example.com/v1", "https://api.example.com/v1"),
        ("  https://api.example.com/v1  ", "https://api.example.com/v1"),
        ("`https://api.example.com/v1`", "https://api.example.com/v1"),
    ]
    
    all_passed = True
    for input_url, expected in test_cases:
        result = normalize_base_url(input_url, 'test')
        status = "✅ PASS" if result == expected else "❌ FAIL"
        if result != expected:
            all_passed = False
        
        print(f"\n输入: {input_url}")
        print(f"期望: {expected}")
        print(f"实际: {result}")
        print(f"状态: {status}")
    
    print("\n" + "=" * 60)
    return all_passed


def test_provider_factory():
    """测试 Provider 工厂"""
    print("\n" + "=" * 60)
    print("测试 Provider 工厂")
    print("=" * 60)
    
    from app.services.llm.factory import ProviderFactory
    
    providers = ProviderFactory.get_supported_providers()
    print(f"支持的 Provider: {providers}")
    
    # 测试 custom provider 创建
    config = {
        'provider': 'custom',
        'model': 'test-model',
        'apiKey': 'test-api-key',
        'baseUrl': 'https://api.example.com/v1',
        'temperature': 0.3,
        'maxTokens': 2048
    }
    
    try:
        provider = ProviderFactory.create('custom', config)
        print(f"✅ Provider 创建成功: {provider.__class__.__name__}")
        print(f"   Model: {provider.model}")
        print(f"   Base URL: {provider.base_url}")
        return True
    except Exception as e:
        print(f"❌ Provider 创建失败: {e}")
        return False


def test_empty_response_diagnosis():
    """测试空响应诊断信息"""
    print("\n" + "=" * 60)
    print("测试空响应诊断信息")
    print("=" * 60)
    
    # 导入诊断函数
    from app.services.recommendations.batch_ai_inference import _diagnose_empty_response
    
    # 测试用例1：完整配置 + 标准提示词
    print("\n--- 测试用例1：完整配置 + 标准提示词 ---")
    model_config = {
        'provider': 'custom',
        'model': 'minimax-m2.7',
        'baseUrl': 'https://aicp.teamshub.com/openai/api/v1',
        'apiKey': 'test-key-123',
        'temperature': 0.3,
        'maxTokens': 2048
    }
    
    prompt1 = """你是一个智能表单助手。请根据以下信息推断表单字段值。

## 任务说明
- 表单编码: test_form

## 需要推断的字段
{"field1": "字段1描述", "field2": "字段2描述"}

## 输出格式
请输出 JSON 格式：
{
  "fields": {
    "field1": ["值1", "值2"],
    "field2": ["值1"]
  }
}

## 规则
1. 根据字段描述推断可能的值
2. 严格按照 JSON 格式输出
"""
    
    _diagnose_empty_response(prompt1, model_config)
    
    # 测试用例2：缺少 API Key + 空提示词（模拟错误场景）
    print("\n--- 测试用例2：缺少 API Key + 空提示词 ---")
    bad_config = {
        'provider': 'custom',
        'model': '',
        'baseUrl': '',
        'apiKey': ''
    }
    
    _diagnose_empty_response("", bad_config)
    
    print("\n✅ 诊断信息输出正常")
    return True


def test_cleanup():
    """测试清理后的代码结构"""
    print("\n" + "=" * 60)
    print("测试代码清理")
    print("=" * 60)
    
    import os
    
    llm_dir = os.path.join(os.path.dirname(__file__), 'app', 'services', 'llm')
    files = [f for f in os.listdir(llm_dir) if f.endswith('.py')]
    
    print(f"LLM 服务目录文件: {files}")
    
    # 检查已删除的文件
    deleted_files = [
        'minimax_provider.py',
        'anthropic_provider.py', 
        'ollama_provider.py',
        'websocket_provider.py'
    ]
    
    all_deleted = True
    for deleted in deleted_files:
        if deleted in files:
            print(f"❌ {deleted} 未删除")
            all_deleted = False
        else:
            print(f"✅ {deleted} 已删除")
    
    # 检查保留的文件
    kept_files = ['__init__.py', 'base.py', 'factory.py', 'openai_provider.py', 'provider.py']
    for kept in kept_files:
        if kept in files:
            print(f"✅ {kept} 保留")
        else:
            print(f"❌ {kept} 缺失")
            all_deleted = False
    
    return all_deleted


if __name__ == "__main__":
    print("\n" + "=" * 70)
    print("修复效果自测")
    print("=" * 70)
    
    results = []
    
    results.append(("URL 规范化", test_url_normalization()))
    results.append(("Provider 工厂", test_provider_factory()))
    results.append(("空响应诊断", test_empty_response_diagnosis()))
    results.append(("代码清理", test_cleanup()))
    
    print("\n" + "=" * 70)
    print("测试结果汇总")
    print("=" * 70)
    
    passed = sum(1 for _, r in results if r)
    total = len(results)
    
    for name, result in results:
        status = "✅ PASS" if result else "❌ FAIL"
        print(f"{name}: {status}")
    
    print(f"\n总结果: {passed}/{total} 通过")
    
    if passed == total:
        print("\n🎉 所有测试通过！")
    else:
        print("\n⚠️ 部分测试失败，请检查相关代码")