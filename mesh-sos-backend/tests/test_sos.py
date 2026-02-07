"""
Tests for SOS API endpoints
"""
import pytest
from datetime import datetime, timedelta
from uuid import uuid4
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.main import app
from app.database import Base, get_db
from app.models import EmergencyType

# Create in-memory SQLite database for testing
SQLALCHEMY_DATABASE_URL = "sqlite:///:memory:"
engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def override_get_db():
    """Override database dependency for testing"""
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


# Override dependencies
app.dependency_overrides[get_db] = override_get_db

# Test client
client = TestClient(app)

# Test API key
TEST_API_KEY = "meshsos-dev-api-key-change-in-production"
HEADERS = {"X-API-Key": TEST_API_KEY}


@pytest.fixture(autouse=True)
def setup_database():
    """Create tables before each test"""
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


def create_test_sos_packet():
    """Create a test SOS packet payload"""
    return {
        "sos_id": str(uuid4()),
        "device_id": "a" * 64,  # SHA-256 hash is 64 chars
        "timestamp": datetime.utcnow().isoformat(),
        "latitude": 37.7749,
        "longitude": -122.4194,
        "accuracy": 10.5,
        "emergency_type": EmergencyType.MEDICAL.value,
        "optional_message": "Need medical assistance",
        "battery_percentage": 75,
        "hop_count": 2,
        "ttl": 8,
        "signature": "b" * 64
    }


class TestHealthEndpoints:
    """Test health check endpoints"""
    
    def test_root(self):
        """Test root endpoint"""
        response = client.get("/")
        assert response.status_code == 200
        data = response.json()
        assert data["service"] == "MeshSOS Backend"
        assert data["status"] == "healthy"
    
    def test_health(self):
        """Test health endpoint"""
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json()["status"] == "ok"


class TestAuthentication:
    """Test API key authentication"""
    
    def test_missing_api_key(self):
        """Test request without API key"""
        response = client.get("/api/v1/active-sos")
        assert response.status_code == 422  # Missing header
    
    def test_invalid_api_key(self):
        """Test request with invalid API key"""
        response = client.get("/api/v1/active-sos", headers={"X-API-Key": "invalid"})
        assert response.status_code == 401
    
    def test_valid_api_key(self):
        """Test request with valid API key"""
        response = client.get("/api/v1/active-sos", headers=HEADERS)
        assert response.status_code == 200


class TestUploadSOS:
    """Test POST /upload-sos endpoint"""
    
    def test_upload_new_sos(self):
        """Test uploading a new SOS packet"""
        packet = create_test_sos_packet()
        response = client.post("/api/v1/upload-sos", json=packet, headers=HEADERS)
        
        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert data["sos_id"] == packet["sos_id"]
    
    def test_upload_duplicate_sos(self):
        """Test uploading duplicate SOS packet (should succeed with message)"""
        packet = create_test_sos_packet()
        
        # First upload
        response1 = client.post("/api/v1/upload-sos", json=packet, headers=HEADERS)
        assert response1.status_code == 200
        
        # Duplicate upload
        response2 = client.post("/api/v1/upload-sos", json=packet, headers=HEADERS)
        assert response2.status_code == 200
        assert "already exists" in response2.json()["message"]
    
    def test_upload_old_timestamp(self):
        """Test uploading SOS with old timestamp (replay attack prevention)"""
        packet = create_test_sos_packet()
        packet["timestamp"] = (datetime.utcnow() - timedelta(hours=2)).isoformat()
        
        response = client.post("/api/v1/upload-sos", json=packet, headers=HEADERS)
        assert response.status_code == 400
        assert "too old" in response.json()["detail"]
    
    def test_upload_invalid_coordinates(self):
        """Test uploading SOS with invalid coordinates"""
        packet = create_test_sos_packet()
        packet["latitude"] = 200  # Invalid
        
        response = client.post("/api/v1/upload-sos", json=packet, headers=HEADERS)
        assert response.status_code == 422  # Validation error


class TestActiveSOS:
    """Test GET /active-sos endpoint"""
    
    def test_empty_active_sos(self):
        """Test getting active SOS when none exist"""
        response = client.get("/api/v1/active-sos", headers=HEADERS)
        
        assert response.status_code == 200
        data = response.json()
        assert data["count"] == 0
        assert data["sos_packets"] == []
    
    def test_get_active_sos(self):
        """Test getting active SOS after upload"""
        packet = create_test_sos_packet()
        client.post("/api/v1/upload-sos", json=packet, headers=HEADERS)
        
        response = client.get("/api/v1/active-sos", headers=HEADERS)
        
        assert response.status_code == 200
        data = response.json()
        assert data["count"] == 1
        assert data["sos_packets"][0]["sos_id"] == packet["sos_id"]
    
    def test_filter_by_emergency_type(self):
        """Test filtering active SOS by emergency type"""
        # Upload medical emergency
        medical = create_test_sos_packet()
        medical["emergency_type"] = EmergencyType.MEDICAL.value
        client.post("/api/v1/upload-sos", json=medical, headers=HEADERS)
        
        # Upload fire emergency
        fire = create_test_sos_packet()
        fire["emergency_type"] = EmergencyType.FIRE.value
        client.post("/api/v1/upload-sos", json=fire, headers=HEADERS)
        
        # Filter by medical
        response = client.get(
            "/api/v1/active-sos",
            params={"emergency_type": "medical"},
            headers=HEADERS
        )
        
        assert response.status_code == 200
        data = response.json()
        assert data["count"] == 1
        assert data["sos_packets"][0]["emergency_type"] == "medical"


class TestMarkResponded:
    """Test POST /mark-responded endpoint"""
    
    def test_mark_responded(self):
        """Test marking SOS as responded"""
        packet = create_test_sos_packet()
        client.post("/api/v1/upload-sos", json=packet, headers=HEADERS)
        
        response = client.post(
            "/api/v1/mark-responded",
            json={
                "sos_id": packet["sos_id"],
                "responder_id": "responder123"
            },
            headers=HEADERS
        )
        
        assert response.status_code == 200
        assert response.json()["success"] is True
        
        # Verify no longer in active list
        active = client.get("/api/v1/active-sos", headers=HEADERS)
        assert active.json()["count"] == 0
    
    def test_mark_nonexistent_responded(self):
        """Test marking nonexistent SOS as responded"""
        response = client.post(
            "/api/v1/mark-responded",
            json={
                "sos_id": str(uuid4()),
                "responder_id": "responder123"
            },
            headers=HEADERS
        )
        
        assert response.status_code == 404


class TestGetSOSById:
    """Test GET /sos/{sos_id} endpoint"""
    
    def test_get_existing_sos(self):
        """Test getting existing SOS by ID"""
        packet = create_test_sos_packet()
        client.post("/api/v1/upload-sos", json=packet, headers=HEADERS)
        
        response = client.get(f"/api/v1/sos/{packet['sos_id']}", headers=HEADERS)
        
        assert response.status_code == 200
        data = response.json()
        assert data["sos_id"] == packet["sos_id"]
        assert data["latitude"] == packet["latitude"]
    
    def test_get_nonexistent_sos(self):
        """Test getting nonexistent SOS by ID"""
        response = client.get(f"/api/v1/sos/{uuid4()}", headers=HEADERS)
        assert response.status_code == 404
