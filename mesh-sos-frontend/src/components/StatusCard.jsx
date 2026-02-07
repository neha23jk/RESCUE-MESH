// Status Card Component - displays stats
import './StatusCard.css';

export default function StatusCard({ icon: Icon, label, value, color = 'var(--text-primary)' }) {
    return (
        <div className="status-card">
            <div className="status-card-icon" style={{ color }}>
                <Icon size={32} />
            </div>
            <div className="status-card-content">
                <div className="status-card-label">{label}</div>
                <div className="status-card-value" style={{ color }}>{value}</div>
            </div>
        </div>
    );
}
