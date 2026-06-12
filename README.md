# RESCUE-MESH (MeshSOS)

**An offline-first emergency communication network for disaster resilience.**

MeshSOS is a cross-platform system that enables emergency SOS signals to propagate through peer-to-peer mesh networks when traditional infrastructure (cellular, Wi-Fi) is unavailable. It coordinates emergency responders through a centralized dashboard while maintaining distributed communication at the device level.

## Problem Statement

In natural disasters and infrastructure failures:
- Cell networks collapse or become overwhelmed
- Wi-Fi connectivity is limited or nonexistent  
- Emergency responders lack real-time situational awareness
- Survivors cannot communicate their location and needs

**MeshSOS solves this** by enabling devices to relay emergency signals locally via Bluetooth Low Energy while syncing data to a centralized backend when connectivity is restored.

---

## Architecture

### How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│                     OFFLINE MESH LAYER                          │
│  (BLE/WiFi Direct - operates without internet)                  │
│                                                                   │
│  Device A ──BLE──> Device B ──BLE──> Device C                   │
│  (Broadcasts)     (Relays)           (Relays)                    │
│  SOS Packet       Hop Count++        Upstream...                 │
└─────────────────────────────────────────────────────────────────┘
                           │
                    (When Internet Available)
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    BACKEND LAYER                                 │
│             (FastAPI + PostgreSQL)                               │
│                                                                   │
│  • Deduplicates packets by UUID                                  │
│  • Stores SOS with geolocation, emergency type, metadata         │
│  • Tracks delivery status & responder assignments                │
└─────────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                   RESPONDER DASHBOARD                            │
│              (React Web UI - Leaflet Map)                        │
│                                                                   │
│  • Visualize active SOS locations on map                         │
│  • Filter by emergency type, time range, status                  │
│  • Mark packets as responded                                     │
│  • Real-time updates from backend                                │
└─────────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **Emergency Triggered** → Android device generates SOS packet (UUID, GPS, emergency type, timestamp)
2. **Mesh Propagation** → Packet broadcasts via BLE/WiFi Direct, hops across nearby devices (TTL-based)
3. **Deduplication** → Each relay node tracks packet by UUID to prevent infinite loops
4. **Sync Upload** → When connectivity detected, device uploads all collected packets to backend
5. **Backend Processing** → Server deduplicates by UUID, stores in PostgreSQL with `DELIVERED` status
6. **Dashboard Display** → Responders see active SOS on map, can filter and respond
7. **Response Tracking** → Responder marks packet `RESPONDED` with their ID

---

## Features

- ✅ **Offline Mesh Networking** – BLE/WiFi Direct peer-to-peer communication without internet
- ✅ **SOS Broadcasting** – Send emergency signals with location, emergency type, custom messages
- ✅ **Packet Deduplication** – UUID-based tracking prevents redundant relays
- ✅ **Centralized Dashboard** – Real-time map visualization for emergency responders
- ✅ **Emergency Classification** – Medical, Fire, Flood, Earthquake, General categories
- ✅ **Response Tracking** – Mark packets as responded, assign responder IDs
- ✅ **Cross-Platform** – Android mesh nodes + web responder dashboard
- ✅ **Time-Windowed Queries** – Retrieve SOS from last 1–7 days with configurable limits

---

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Backend** | FastAPI | 0.109.0 |
| **Database** | PostgreSQL | 15 (Alpine) |
| **ORM** | SQLAlchemy | 2.0.25 |
| **Frontend** | React | 19.2 |
| **Map Library** | Leaflet + React-Leaflet | 1.9.4 / 5.0.0 |
| **Build Tool** | Vite | 7.2.4 |
| **Android** | Kotlin / Java | – |
| **Mesh Comms** | BLE (Bluetooth Low Energy) / WiFi Direct | – |
| **Deployment** | Docker / Docker Compose | – |

---

## Project Structure

```
RESCUE-MESH/
├── mesh-sos-backend/          # FastAPI REST API
│   ├── app/
│   │   ├── main.py           # FastAPI app setup
│   │   ├── database.py       # PostgreSQL connection
│   │   ├── models.py         # Pydantic & SQLAlchemy models
│   │   └── routes/
│   │       └── sos.py        # SOS packet endpoints
│   ├── docker-compose.yml    # PostgreSQL + API orchestration
│   ├── Dockerfile
│   └── requirements.txt
│
├── mesh-sos-frontend/         # React Dashboard
│   ├── src/
│   │   ├── App.jsx           # Main app router
│   │   ├── components/
│   │   │   ├── Header.jsx    # Navigation
│   │   │   ├── Map.jsx       # Leaflet map visualization
│   │   │   ├── SosCard.jsx   # SOS packet details
│   │   │   └── StatusCard.jsx
│   │   └── api/              # Axios API client
│   ├── package.json
│   └── vite.config.js
│
├── MeshSOS/                   # Android App
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/         # Kotlin/Java source
│   │   │   ├── res/          # Android resources
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   └── gradle/
│
└── README.md                  # This file
```

---

## API Endpoints

All endpoints use `/api/v1` prefix. See [Backend README](./mesh-sos-backend/README.md) for full details.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `POST /upload-sos` | POST | Upload SOS packet from device |
| `GET /active-sos` | GET | Retrieve active (non-responded) SOS packets |
| `POST /mark-responded` | POST | Mark SOS as responded by responder |
| `GET /sos/{sos_id}` | GET | Fetch specific SOS packet by UUID |

---

## Getting Started

### Prerequisites

- **Node.js** 16+ & npm
- **Python** 3.9+
- **Docker & Docker Compose** (optional, recommended for backend)
- **Android 8.0+** (for mobile app)

### 1. Backend Setup

**Option A: Docker (Recommended)**
```bash
cd mesh-sos-backend
docker-compose up -d --build

# API available at http://localhost:8001
# Docs at http://localhost:8001/docs (Swagger UI)
```

**Option B: Manual**
```bash
cd mesh-sos-backend

# Create and activate virtual environment
python -m venv venv
source venv/bin/activate          # Linux/Mac
# or
.\venv\Scripts\activate            # Windows

# Install dependencies
pip install -r requirements.txt

# Configure environment
cp .env.example .env
# Edit .env with your database URL (default: postgresql://postgres:postgres@localhost:5432/meshsos)

# Start server
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
```

### 2. Frontend Setup

```bash
cd mesh-sos-frontend

npm install
npm run dev

# Dashboard available at http://localhost:5173
```

### 3. Android App

**Build from source:**
```bash
cd MeshSOS
./gradlew assembleDebug
./gradlew installDebug
```

**Configure API connection:**
1. Ensure Android device and development machine are on the same network
2. Find your machine's local IP (e.g., `192.168.1.10`)
3. In app settings, set API URL to: `http://<YOUR_LOCAL_IP>:8001`
   - For Android Emulator: `http://10.0.2.2:8001`

---

## Data Model

### SOS Packet Structure

```python
{
  "sos_id": "uuid-string",           # Unique packet identifier
  "device_id": "hash",                # Source device (privacy-hashed)
  "timestamp": "2025-01-15T10:30:00Z",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "accuracy": 15.5,                   # GPS accuracy in meters
  "emergency_type": "MEDICAL",        # MEDICAL | FIRE | FLOOD | EARTHQUAKE | GENERAL
  "optional_message": "Injured leg",
  "battery_percentage": 45,
  "hop_count": 2,                     # Number of relays
  "ttl": 8,                           # Time-to-live (max hops remaining)
  "signature": "optional-signature",  # For integrity verification
  "status": "DELIVERED",              # PENDING | RELAYED | DELIVERED | RESPONDED
  "received_at": "2025-01-15T10:30:05Z",
  "responded_at": null,
  "responder_id": null
}
```

---

## Development

### Running Tests

```bash
cd mesh-sos-backend
pytest tests/ -v
```

### Code Style

- **Python:** PEP 8 via Black/Flake8
- **JavaScript:** ESLint (configured in `mesh-sos-frontend/.eslintrc`)
- **Kotlin:** Android Studio defaults

### Environment Variables

**Backend (.env.example)**
```env
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/meshsos
API_KEY=meshsos-dev-api-key-change-in-production
DEBUG=true
```

---

## Deployment

### Production Considerations

- Replace `API_KEY` with a strong secret
- Use environment-specific `.env` files (dev/staging/prod)
- Enable CORS restrictions in `main.py` (currently `allow_origins=["*"]`)
- Set up HTTPS/TLS for backend
- Use managed PostgreSQL service (AWS RDS, GCP Cloud SQL, etc.)
- Deploy frontend to CDN (Vercel, Netlify, AWS S3 + CloudFront)
- Use Docker secrets for sensitive data

### Example Docker Production Deployment

```bash
# Build images
docker-compose -f docker-compose.yml build

# Deploy with environment file
docker-compose --env-file .env.prod up -d
```

---

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is open-source and available under the [MIT License](LICENSE).

---

## Roadmap

- [ ] WebRTC for WiFi-based mesh (in addition to BLE)
- [ ] Satellite connectivity fallback
- [ ] End-to-end encryption for packets
- [ ] Push notifications on responder dashboard
- [ ] Offline response capability (mark responded locally, sync later)
- [ ] Machine learning for emergency classification
- [ ] Multi-language support in app

---

## Contact & Support

For questions or issues:
- Open a GitHub issue
- See [Backend README](./mesh-sos-backend/README.md) for API details
- See [Android README](./MeshSOS/README.md) for mobile app specifics
