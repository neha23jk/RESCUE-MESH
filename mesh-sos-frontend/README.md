# MeshSOS Frontend Dashboard

**Real-time responder dashboard for emergency SOS visualization and coordination.**

React-based web application that displays active emergency SOS locations on an interactive map, enabling emergency responders to assess situational awareness and coordinate relief efforts.

## Overview

The dashboard connects to the backend API and provides:
- **Interactive Map** – Leaflet-based geospatial visualization
- **SOS Cards** – Detailed view of each emergency (location, type, battery, timestamp)
- **Real-time Updates** – Fetch and display active SOS packets
- **Response Tracking** – Mark SOS as responded and assign responder IDs
- **Filtering** – Search by emergency type, time window, and status

---

## Quick Start

### Prerequisites
- Node.js 16+ and npm

### Installation

```bash
cd mesh-sos-frontend

# Install dependencies
npm install

# Start development server
npm run dev

# Dashboard will be available at http://localhost:5173
```

### Build for Production

```bash
npm run build

# Preview production build
npm run preview
```

---

## Tech Stack

| Tool | Version |
|------|---------|
| React | 19.2+ |
| Vite | 7.2+ |
| Leaflet | 1.9.4 |
| React-Leaflet | 5.0.0 |
| Axios | 1.13.4 |
| Lucide React (Icons) | 0.563.0 |

---

## Project Structure

```
src/
├── App.jsx              # Main router & layout
├── App.css              # Global styles
├── components/
│   ├── Header.jsx       # Navigation & title bar
│   ├── Header.css
│   ├── Map.jsx          # Leaflet map with markers
│   ├── Map.css
│   ├── SosCard.jsx      # Individual SOS packet details
│   ├── SosCard.css
│   ├── StatusCard.jsx   # System health metrics
│   └── StatusCard.css
├── api/                 # API client (Axios)
├── assets/              # Images, logos
└── index.css            # CSS reset & utilities
```

---

## Components

### Map Component
- Displays Leaflet map centered on emergency locations
- Color-coded markers by emergency type (red=fire, blue=medical, etc.)
- Click markers to view SOS details
- Pan and zoom navigation

### SOS Card
- Shows SOS packet metadata:
  - Location (lat/lon + accuracy)
  - Emergency type badge
  - Device battery percentage
  - Time elapsed since SOS broadcast
  - Optional message
- **Mark Responded** button – Send responder ID back to backend

### Header
- Title and navigation
- Status indicator (connected/disconnected from backend)
- Theme toggle (optional)

### Status Card
- Active SOS count
- Responders online
- System uptime

---

## API Integration

The dashboard fetches data from the backend at:

```
GET /api/v1/active-sos?hours=24&limit=100
```

**Response:**
```json
{
  "count": 3,
  "sos_packets": [
    {
      "sos_id": "uuid",
      "device_id": "hash",
      "latitude": 40.7128,
      "longitude": -74.0060,
      "emergency_type": "MEDICAL",
      "optional_message": "Collapsed building",
      "battery_percentage": 25,
      "timestamp": "2025-01-15T10:30:00Z",
      "status": "DELIVERED"
    }
  ]
}
```

---

## Environment Configuration

Create `.env` file:

```env
VITE_API_BASE_URL=http://localhost:8001
VITE_MAP_ZOOM_LEVEL=12
VITE_UPDATE_INTERVAL_MS=5000
```

---

## Development

### Code Style
```bash
npm run lint
```

### Dependencies
- **Axios** – HTTP client for backend communication
- **React Router** – Client-side routing (future: multi-page dashboard)
- **Leaflet** – Lightweight map library
- **Lucide React** – Icon library

---

## Common Tasks

### Add New Component
```bash
# Create component in src/components/MyComponent.jsx
# Import in App.jsx
# Add styling in MyComponent.css
```

### Connect to New API Endpoint
```javascript
// In src/api/client.js
export const fetchActiveSOS = (hours, limit) => {
  return api.get('/api/v1/active-sos', {
    params: { hours, limit }
  });
};
```

### Update Map Styling
Edit `Map.css` and Leaflet tile provider in `Map.jsx`:
```javascript
<TileLayer
  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
  attribution="© OpenStreetMap contributors"
/>
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Map not loading | Check backend API is running on port 8001; verify `VITE_API_BASE_URL` |
| Markers not showing | Ensure SOS packets have valid lat/lon; check browser console for errors |
| CORS errors | Backend needs `CORSMiddleware` configured (see backend README) |
| Stale data | Increase fetch frequency in `VITE_UPDATE_INTERVAL_MS` |

---

## Deployment

### Vercel (Recommended)

```bash
# Install Vercel CLI
npm i -g vercel

# Deploy
vercel
```

### AWS S3 + CloudFront

```bash
npm run build

# Upload dist/ to S3
aws s3 sync dist/ s3://my-bucket/ --delete

# Invalidate CloudFront cache
aws cloudfront create-invalidation --distribution-id E123 --paths "/*"
```

### Docker

```dockerfile
FROM node:18-alpine as build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

---

## Performance Tips

- Lazy load map library with `React.lazy()`
- Memoize map markers to prevent re-renders
- Paginate SOS results (implement infinite scroll or pagination)
- Cache API responses with 30–60 second TTL
- Use IndexedDB for offline SOS backup

---

## Contributing

See [main README](../README.md#contributing) for contribution guidelines.

---

## License

MIT – See [LICENSE](../LICENSE)
