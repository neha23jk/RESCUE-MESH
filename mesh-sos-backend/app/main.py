"""
MeshSOS Backend - FastAPI Application
"""
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .database import init_db
from .routes import sos


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan handler"""
    print("🚀 Starting MeshSOS Backend...")
    init_db()
    print("✅ Database initialized")
    yield
    print("👋 Shutting down...")


app = FastAPI(
    title="MeshSOS Backend",
    description="Backend API for Offline Emergency Mesh SOS Network",
    version="1.0.0",
    lifespan=lifespan
)

# CORS - Allow all origins for development
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routes
app.include_router(sos.router)


@app.get("/")
async def root():
    """Health check"""
    return {
        "service": "MeshSOS Backend",
        "status": "healthy",
        "version": "1.0.0"
    }


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "ok"}
