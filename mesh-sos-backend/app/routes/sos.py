"""
SOS packet API routes - Clean implementation without API key requirements
"""
from datetime import datetime, timedelta
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.orm import Session
from sqlalchemy import and_

from ..database import get_db
from ..models import (
    SosPacketDB, 
    SosPacketCreate, 
    SosPacketResponse, 
    UploadResponse,
    ActiveSosResponse,
    MarkRespondedRequest,
    DeliveryStatus,
    EmergencyType
)

router = APIRouter(prefix="/api/v1", tags=["SOS"])


@router.post("/upload-sos", response_model=UploadResponse)
async def upload_sos(
    packet: SosPacketCreate,
    db: Session = Depends(get_db)
):
    """
    Upload an SOS packet from a mesh node with internet connectivity.
    
    This endpoint receives SOS packets that have been collected by the mesh
    network and uploads them to the central server for responder access.
    """
    import logging
    logger = logging.getLogger("uvicorn")
    logger.info(f"Received SOS packet: {packet.model_dump()}")
    
    # Check if packet already exists (deduplication)
    existing = db.query(SosPacketDB).filter(SosPacketDB.sos_id == packet.sos_id).first()
    
    if existing:
        # Packet already uploaded - this is normal in mesh network
        return UploadResponse(
            success=True,
            sos_id=packet.sos_id,
            message="SOS packet already exists in database"
        )
    
    # Validate timestamp (prevent replay attacks - max 24 hours for disaster scenarios)
    max_age = timedelta(hours=24)
    if datetime.utcnow() - packet.timestamp > max_age:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="SOS packet timestamp too old (>24 hours)"
        )
    
    # Create new packet record
    db_packet = SosPacketDB(
        sos_id=packet.sos_id,
        device_id=packet.device_id,
        timestamp=packet.timestamp,
        latitude=packet.latitude,
        longitude=packet.longitude,
        accuracy=packet.accuracy,
        emergency_type=packet.emergency_type,
        optional_message=packet.optional_message,
        battery_percentage=packet.battery_percentage,
        hop_count=packet.hop_count,
        ttl=packet.ttl,
        signature=packet.signature,
        status=DeliveryStatus.DELIVERED,
        received_at=datetime.utcnow()
    )
    
    db.add(db_packet)
    db.commit()
    db.refresh(db_packet)
    
    logger.info(f"SOS packet saved successfully: {packet.sos_id}")
    
    return UploadResponse(
        success=True,
        sos_id=packet.sos_id,
        message="SOS packet uploaded successfully"
    )


@router.get("/active-sos", response_model=ActiveSosResponse)
async def get_active_sos(
    db: Session = Depends(get_db),
    emergency_type: Optional[EmergencyType] = Query(None, description="Filter by emergency type"),
    hours: int = Query(24, ge=1, le=168, description="Get SOS from last N hours"),
    limit: int = Query(100, ge=1, le=500, description="Maximum number of results")
):
    """
    Get all active (non-responded) SOS packets.
    
    Returns SOS packets that have not been marked as responded,
    useful for responder dashboards.
    """
    # Calculate time threshold
    time_threshold = datetime.utcnow() - timedelta(hours=hours)
    
    # Build query
    query = db.query(SosPacketDB).filter(
        and_(
            SosPacketDB.status != DeliveryStatus.RESPONDED,
            SosPacketDB.received_at >= time_threshold
        )
    )
    
    # Apply emergency type filter if provided
    if emergency_type:
        query = query.filter(SosPacketDB.emergency_type == emergency_type)
    
    # Order by timestamp (newest first) and limit
    packets = query.order_by(SosPacketDB.timestamp.desc()).limit(limit).all()
    
    # Convert to response format
    response_packets = [
        SosPacketResponse.model_validate(p) for p in packets
    ]
    
    return ActiveSosResponse(
        count=len(response_packets),
        sos_packets=response_packets
    )


@router.post("/mark-responded", response_model=UploadResponse)
async def mark_responded(
    request: MarkRespondedRequest,
    db: Session = Depends(get_db)
):
    """
    Mark an SOS packet as responded.
    
    Called by responders when they have addressed an emergency.
    """
    # Find the packet
    packet = db.query(SosPacketDB).filter(SosPacketDB.sos_id == request.sos_id).first()
    
    if not packet:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"SOS packet {request.sos_id} not found"
        )
    
    if packet.status == DeliveryStatus.RESPONDED:
        return UploadResponse(
            success=True,
            sos_id=request.sos_id,
            message="SOS packet already marked as responded"
        )
    
    # Update status
    packet.status = DeliveryStatus.RESPONDED
    packet.responded_at = datetime.utcnow()
    packet.responder_id = request.responder_id
    
    db.commit()
    
    return UploadResponse(
        success=True,
        sos_id=request.sos_id,
        message="SOS packet marked as responded"
    )


@router.get("/sos/{sos_id}", response_model=SosPacketResponse)
async def get_sos_by_id(
    sos_id: UUID,
    db: Session = Depends(get_db)
):
    """
    Get a specific SOS packet by ID.
    """
    packet = db.query(SosPacketDB).filter(SosPacketDB.sos_id == sos_id).first()
    
    if not packet:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"SOS packet {sos_id} not found"
        )
    
    return SosPacketResponse.model_validate(packet)
