"""
测试 Ollama 集成的脚本
"""
import sys
import os

# 添加项目根目录到 Python 路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'backend'))

from app.services.llm_service import llm_service


def test_ollama_integration():
    """测试 Ollama 集成"""
    print("=" * 60)
    print("测试 Ollama 集成")
    print("=" * 60)
    
    # 检查 LLM 服务是否启用
    print(f"\nLLM 服务启用状态: {llm_service.enabled}")
    print(f"LLM 提供商: {llm_service.llm_config.get('provider', 'unknown')}")
    print(f"模型名称: {llm_service.llm_config.get('model', 'unknown')}")
    print(f"Base URL: {llm_service.llm_config.get('baseUrl', 'unknown')}")
    
    if not llm_service.enabled:
        print("\n⚠️  LLM 服务未启用，请检查配置")
        return False
    
    # 测试意图识别
    print("\n" + "=" * 60)
    print("测试 1: 意图识别")
    print("=" * 60)
    
    test_input = "我想申请请假，从明天开始请3天假"
    form_types = ["leave", "expense", "sales_order"]
    
    print(f"\n用户输入: {test_input}")
    print(f"表单类型: {form_types}")
    
    result = llm_service.recognize_intent(test_input, form_types)
    
    if result:
        print(f"\n✅ 意图识别成功:")
        print(f"   结果: {result}")
    else:
        print(f"\n❌ 意图识别失败")
        if llm_service.fallback_to_rules:
            print("   将使用规则引擎进行回退处理")
    
    # 测试字段提取
    print("\n" + "=" * 60)
    print("测试 2: 字段提取")
    print("=" * 60)
    
    test_input = "请假类型是病假，开始日期是2024-01-15，结束日期是2024-01-17"
    
    # 加载请假表单 schema
    import json
    from app.core.config_loader import config_loader
    
    ontology = config_loader.load_ontology('leave')
    if ontology:
        form_schema = {
            "fields": [
                {"name": field["code"], "type": field.get("type", "string")}
                for field in ontology.get("fields", [])
            ]
        }
        
        print(f"\n用户输入: {test_input}")
        print(f"表单 Schema: {json.dumps(form_schema, ensure_ascii=False, indent=2)}")
        
        result = llm_service.extract_fields(test_input, form_schema)
        
        if result:
            print(f"\n✅ 字段提取成功:")
            print(f"   结果: {result}")
        else:
            print(f"\n❌ 字段提取失败")
            if llm_service.fallback_to_rules:
                print("   将使用规则引擎进行回退处理")
    else:
        print("\n⚠️  无法加载 leave 本体，跳过字段提取测试")
    
    print("\n" + "=" * 60)
    print("测试完成")
    print("=" * 60)
    
    return True


if __name__ == "__main__":
    try:
        success = test_ollama_integration()
        sys.exit(0 if success else 1)
    except Exception as e:
        print(f"\n❌ 测试过程中发生错误: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
