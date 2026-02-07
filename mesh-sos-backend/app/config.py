"""
Configuration settings for MeshSOS Backend
"""
from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    """Application settings loaded from environment variables"""
    
    # Database
    database_url: str = "postgresql://postgres:postgres@localhost:5432/meshsos"
    
    # Security
    api_key: str = "meshsos-dev-api-key-change-in-production"
    
    # App settings
    debug: bool = False
    
    # Rate limiting
    rate_limit_per_minute: int = 60
    sos_rate_limit_per_hour: int = 10
    
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


@lru_cache()
def get_settings() -> Settings:
    """Get cached settings instance"""
    return Settings()
