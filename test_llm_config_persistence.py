"""
测试 LLM 配置保存和加载功能
"""
import requests
import json

BASE_URL = "http://localhost:6173"

def test_save_config():
    """测试保存配置"""
    print("=" * 60)
    print("测试 1: 保存 LLM 配置")
    print("=" * 60)
    
    user_id = "test-user-002"
    config_data = {
        "user_identifier": user_id,
        "provider": "custom",
        "model": "minimax-m2.7",
        "api_key": "cb3a5cb469de1d0820d25a1e6349306dc4482f90",  # 使用下划线命名
        "base_url": "https://aicp.teamshub.com/openai/api/v1/openai/v1",  # 使用下划线命名
        "temperature": 0.3,
        "max_tokens": 2048,  # 使用下划线命名
        "thinking": False
    }
    
    response = requests.post(
        f"{BASE_URL}/api/v1/llm-config/save",
        json=config_data,
        headers={"Content-Type": "application/json"}
    )
    
    print(f"状态码: {response.status_code}")
    result = response.json()
    print(f"响应: {json.dumps(result, indent=2, ensure_ascii=False)}")
    
    if result.get("success"):
        print("✓ 保存成功")
        return True
    else:
        print("✗ 保存失败")
        return False


def test_get_active_config():
    """测试获取激活配置"""
    print("\n" + "=" * 60)
    print("测试 2: 获取激活配置")
    print("=" * 60)
    
    user_id = "test-user-002"
    
    response = requests.get(f"{BASE_URL}/api/v1/llm-config/active/{user_id}")
    
    print(f"状态码: {response.status_code}")
    result = response.json()
    print(f"响应: {json.dumps(result, indent=2, ensure_ascii=False)}")
    
    if result.get("success") and result.get("config"):
        print("✓ 获取成功")
        print(f"  - Provider: {result['config']['provider']}")
        print(f"  - Model: {result['config']['model']}")
        print(f"  - Base URL: {result['config']['base_url']}")
        return True
    else:
        print("✗ 获取失败或未找到配置")
        return False


def test_list_configs():
    """测试列出所有配置"""
    print("\n" + "=" * 60)
    print("测试 3: 列出用户的所有配置")
    print("=" * 60)
    
    user_id = "test-user-002"
    
    response = requests.get(f"{BASE_URL}/api/v1/llm-config/list/{user_id}?limit=10")
    
    print(f"状态码: {response.status_code}")
    result = response.json()
    print(f"响应: {json.dumps(result, indent=2, ensure_ascii=False)}")
    
    if result.get("success") and result.get("configs"):
        print(f"✓ 获取到 {len(result['configs'])} 个配置")
        for i, config in enumerate(result['configs'], 1):
            print(f"  {i}. Model: {config['model']}, Active: {config['is_active']}")
        return True
    else:
        print("✗ 获取失败或没有配置")
        return False


def main():
    print("\n开始测试 LLM 配置数据库持久化功能\n")
    
    # 测试保存
    save_success = test_save_config()
    
    if not save_success:
        print("\n⚠️ 保存测试失败，跳过后续测试")
        return
    
    # 测试获取激活配置
    get_success = test_get_active_config()
    
    # 测试列出配置
    list_success = test_list_configs()
    
    print("\n" + "=" * 60)
    print("测试总结")
    print("=" * 60)
    print(f"保存配置: {'✓ 通过' if save_success else '✗ 失败'}")
    print(f"获取激活配置: {'✓ 通过' if get_success else '✗ 失败'}")
    print(f"列出配置: {'✓ 通过' if list_success else '✗ 失败'}")
    
    if all([save_success, get_success, list_success]):
        print("\n🎉 所有测试通过！")
    else:
        print("\n⚠️ 部分测试失败，请检查后端日志")


if __name__ == "__main__":
    main()
