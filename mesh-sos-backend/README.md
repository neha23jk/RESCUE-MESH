# MeshSOS Backend

Backend API for the Offline Emergency Mesh SOS Network.

## Quick Start

### Using Docker (Recommended)

```bash
# Start PostgreSQL and API
docker-compose up -d

# API will be available at http://localhost:8000
# API docs at http://localhost:8000/docs
```

### Manual Setup

```bash
# Create virtual environment
python -m venv venv
venv\Scripts\activate  # Windows
source venv/bin/activate  # Linux/Mac

# Install dependencies
pip install -r requirements.txt

# Copy environment file
copy .env.example .env  # Windows
cp .env.example .env    # Linux/Mac

# Start PostgreSQL (must be running)
# Edit .env with your database URL

# Run the server
uvicorn app.main:app --reload
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/upload-sos` | POST | Upload SOS packet |
| `/api/v1/active-sos` | GET | Get active SOS list |
| `/api/v1/mark-responded` | POST | Mark SOS as responded |
| `/api/v1/sos/{id}` | GET | Get SOS by ID |

## Authentication

All endpoints require `X-API-Key` header:

```bash
curl -H "X-API-Key: your-api-key" http://localhost:8000/api/v1/active-sos
```

## Running Tests

```bash
pytest tests/ -v
```
