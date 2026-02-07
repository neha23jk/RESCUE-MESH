"""
Authentication utilities - placeholder for future use

Currently, all endpoints are public for development/disaster response scenarios.
To add authentication in the future, implement the functions here.
"""
from typing import Optional
from fastapi import Header, HTTPException, status
from .config import get_settings

settings = get_settings()


# Authentication is currently disabled for public dashboard access
# To enable it in the future, add dependencies to route functions
