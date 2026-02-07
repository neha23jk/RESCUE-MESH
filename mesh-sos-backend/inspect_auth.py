
from app.auth import verify_api_key
import inspect

print("Inspecting verify_api_key function:")
print(inspect.signature(verify_api_key))
print("Defaults:", verify_api_key.__defaults__)
print("Code location:", inspect.getfile(verify_api_key))
