# MeshSOS Backend API

**Centralized backend for offline emergency SOS network coordination.**

FastAPI-based REST API that ingests, deduplicates, and stores emergency SOS packets from mesh-connected Android devices. Provides endpoints for responders to query active emergencies, mark responses, and track packet delivery status.

## Overview

The backend handles:
- **SOS Ingestion** – Receive packets uploaded from Android devices when connectivity restored
- **Deduplication** – UUID-based packet tracking prevents redundant storage
- **Geospatial Indexing** – PostgreSQL with lat/lon queries for map visualization
- **Response Tracking** – Assign responder IDs to SOS packets
- **Time-Windowed Queries** – Retrieve emergencies from configurable time windows (1–7 days)

---

## Quick Start

### Using Docker (Recommended)

```bash
cd mesh-sos-backend

# Start PostgreSQL + FastAPI
docker-compose up -d --build

# API available at http://localhost:8001
# API docs at http://localhost:8001/docs (Swagger UI)
# ReDoc docs at http://localhost:8001/redoc

# View logs
docker-compose logs -f api
```

### Manual Setup

```bash
# Create virtual environment
python -m venv venv

# Activate
source venv/bin/activate          # Linux/Mac
# or
.\venv\Scripts\activate            # Windows

# Install dependencies
pip install -r requirements.txt

# Configure environment
cp .env.example .env
# Edit .env with your database URL

# Start PostgreSQL separately (must be running)
# Then run the server:
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
```

---

## Tech Stack

| Component | Version |
|-----------|---------|
| FastAPI | 0.109.0 |
| Uvicorn | 0.27.0 |
| SQLAlchemy | 2.0.25 |
| Pydantic | 2.5.3 |
| PostgreSQL | 15 (Alpine) |
| psycopg2 | 2.9.9 |

---

## API Endpoints

All endpoints use `/api/v1` prefix.

### 1. Upload SOS Packet

**POST** `/upload-sos`

Upload an SOS packet from a mesh-connected device. Implements deduplication by UUID.

**Request:**
```json
{
  "sos_id": "550e8400-e29b-41d4-a716-446655440000",
  "device_id": "device_hash_abc123",
  "timestamp": "2025-01-15T10:30:00Z",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "accuracy": 15.5,
  "emergency_type": "MEDICAL",
  "optional_message": "Collapsed building",
  "battery_percentage": 45,
  "hop_count": 2,
  "ttl": 8,
  "signature": "optional-signature"
}
```

**Response (200):**
```json
{
  "success": true,
  "sos_id": "550e8400-e29b-41d4-a716-446655440000",
  "message": "SOS packet uploaded successfully"
}
```

**Response (409) – Duplicate:**
```json
{
  "success": true,
  "sos_id": "550e8400-e29b-41d4-a716-446655440000",
  "message": "SOS packet already exists"
}
```

---

### 2. Get Active SOS Packets

**GET** `/active-sos`

Retrieve all non-responded SOS packets within a time window.

**Query Parameters:**
- `hours` (int, default=24, range=1–168) – Look back N hours
- `limit` (int, default=100, range=1–500) – Max results to return

**Example:**
```bash
curl http://localhost:8001/api/v1/active-sos?hours=24&limit=100
```

**Response (200):**
```json
{
  "count": 3,
  "sos_packets": [
    {
      "sos_id": "550e8400-e29b-41d4-a716-446655440000",
      "device_id": "device_hash_abc123",
      "latitude": 40.7128,
      "longitude": -74.0060,
      "accuracy": 15.5,
      "emergency_type": "MEDICAL",
      "optional_message": "Collapsed building",
      "battery_percentage": 45,
      "hop_count": 2,
      "ttl": 8,
      "status": "DELIVERED",
      "timestamp": "2025-01-15T10:30:00Z",
      "received_at": "2025-01-15T10:30:05Z",
      "responded_at": null
    }
  ]
}
```

---

### 3. Mark SOS as Responded

**POST** `/mark-responded`

Mark an SOS packet as responded by a rescue responder.

**Request:**
```json
{
  "sos_id": "550e8400-e29b-41d4-a716-446655440000",
  "responder_id": "rescue-team-42"
}
```

**Response (200):**
```json
{
  "success": true,
  "sos_id": "550e8400-e29b-41d4-a716-446655440000",
  "message": "SOS marked as responded"
}
```

**Response (404):**
```json
{
  "detail": "SOS packet 550e8400-e29b-41d4-a716-446655440000 not found"
}
```

---

### 4. Get SOS by ID

**GET** `/sos/{sos_id}`

Fetch a specific SOS packet by UUID.

**Example:**
```bash
curl http://localhost:8001/api/v1/sos/550e8400-e29b-41d4-a716-446655440000
```

**Response (200):**
```json
{
  "sos_id": "550e8400-e29b-41d4-a716-446655440000",
  "device_id": "device_hash_abc123",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "accuracy": 15.5,
  "emergency_type": "MEDICAL",
  "optional_message": "Collapsed building",
  "battery_percentage": 45,
  "hop_count": 2,
  "ttl": 8,
  "status": "DELIVERED",
  "timestamp": "2025-01-15T10:30:00Z",
  "received_at": "2025-01-15T10:30:05Z",
  "responded_at": null
}
```

---

## Data Model

### SOS Packet (Database)

```python
class SosPacketDB(Base):
    __tablename__ = "sos_packets"
    
    sos_id: str (PK)              # UUID
    device_id: str (indexed)      # Hashed device identifier
    timestamp: datetime           # When SOS was created
    latitude: float
    longitude: float
    accuracy: float (nullable)
    emergency_type: EmergencyType # MEDICAL | FIRE | FLOOD | EARTHQUAKE | GENERAL
    optional_message: str (nullable)
    battery_percentage: int (nullable)
    hop_count: int                # Number of mesh relays
    ttl: int                      # Time-to-live
    signature: str (nullable)     # Integrity verification
    status: DeliveryStatus        # PENDING | RELAYED | DELIVERED | RESPONDED
    received_at: datetime         # Server received timestamp
    responded_at: datetime (nullable)
    responder_id: str (nullable)
```

### Enums

**EmergencyType:**
- `MEDICAL` – Medical emergency
- `FIRE` – Fire/explosion
- `FLOOD` – Flooding/water damage
- `EARTHQUAKE` – Seismic event
- `GENERAL` – Other emergency

**DeliveryStatus:**
- `PENDING` – Received but not yet processed
- `RELAYED` – Forwarded by intermediate node
- `DELIVERED` – Stored in backend
- `RESPONDED` – Responder assigned and acknowledged

---

## Environment Configuration

### .env Example

```env
# Database
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/meshsos

# API Security
API_KEY=meshsos-dev-api-key-change-in-production

# Debug mode
DEBUG=true
```

**Production Security:**
```env
DATABASE_URL=postgresql://user:password@prod-db.example.com:5432/meshsos
API_KEY=<strong-random-key-32-chars-minimum>
DEBUG=false
```

---

## Project Structure

```
mesh-sos-backend/
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI app, CORS setup, lifespan
│   ├── database.py          # SQLAlchemy session, connection
│   ├── models.py            # Pydantic schemas + SQLAlchemy models
│   └── routes/
│       ├── __init__.py
│       └── sos.py           # SOS endpoints
├── tests/                   # Pytest test files
├── docker-compose.yml       # PostgreSQL + API orchestration
├── Dockerfile               # Multi-stage FastAPI build
├── requirements.txt         # Python dependencies
├── .env.example             # Template environment variables
└── README.md                # This file
```

---

## Running Tests

```bash
# Install test dependencies (included in requirements.txt)
pytest tests/ -v

# Run specific test
pytest tests/test_sos.py::test_upload_sos -v

# Run with coverage
pytest tests/ --cov=app --cov-report=html
```

---

## Development

### Hot Reload

The development server automatically reloads when files change:

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
```

### Database Migrations

Using Alembic (future enhancement):
```bash
alembic revision --autogenerate -m "Add new column"
alembic upgrade head
```

### Debugging

Enable debug mode in `.env`:
```env
DEBUG=true
```

Use FastAPI's interactive docs:
- Swagger UI: `http://localhost:8001/docs`
- ReDoc: `http://localhost:8001/redoc`

---

## Performance Considerations

### Database Indexing

```python
# Indexes on common queries
device_id   # For aggregating packets per device
status      # For filtering active vs. responded
timestamp   # For time-windowed queries
received_at # For sorting by recency
```

### Query Optimization

```sql
-- Efficient active SOS query
SELECT * FROM sos_packets
WHERE status != 'RESPONDED' 
  AND received_at >= NOW() - INTERVAL '24 hours'
ORDER BY timestamp DESC
LIMIT 100;
```

### Scaling Strategies

- **Read Replicas** – PostgreSQL streaming replication for read-heavy queries
- **Caching** – Redis for frequently accessed SOS by ID
- **API Rate Limiting** – Use `slowapi` (already in requirements)
- **Pagination** – Implement cursor-based pagination for large result sets

---

## Deployment

### Docker Compose Production

```bash
# Build with production build args
docker-compose -f docker-compose.yml build --no-cache

# Deploy with external database
docker-compose --env-file .env.prod up -d
```

### Kubernetes

See `k8s/` directory for Helm charts (future).

### Environment Variables for Production

```env
DATABASE_URL=postgresql://user:pass@managed-db.region.rds.amazonaws.com:5432/meshsos
API_KEY=<128-char-random-string>
DEBUG=false
```

---

## Security

- [ ] **Add API key authentication** – Currently disabled; implement `X-API-Key` header validation
- [ ] **Enable CORS restrictions** – Replace `allow_origins=["*"]` with specific domains
- [ ] **Rate limiting** – Use `slowapi` middleware
- [ ] **Input validation** – Pydantic models handle basic validation; add custom validators
- [ ] **SQL injection protection** – SQLAlchemy parameterized queries prevent injection
- [ ] **Packet encryption** – Add ED25519 signature verification
- [ ] **HTTPS only** – Enforce TLS in production

---

## Contributing

See [main README](../README.md#contributing) for contribution guidelines.

---

## License

MIT – See [LICENSE](../LICENSE)
