
import sys
import os
sys.path.append(os.getcwd())

from app.main import app
from fastapi.routing import APIRoute

print("Inspecting API Routes for Dependencies:")
for route in app.routes:
    if isinstance(route, APIRoute):
        print(f"Route: {route.path} [{route.methods}]")
        for dep in route.dependencies:
            print(f"  - Dependency: {dep.dependency}")
        if route.dependant.dependencies:
            for d in route.dependant.dependencies:
                 print(f"  - Dependant Dependency: {d.name} : {d.call}")

