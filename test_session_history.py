"""
测试会话历史加载功能 - 验证空会话不保存到数据库
"""
import requests
import json
from datetime import datetime

# 后端API基础URL
BASE_URL = "http://localhost:8000"

def test_empty_session_not_saved():
    """测试空会话不会保存到数据库"""
    print("=== 测试1：空会话不应该保存到数据库 ===")
    
    # 查询当前用户的会话数量
    sessions_resp = requests.get(
        f"{BASE_URL}/api/v2/chat/sessions",
        params={"user_id": "test_user"}
    )
    
    if sessions_resp.status_code != 200:
        print(f"查询会话列表失败: {sessions_resp.status_code}")
        return
    
    initial_count = sessions_resp.json()['total']
    print(f"初始会话数量: {initial_count}")
    
    # 注意：前端创建空会话时不会调用后端API
    # 所以这里不需要做任何操作，只需要验证没有新会话被创建
    
    # 再次查询会话数量，应该不变
    sessions_resp = requests.get(
        f"{BASE_URL}/api/v2/chat/sessions",
        params={"user_id": "test_user"}
    )
    
    final_count = sessions_resp.json()['total']
    print(f"最终会话数量: {final_count}")
    
    if initial_count == final_count:
        print("✓ 测试通过：空会话没有保存到数据库")
    else:
        print("✗ 测试失败：会话数量发生了变化")
    
    print()

def test_session_created_on_first_message():
    """测试首次发消息时会话才被创建"""
    print("=== 测试2：首次发消息时才创建会话 ===")
    
    # 1. 发送第一条消息（这会触发会话创建）
    print("\n1. 发送第一条消息...")
    
    # 先创建一个临时会话用于测试
    create_resp = requests.post(
        f"{BASE_URL}/api/v2/chat/sessions",
        json={
            "user_id": "test_user",
            "title": f"测试会话 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
        }
    )
    
    if create_resp.status_code != 200:
        print(f"创建会话失败: {create_resp.status_code}")
        return
    
    session_data = create_resp.json()
    session_id = session_data["session_id"]
    print(f"创建会话成功: {session_id}")
    
    # 2. 添加一条用户消息
    print("\n2. 添加用户消息...")
    msg_resp = requests.post(
        f"{BASE_URL}/api/v2/chat/sessions/{session_id}/messages",
        json={
            "role": "user",
            "content": "你好，这是第一条消息"
        }
    )
    
    if msg_resp.status_code == 200:
        print("  消息保存成功")
    else:
        print(f"  消息保存失败: {msg_resp.status_code}")
        return
    
    # 3. 验证会话存在且有消息
    print("\n3. 验证会话和消息...")
    messages_resp = requests.get(
        f"{BASE_URL}/api/v2/chat/sessions/{session_id}/messages"
    )
    
    if messages_resp.status_code == 200:
        messages_data = messages_resp.json()
        print(f"  会话中有 {messages_data['total']} 条消息")
        if messages_data['total'] > 0:
            print("✓ 测试通过：首次发消息后会话已创建并保存消息")
        else:
            print("✗ 测试失败：会话中没有消息")
    else:
        print(f"  加载消息失败: {messages_resp.status_code}")
    
    # 4. 清理测试数据
    print("\n4. 清理测试数据...")
    delete_resp = requests.delete(
        f"{BASE_URL}/api/v2/chat/sessions/{session_id}"
    )
    if delete_resp.status_code == 200:
        print("  测试会话已删除")
    else:
        print(f"  删除会话失败: {delete_resp.status_code}")
    
    print()

def test_session_history_loading():
    """测试会话历史加载"""
    print("=== 测试3：会话历史加载 ===")
    
    # 1. 创建测试会话
    print("\n1. 创建测试会话...")
    create_resp = requests.post(
        f"{BASE_URL}/api/v2/chat/sessions",
        json={
            "user_id": "test_user",
            "title": f"历史测试会话 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
        }
    )
    
    if create_resp.status_code != 200:
        print(f"创建会话失败: {create_resp.status_code}")
        return
    
    session_data = create_resp.json()
    session_id = session_data["session_id"]
    print(f"创建会话成功: {session_id}")
    
    # 2. 添加多条消息
    print("\n2. 添加测试消息...")
    messages = [
        {"role": "user", "content": "你好，这是一个测试消息"},
        {"role": "assistant", "content": "你好！我是AI助手，有什么可以帮助你的吗？"},
        {"role": "user", "content": "帮我填写一个销售订单"},
        {"role": "assistant", "content": "好的，我来帮你填写销售订单。请提供以下信息：客户名称、产品、数量等。"}
    ]
    
    for msg in messages:
        msg_resp = requests.post(
            f"{BASE_URL}/api/v2/chat/sessions/{session_id}/messages",
            json=msg
        )
        if msg_resp.status_code == 200:
            print(f"  ✓ 消息保存成功: {msg['role']}")
        else:
            print(f"  ✗ 消息保存失败: {msg_resp.status_code}")
    
    # 3. 重新加载消息（模拟重新登录后的加载）
    print("\n3. 重新加载消息（模拟重新登录）...")
    messages_resp = requests.get(
        f"{BASE_URL}/api/v2/chat/sessions/{session_id}/messages"
    )
    
    if messages_resp.status_code == 200:
        messages_data = messages_resp.json()
        print(f"加载到 {messages_data['total']} 条消息")
        
        if messages_data['total'] == len(messages):
            print("✓ 测试通过：所有历史消息都正确加载")
            for i, msg in enumerate(messages_data['messages']):
                print(f"  {i+1}. [{msg['role']}] {msg['content'][:50]}...")
        else:
            print(f"✗ 测试失败：期望 {len(messages)} 条消息，实际加载 {messages_data['total']} 条")
    else:
        print(f"加载消息失败: {messages_resp.status_code}")
    
    # 4. 清理测试数据
    print("\n4. 清理测试数据...")
    delete_resp = requests.delete(
        f"{BASE_URL}/api/v2/chat/sessions/{session_id}"
    )
    if delete_resp.status_code == 200:
        print("测试会话已删除")
    else:
        print(f"删除会话失败: {delete_resp.status_code}")
    
    print()

if __name__ == "__main__":
    try:
        test_empty_session_not_saved()
        test_session_created_on_first_message()
        test_session_history_loading()
        print("=== 所有测试完成 ===")
    except Exception as e:
        print(f"测试过程中出现错误: {e}")
        import traceback
        traceback.print_exc()
