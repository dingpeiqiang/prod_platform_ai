import requests
import time

# 1. 测试健康检查
print("1. 测试健康检查...")
response = requests.get("http://localhost:8000/api/v1/langchain/health", timeout=10)
print(f"Response: {response.status_code}")
print(f"Content: {response.text}")

# 等待一下
time.sleep(1)

# 2. 查询追踪列表
print("\n2. 查询追踪列表...")
response = requests.get("http://localhost:8000/api/visualization/traces?limit=20", timeout=10)
print(f"Response: {response.status_code}")
print(f"Content: {response.text}")

# 3. 查询统计信息
print("\n3. 查询统计信息...")
response = requests.get("http://localhost:8000/api/visualization/stats", timeout=10)
print(f"Response: {response.status_code}")
print(f"Content: {response.text}")