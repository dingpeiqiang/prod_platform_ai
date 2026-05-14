import requests

# 先调用健康检查来初始化 tracer
print("Calling health check to initialize tracer...")
response = requests.get("http://localhost:8000/api/v1/langchain/health")
print(f"Health check response: {response.status_code}")
print(f"Content: {response.text}")

# 调用意图识别
print("\nCalling intent recognition...")
response = requests.post(
    "http://localhost:8000/api/v1/langchain/intent/recognize",
    json={"user_input": "测试追踪功能"}
)
print(f"Intent recognition response: {response.status_code}")
print(f"Content: {response.text}")

# 再次调用健康检查查看 tracer 统计
print("\nChecking tracer stats again...")
response = requests.get("http://localhost:8000/api/v1/langchain/health")
print(f"Health check response: {response.status_code}")
print(f"Content: {response.text}")