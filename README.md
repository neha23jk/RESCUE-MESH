# RESCUE-MESH (MeshSOS)

**MeshSOS** is an Offline Emergency Mesh SOS Network designed to provide reliable communication in disaster scenarios where traditional infrastructure (cellular/Wi-Fi) is unavailable. It enables devices to form a mesh network, relay SOS signals, and upload them to a central server when connectivity is restored.

## 🚀 Features

- **Offline Mesh Networking:** Devices communicate directly with each other to relay emergency signals.
- **SOS Broadcasting:** Users can broadcast SOS signals with location, emergency type, and optional messages.
- **Deduplication:** Efficient packet handling to avoid redundant data.
- **Centralized Dashboard:** A web-based dashboard for responders to visualize emergencies on a map.
- **Cross-Platform:** Android app for users and a web dashboard for emergency centers.

## 🛠️ Tech Stack

### Backend
- **Framework:** FastAPI (Python)
- **Database:** PostgreSQL (with SQLAlchemy)
- **Deployment:** Docker / Docker Compose

### Frontend
- **Framework:** React (Vite)
- **Map Integration:** Leaflet / React-Leaflet
- **Styling:** CSS Modules / Lucide React Icons

### Android App
- **Language:** Kotlin / Java
- **Communication:** BLE (Bluetooth Low Energy) / WiFi Direct

## 📂 Project Structure

- `mesh-sos-backend/`: FastAPI backend service.
- `mesh-sos-frontend/`: React-based web dashboard.
- `MeshSOS/`: Android application source code.

## 🏁 Getting Started

### Prerequisites
- Node.js & npm
- Python 3.9+
- Docker & Docker Compose (optional but recommended)

### 1. Backend Setup

You can run the backend using Docker or manually.

**Option A: Docker (Recommended)**
```bash
cd mesh-sos-backend
docker-compose up -d --build
```

**Option B: Manual**
```bash
cd mesh-sos-backend
python -m venv venv
# Activate venv: .\venv\Scripts\activate (Windows) or source venv/bin/activate (Linux/Mac)
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
```
*Note: The backend runs on port `8001` and listens on `0.0.0.0` for external access.*

### 2. Frontend Setup

```bash
cd mesh-sos-frontend
npm install
npm run dev
```
*The dashboard will be available at `http://localhost:5173`.*

## 📱 Android App Connection

To connect the Android app (running on a physical device or emulator) to the backend:

1. Ensure your phone and computer are on the **same Wi-Fi network**.
2. Identify your computer's **Local IP Address** (e.g., `192.168.1.10`).
3. In the Android app settings, point the API URL to: `http://<YOUR_LOCAL_IP>:8001`.
   - *For Android Emulator, use `http://10.0.2.2:8001`.*

## 🤝 Contributing

Contributions are welcome! Please open an issue or submit a pull request.

## 📄 License

This project is open-source and available under the [MIT License](LICENSE).
