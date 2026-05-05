"""
测试历史数据导入API接口
"""
import requests
import json

BASE_URL = "http://localhost:8000"

def test_list_importable_forms():
    """测试获取可导入表单列表"""
    print("=" * 60)
    print("测试1: 获取可导入表单列表")
    print("=" * 60)
    
    try:
        response = requests.get(f"{BASE_URL}/api/v1/config/import/list")
        result = response.json()
        
        if result["success"]:
            print(f"✅ 成功获取 {len(result['forms'])} 个可导入表单\n")
            for form in result["forms"]:
                print(f"  - {form['formCode']:30s} ({form['formName']}) [{form['dataType']}]")
                if form.get('description'):
                    print(f"    描述: {form['description']}")
            return True
        else:
            print(f"❌ 失败: {result}")
            return False
    except Exception as e:
        print(f"❌ 异常: {e}")
        return False


def test_import_leave_data():
    """测试导入请假数据"""
    print("\n" + "=" * 60)
    print("测试2: 导入请假数据（限制5条）")
    print("=" * 60)
    
    try:
        response = requests.post(
            f"{BASE_URL}/api/v1/config/import/execute",
            json={
                "formCode": "leave",
                "limit": 5
            }
        )
        result = response.json()
        
        if result["success"]:
            print(f"✅ 导入成功!")
            print(f"\n📊 统计信息:")
            print(f"  源记录数:     {result['totalSource']}")
            print(f"  实际导入:     {result['totalImported']}")
            print(f"  跳过(空数据): {result['totalSkipped']}")
            print(f"  错误数:       {result['totalErrors']}")
            
            if result.get('fieldStats'):
                print(f"\n🔍 字段分布 (Top 5):")
                for stat in result['fieldStats'][:5]:
                    print(f"\n  {stat['fieldCode']} ({stat['distinctValues']} 种不同值):")
                    for item in stat['topValues'][:3]:
                        value_display = item['value'][:40] + '...' if len(item['value']) > 40 else item['value']
                        print(f"    {value_display}: {item['count']}次")
            
            if result.get('nestedFields'):
                print(f"\n📦 嵌套字段: {', '.join(result['nestedFields'])}")
            
            return True
        else:
            print(f"❌ 导入失败: {result['message']}")
            return False
    except Exception as e:
        print(f"❌ 异常: {e}")
        return False


def main():
    print("\n🧪 开始测试历史数据导入API\n")
    
    # 测试1: 获取表单列表
    test1_passed = test_list_importable_forms()
    
    # 测试2: 导入数据
    if test1_passed:
        test2_passed = test_import_leave_data()
        
        if test2_passed:
            print("\n" + "=" * 60)
            print("✅ 所有测试通过!")
            print("=" * 60)
            print("\n💡 提示:")
            print("  1. 打开浏览器访问前端应用")
            print("  2. 点击左侧边栏底部的 '📊 数据导入' 按钮")
            print("  3. 选择要导入的表单类型并执行导入")
            print("  4. 查看导入结果和字段分布统计")
        else:
            print("\n" + "=" * 60)
            print("⚠️  部分测试失败，请检查后端服务是否正常启动")
            print("=" * 60)
    else:
        print("\n" + "=" * 60)
        print("❌ 测试失败，请确保后端服务已启动")
        print("=" * 60)
        print("\n启动后端服务:")
        print("  cd backend")
        print("  python -m uvicorn app.main:app --reload --port 8000")


if __name__ == "__main__":
    main()
