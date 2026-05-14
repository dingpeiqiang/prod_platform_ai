import requests
import json

# 测试意图识别 API
print("Testing intent recognition API...")
response = requests.post(
    "http://localhost:8000/api/v1/langchain/intent/recognize",
    json={"user_input": "测试追踪功能"}
)
print(f"Response: {response.status_code}")
print(f"Content: {response.text}")

# 测试可视化 API
print("\nTesting visualization API...")
response = requests.get("http://localhost:8000/api/visualization/traces?limit=20")
print(f"Response: {response.status_code}")
print(f"Content: {response.text}")

# 测试统计 API
print("\nTesting stats API...")
response = requests.get("http://localhost:8000/api/visualization/stats")
print(f"Response: {response.status_code}")
print(f"Content: {response.text}")