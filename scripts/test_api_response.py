"""
测试导入API返回的数据格式
"""
import requests
import json

BASE_URL = "http://localhost:8000"

def test_import_api_response():
    """测试导入API的响应格式"""
    print("=" * 80)
    print("测试导入API响应数据格式")
    print("=" * 80)
    
    try:
        # 执行导入（限制5条用于测试）
        response = requests.post(
            f"{BASE_URL}/api/v1/config/import/execute",
            json={
                "formCode": "leave",
                "limit": 5
            }
        )
        
        result = response.json()
        
        print(f"\n✅ API调用成功")
        print(f"\n📋 响应数据结构:")
        print(json.dumps(result, indent=2, ensure_ascii=False))
        
        print(f"\n🔍 关键字段检查:")
        print(f"  success: {result.get('success')}")
        print(f"  message: {result.get('message')}")
        print(f"  totalImported: {result.get('totalImported')}")
        print(f"  totalSource: {result.get('totalSource')}")
        print(f"  fieldStats 类型: {type(result.get('fieldStats'))}")
        print(f"  fieldStats 长度: {len(result.get('fieldStats', []))}")
        
        if result.get('fieldStats'):
            print(f"\n📊 字段统计详情:")
            for i, stat in enumerate(result['fieldStats'][:3]):
                print(f"\n  [{i+1}] {stat['fieldCode']}")
                print(f"      distinctValues: {stat['distinctValues']}")
                print(f"      topValues 数量: {len(stat['topValues'])}")
                if stat['topValues']:
                    print(f"      第一个值: {stat['topValues'][0]}")
        
        return True
        
    except Exception as e:
        print(f"\n❌ 测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False


if __name__ == "__main__":
    print("\n请确保后端服务已启动 (python -m uvicorn app.main:app --reload --port 8000)\n")
    input("按回车键开始测试...")
    test_import_api_response()
