import requests
import time

# 1. 执行意图识别，生成追踪数据
print("1. 执行意图识别...")
response = requests.post(
    "http://localhost:8000/api/v1/langchain/intent/recognize",
    json={"user_input": "测试数据库持久化追踪功能"}
)
print(f"Response: {response.status_code}")
print(f"Content: {response.text}")

# 等待一下让异步保存完成
time.sleep(1)

# 2. 查询追踪列表
print("\n2. 查询追踪列表...")
response = requests.get("http://localhost:8000/api/visualization/traces?limit=20")
print(f"Response: {response.status_code}")
print(f"Content: {response.text}")

# 3. 查询统计信息
print("\n3. 查询统计信息...")
response = requests.get("http://localhost:8000/api/visualization/stats")
print(f"Response: {response.status_code}")
print(f"Content: {response.text}")