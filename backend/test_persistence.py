"""
测试向量存储持久化功能
"""

import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.harness.memory.vector_store import get_vector_store, VectorStore


def test_persistence():
    print("=" * 60)
    print("向量存储持久化测试")
    print("=" * 60)
    
    # 第一阶段：添加文档
    print("\n阶段1: 添加测试文档")
    vs = get_vector_store()
    
    entry_id1 = vs.add(
        content="企业报销政策说明：员工出差报销需提供正规发票，住宿标准为每天不超过300元。",
        metadata={"title": "报销政策", "source": "公司制度手册"},
        session_id="kb_global"
    )
    entry_id2 = vs.add(
        content="年假规定：员工入职满一年后可享受5天年假，每增加一年工龄增加1天。",
        metadata={"title": "年假规定", "source": "人力资源手册"},
        session_id="kb_global"
    )
    
    print(f"   添加文档1: {entry_id1}")
    print(f"   添加文档2: {entry_id2}")
    
    # 强制保存
    vs.save_to_disk()
    
    # 检查文件是否生成
    persist_path = vs.persist_path
    print(f"\n   持久化文件: {persist_path}")
    if os.path.exists(persist_path):
        file_size = os.path.getsize(persist_path)
        print(f"   文件大小: {file_size} 字节")
    else:
        print("   ❌ 文件未生成")
        return
    
    # 第二阶段：模拟重启，创建新实例
    print("\n阶段2: 模拟重启（创建新实例）")
    new_vs = VectorStore()  # 创建新实例，应该自动加载数据
    
    stats = new_vs.get_stats()
    print(f"   加载后文档总数: {stats['total_entries']}")
    
    if stats['total_entries'] == 2:
        print("   ✅ 持久化成功！重启后数据恢复")
    else:
        print(f"   ❌ 持久化失败！期望2个文档，实际{stats['total_entries']}个")
    
    # 查询测试
    print("\n阶段3: 验证数据完整性")
    results = new_vs.search("报销", top_k=10, session_id="kb_global")
    print(f"   查询结果数量: {len(results)}")
    for entry, similarity in results:
        print(f"