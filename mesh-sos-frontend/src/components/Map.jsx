import { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import { AlertCircle, Battery, Signal } from 'lucide-react';
import './Map.css';

// Custom marker colors matching status badges
const TYPE_COLORS = {
    FIRE: '#dc2626',
    MEDICAL: '#2563eb',
    FLOOD: '#0ea5e9',
    EARTHQUAKE: '#d97706',
    GENERAL: '#6b7280'
};

// Create custom SVG icon for each emergency type
const createCustomIcon = (type) => {
    const color = TYPE_COLORS[type] || TYPE_COLORS.GENERAL;

    return L.divIcon({
        className: 'custom-marker-icon', // defined in CSS to remove default square box
        html: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="${color}" stroke="#ffffff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="filter: drop-shadow(0px 2px 4px rgba(0,0,0,0.3)); width: 32px; height: 32px;">
                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                <circle cx="12" cy="10" r="3" fill="#ffffff"></circle>
              </svg>`,
        iconSize: [32, 32],
        iconAnchor: [16, 30], // Tip of the pin
        popupAnchor: [0, -32]
    });
};

// Component to update map center dynamically
function MapUpdater({ activeSOS }) {
    const map = useMap();

    useEffect(() => {
        if (activeSOS.length > 0) {
            // Create bounds from all active SOS locations
            const bounds = L.latLngBounds(activeSOS.map(sos => [sos.latitude, sos.longitude]));
            map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
        }
    }, [activeSOS, map]);

    return null;
}

export default function Map({ activeSOS = [] }) {
    // Default center (center of India approx, or user location if available)
    const defaultCenter = [20.5937, 78.9629];

    return (
        <div className="map-wrapper">
            <MapContainer
                center={defaultCenter}
                zoom={5}
                className="leaflet-map"
                scrollWheelZoom={true}
            >
                <TileLayer
                    attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                    url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                />

                <MapUpdater activeSOS={activeSOS} />

                {activeSOS.map((sos) => (
                    <Marker
                        key={sos.sos_id}
                        position={[sos.latitude, sos.longitude]}
                        icon={createCustomIcon(sos.emergency_type)}
                    >
                        <Popup className="sos-popup">
                            <div className="popup-content">
                                <div className="popup-header" style={{ borderTop: `3px solid ${TYPE_COLORS[sos.emergency_type] || TYPE_COLORS.GENERAL}` }}>
                                    <span className={`popup-badge type-${sos.emergency_type.toLowerCase()}`}>
                                        {sos.emergency_type}
                                    </span>
                                    <span className="popup-time">
                                        {new Date(sos.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                    </span>
                                </div>
                                <div className="popup-details">
                                    <p><strong>Device:</strong> {sos.device_id}</p>
                                    <div className="popup-metrics">
                                        <span title="Battery">
                                            <Battery size={14} />
                                            {sos.battery_percentage !== undefined ? `${sos.battery_percentage}%` : 'N/A'}
                                        </span>
                                        <span title="Hops">
                                            <Signal size={14} />
                                            {sos.hop_count} hops
                                        </span>
                                    </div>
                                    <div className="popup-coords">
                                        {sos.latitude.toFixed(6)}, {sos.longitude.toFixed(6)}
                                    </div>
                                </div>
                            </div>
                        </Popup>
                    </Marker>
                ))}
            </MapContainer>
        </div>
    );
}
