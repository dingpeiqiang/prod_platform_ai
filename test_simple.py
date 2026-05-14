import requests
import time

# 测试可视化 API
print("Testing visualization API...")
start = time.time()
try:
    response = requests.get("http://localhost:8000/api/visualization/traces?limit=20", timeout=10)
    print(f"Response: {response.status_code}")
    print(f"Content: {response.text[:500]}")
except requests.exceptions.Timeout:
    print("Request timed out!")
except Exception as e:
    print(f"Error: {e}")
print(f"Time taken: {time.time() - start:.2f}s")