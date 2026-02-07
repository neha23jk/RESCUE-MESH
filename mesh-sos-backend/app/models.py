"""
Pydantic and SQLAlchemy models for SOS packets
"""
from datetime import datetime
from enum import Enum
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field, field_validator
from sqlalchemy import Column, String, Float, Integer, DateTime, Enum as SQLEnum, Text, Boolean
from sqlalchemy.dialects.postgresql import UUID as PGUUID

from .database import Base


# ============ Enums ============

class EmergencyType(str, Enum):
    """Type of emergency"""
    MEDICAL = "MEDICAL"
    FIRE = "FIRE"
    FLOOD = "FLOOD"
    EARTHQUAKE = "EARTHQUAKE"
    GENERAL = "GENERAL"


class DeliveryStatus(str, Enum):
    """Delivery status of SOS packet"""
    PENDING = "PENDING"
    RELAYED = "RELAYED"
    DELIVERED = "DELIVERED"
    RESPONDED = "RESPONDED"


# ============ SQLAlchemy Models ============

class SosPacketDB(Base):
    """SQLAlchemy model for SOS packets in database"""
    __tablename__ = "sos_packets"
    
    # Primary key
    sos_id = Column(PGUUID(as_uuid=True), primary_key=True)
    
    # Device info (hashed for privacy)
    device_id = Column(String(64), nullable=False, index=True)
    
    # Timestamp
    timestamp = Column(DateTime, nullable=False)
    
    # Location
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    accuracy = Column(Float, nullable=True)
    
    # Emergency details
    emergency_type = Column(SQLEnum(EmergencyType), nullable=False, default=EmergencyType.GENERAL)
    optional_message = Column(Text, nullable=True)
    
    # Battery info
    battery_percentage = Column(Integer, nullable=True)
    
    # Mesh routing info
    hop_count = Column(Integer, nullable=False, default=0)
    ttl = Column(Integer, nullable=False, default=10)
    
    # Security
    signature = Column(String(64), nullable=True)
    
    # Server-side tracking
    status = Column(SQLEnum(DeliveryStatus), nullable=False, default=DeliveryStatus.DELIVERED)
    received_at = Column(DateTime, nullable=False, default=datetime.utcnow)
    responded_at = Column(DateTime, nullable=True)
    responder_id = Column(String(64), nullable=True)
    
    # Relay tracking
    uploaded_by_device_id = Column(String(64), nullable=True)


# ============ Pydantic Schemas ============

class SosPacketCreate(BaseModel):
    """Schema for creating/uploading SOS packet"""
    sos_id: UUID
    device_id: str = Field(..., min_length=1, max_length=128)
    timestamp: datetime
    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)
    accuracy: Optional[float] = None
    emergency_type: EmergencyType = EmergencyType.GENERAL
    optional_message: Optional[str] = Field(None, max_length=500)
    battery_percentage: Optional[int] = Field(None, ge=0, le=100)
    hop_count: int = Field(0, ge=0, le=100)
    ttl: int = Field(10, ge=0, le=100)
    signature: Optional[str] = Field(None, max_length=128)
    
    @field_validator('emergency_type', mode='before')
    @classmethod
    def validate_emergency_type(cls, v):
        """Accept both uppercase and lowercase emergency types"""
        if isinstance(v, str):
            return v.upper()
        return v


class SosPacketResponse(BaseModel):
    """Schema for SOS packet in responses"""
    sos_id: UUID
    device_id: str
    timestamp: datetime
    latitude: float
    longitude: float
    accuracy: Optional[float] = None
    emergency_type: EmergencyType
    optional_message: Optional[str] = None
    battery_percentage: Optional[int] = None
    hop_count: int
    ttl: int
    status: DeliveryStatus
    received_at: datetime
    responded_at: Optional[datetime] = None
    
    model_config = {
        "from_attributes": True,
        "json_encoders": {},
    }
    
    def model_dump(self, **kwargs):
        """Override to exclude None values by default"""
        kwargs.setdefault('exclude_none', True)
        return super().model_dump(**kwargs)


class MarkRespondedRequest(BaseModel):
    """Schema for marking SOS as responded"""
    sos_id: UUID
    responder_id: str = Field(..., min_length=1, max_length=64)


class UploadResponse(BaseModel):
    """Response for successful upload"""
    success: bool
    sos_id: UUID
    message: str


class ActiveSosResponse(BaseModel):
    """Response containing list of active SOS packets"""
    count: int
    sos_packets: list[SosPacketResponse]
