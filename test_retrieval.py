
import urllib.request
import json

url = "http://127.0.0.1:8001/api/v1/active-sos?hours=24&limit=10"

print(f"Fetching from: {url}")

try:
    with urllib.request.urlopen(url) as response:
        print(f"Status Code: {response.getcode()}")
        data = json.loads(response.read().decode('utf-8'))
        print(f"Response: {json.dumps(data, indent=2)}")
except Exception as e:
    print(f"Error: {e}")
