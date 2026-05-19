"""
知识库功能测试脚本
运行方式: python test_kb.py
"""

import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.services.kb_service import get_kb_service


def test_kb_service():
    print("=" * 60)
    print("轻量知识库测试")
    print("=" * 60)
    
    kb = get_kb_service()
    
    print("\n1. 添加测试文档...")
    entry_id1 = kb.add_document(
        content="企业报销政策说明：员工出差报销需提供正规发票，住宿标准为每天不超过300元。交通费实报实销，但需提供行程单。报销流程需在OA系统提交申请。",
        title="报销政策",
        source="公司制度手册"
    )
    print(f"   已添加文档: {entry_id1}")
    
    entry_id2 = kb.add_document(
        content="年假规定：员工入职满一年后可享受5天年假，每增加一年工龄增加1天，最多15天。年假需提前两周申请，经部门主管审批后生效。",
        title="年假规定",
        source="人力资源手册"
    )
    print(f"   已添加文档: {entry_id2}")
    
    entry_id3 = kb.add_document(
        content="IT设备领用流程：新员工入职后可申请笔记本电脑和显示器。需填写《设备领用申请表》，经IT部门审核后发放。设备使用期限为3年，到期可申请更换。",
        title="设备领用",
        source="IT管理规范"
    )
    print(f"   已添加文档: {entry_id3}")
    
    print("\n2. 查看知识库统计...")
    stats = kb.get_stats()
    print(f"   文档总数: {stats['total_entries']}")
    print(f"   会话数: {stats['total_sessions']}")
    print(f"   使用率: {stats['utilization']:.1%}")
    
    print("\n3. 测试文档检索...")
    query = "报销需要什么"
    results = kb.search(query, top_k=2)
    print(f"   查询: {query}")
    for i, r in enumerate(results, 1):
        print(f"   [{i}] {r['title']} (相似度: {r['similarity']:.4f})")
        print(f"      {r['content'][:100]}...")
    
    print("\n4. 测试知识库问答...")
    query = "年假怎么申请"
    result = kb.qa(query)
    print(f"   查询: {query}")
    print(f"   答案: {result['answer']}")
    print(f"   置信度: {result['confidence']:.4f}")
    
    print("\n5. 获取文档详情...")
    doc = kb.get_document(entry_id1)
    print(f"   文档ID: {doc['id']}")
    print(f"   标题: {doc['title']}")
    print(f"   来源: {doc['source']}")
    print(f"   访问次数: {doc['access_count']}")
    
    print("\n6. 删除测试文档...")
    kb.delete_document(entry_id1)
    kb.delete_document(entry_id2)
    kb.delete_document(entry_id3)
    stats = kb.get_stats()
    print(f"   删除后文档总数: {stats['total_entries']}")
    
    print("\n" + "=" * 60)
    print("测试完成！")
    print("=" * 60)


if __name__ == "__main__":
    test_kb_service()