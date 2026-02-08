
import urllib.request
import urllib.parse
import json
import uuid
from datetime import datetime

url = "http://127.0.0.1:8001/api/v1/upload-sos"

payload = {
    "sos_id": str(uuid.uuid4()),
    "device_id": "debug-script-device",
    "timestamp": datetime.utcnow().isoformat(),
    "latitude": 28.7041,
    "longitude": 77.1025,
    "emergency_type": "MEDICAL",
    "optional_message": "Debug test packet via urllib",
    "hop_count": 0,
    "ttl": 10,
    "battery_percentage": 95
}

print(f"Sending payload: {json.dumps(payload, indent=2)}")

try:
    data = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req) as response:
        print(f"Status Code: {response.getcode()}")
        print(f"Response: {response.read().decode('utf-8')}")
except urllib.error.HTTPError as e:
    print(f"HTTPError: {e.code} - {e.read().decode('utf-8')}")
except Exception as e:
    print(f"Error: {e}")
