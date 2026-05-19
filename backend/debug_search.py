"""
调试搜索问题
"""

import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.harness.memory.vector_store import VectorStore, EmbeddingsManager


def debug_search():
    print("=" * 60)
    print("搜索问题调试")
    print("=" * 60)
    
    # 创建新的存储实例
    vs = VectorStore(similarity_threshold=0.1)
    
    # 清空现有数据
    vs._store = {}
    vs._session_index = {}
    
    # 添加测试文档
    print("\n1. 添加测试文档")
    docs = [
        {"title": "报销政策", "content": "企业报销政策说明：员工出差报销需提供正规发票，住宿标准为每天不超过300元。"},
        {"title": "年假规定", "content": "年假规定：员工入职满一年后可享受5天年假，每增加一年工龄增加1天。"},
        {"title": "请假流程", "content": "请假流程：员工需提前三天提交请假申请，经直属领导审批后方可休假。"},
    ]
    
    for doc in docs:
        entry_id = vs.add(
            content=doc["content"],
            metadata={"title": doc["title"], "source": "测试数据"}
        )
        print(f"   添加: {doc['title']} -> ID: {entry_id}")
    
    # 检查文档是否有向量
    print("\n2. 检查文档向量")
    for entry_id, entry in vs._store.items():
        has_vector = entry.vector is not None and len(entry.vector) > 0
        vector_len = len(entry.vector) if entry.vector else 0
        print(f"   {entry.metadata.get('title', '未命名')}: 向量存在={has_vector}, 维度={vector_len}")
    
    # 测试相似度计算
    print("\n3. 测试向量相似度")
    query = "报销需要什么"
    query_vector = vs.embeddings.embed(query)
    print(f"   查询: '{query}'")
    
    for entry_id, entry in vs._store.items():
        if entry.vector:
            similarity = vs._cosine_similarity(query_vector, entry.vector)
            print(f"   - {entry.metadata.get('title')}: 相似度={similarity:.4f}")
        else:
            print(f"   - {entry.metadata.get('title')}: 无向量")
    
    # 执行搜索
    print("\n4. 执行搜索")
    results = vs.search(query, top_k=10, min_similarity=0.0)
    print(f"   搜索结果数量: {len(results)}")
    for entry, similarity in results:
        print(f"   - {entry.metadata.get('title')}: 相似度={similarity:.4f}")
    
    if len(results) == 0:
        print("\n❌ 搜索失败原因分析:")
        print("   1. 相似度阈值过高")
        print("   2. 向量生成失败")
        print("   3. 文档未正确添加")
    else:
        print("\n✅ 搜索成功！")


if __name__ == "__main__":
    debug_search()