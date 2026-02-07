// SOS Card Component - displays emergency case info
import { useState } from 'react';
import { MapPin, Clock, Route, Activity } from 'lucide-react';
import { sosAPI } from '../api/client';
import './SosCard.css';

const emergencyColors = {
    MEDICAL: 'var(--emergency-medical)',
    FIRE: 'var(--emergency-fire)',
    FLOOD: 'var(--emergency-flood)',
    EARTHQUAKE: 'var(--emergency-earthquake)',
    GENERAL: 'var(--emergency-general)',
};

const statusColors = {
    PENDING: 'var(--status-warning)',
    RELAYED: 'var(--status-warning)',
    DELIVERED: 'var(--status-online)',
    RESPONDED: 'var(--status-online)',
};

export default function SosCard({ sos, onUpdate }) {
    const [loading, setLoading] = useState(false);
    const emergencyColor = emergencyColors[sos.emergency_type] || emergencyColors.GENERAL;
    const statusColor = statusColors[sos.status] || 'var(--text-secondary)';

    const formatDate = (timestamp) => {
        // Add 'Z' to indicate UTC if not present (backend sends without timezone)
        const utcTimestamp = timestamp.endsWith('Z') ? timestamp : timestamp + 'Z';
        const date = new Date(utcTimestamp);
        return date.toLocaleString();
    };

    const handleMarkResponded = async () => {
        setLoading(true);
        try {
            await sosAPI.updateStatus(sos.sos_id);
            if (onUpdate) onUpdate();
        } catch (error) {
            console.error('Error marking as responded:', error);
            alert('Failed to mark as responded');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={`sos-card ${sos.status === 'PENDING' ? 'pulse-glow' : ''}`}>
            <div className="sos-card-header">
                <div className="emergency-badge" style={{ backgroundColor: `${emergencyColor}20`, color: emergencyColor }}>
                    <Activity size={20} />
                    <span>{sos.emergency_type}</span>
                </div>
                <div className="status-badge" style={{ color: statusColor }}>
                    {sos.status}
                </div>
            </div>

            <div className="sos-card-body">
                <div className="info-row">
                    <MapPin size={16} />
                    <span>{sos.latitude.toFixed(4)}, {sos.longitude.toFixed(4)}</span>
                </div>

                <div className="info-row">
                    <Clock size={16} />
                    <span>{formatDate(sos.timestamp)}</span>
                </div>

                <div className="info-row">
                    <Route size={16} />
                    <span>{sos.hop_count} hops • TTL: {sos.ttl}</span>
                </div>

                {sos.optional_message && (
                    <div className="sos-message">
                        <p>"{sos.optional_message}"</p>
                    </div>
                )}
            </div>

            {sos.status !== 'RESPONDED' && (
                <div className="sos-card-footer">
                    <button
                        className="btn btn-primary btn-sm"
                        onClick={handleMarkResponded}
                        disabled={loading}
                    >
                        {loading ? 'Updating...' : 'Mark Responded'}
                    </button>
                </div>
            )}
        </div>
    );
}
