# MeshSOS - Offline Emergency Mesh SOS Network

Disaster-resilient emergency SOS system using Bluetooth Mesh for peer-to-peer communication.

## Features

- **Offline SOS** - Send emergency signals without internet
- **Mesh Network** - SOS signals hop between nearby devices
- **Auto-Sync** - Uploads to cloud when internet available
- **Multiple Emergency Types** - Medical, Fire, Flood, Earthquake, General
- **Responder Mode** - View incoming SOS on map

## Requirements

- Android 8.0 (API 26) or higher
- Bluetooth Low Energy (BLE) support
- GPS

## Building

```bash
# From MeshSOS directory
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

## Permissions

The app requires:
- Bluetooth (scan, advertise, connect)
- Location (for GPS and BLE scanning)
- Notifications

## Architecture

- **MVVM** with Jetpack Compose UI
- **Room Database** for local packet storage
- **BLE Mesh** for peer-to-peer communication
- **Retrofit** for cloud sync
- **Foreground Service** for background operation

## Backend

See `../mesh-sos-backend/README.md` for backend setup.

## Testing

1. Install on 2+ devices
2. Start mesh service on both
3. Put one device in airplane mode
4. Send SOS from first device
5. Verify second device receives and relays
