// Dashboard Page - Main view with stats and active SOS list
import { useState, useEffect } from 'react';
import { AlertCircle, Activity, Users, CheckCircle } from 'lucide-react';
import StatusCard from '../components/StatusCard';
import SosCard from '../components/SosCard';
import { sosAPI } from '../api/client';
import './Dashboard.css';

export default function Dashboard() {
    const [activeSOS, setActiveSOS] = useState([]);
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const fetchData = async () => {
        try {
            setError(null);
            const [sosData, statsData] = await Promise.all([
                sosAPI.getActive(),
                sosAPI.getStats()
            ]);
            setActiveSOS(sosData);
            setStats(statsData);
        } catch (err) {
            console.error('Error fetching data:', err);
            setError('Failed to load data from backend');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchData();

        // Auto-refresh every 10 seconds
        const interval = setInterval(fetchData, 10000);
        return () => clearInterval(interval);
    }, []);

    if (loading) {
        return (
            <div className="dashboard-loading">
                <div className="pulse">Loading...</div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="dashboard-error">
                <AlertCircle size={48} />
                <p>{error}</p>
                <button className="btn btn-primary" onClick={fetchData}>Retry</button>
            </div>
        );
    }

    return (
        <div className="dashboard">
            <div className="dashboard-header">
                <h1>Emergency Dashboard</h1>
                <p className="text-secondary">Real-time monitoring of mesh network SOS signals</p>
            </div>

            <div className="stats-grid">
                <StatusCard
                    icon={AlertCircle}
                    label="Active SOS"
                    value={stats?.activeSOS || activeSOS.length}
                    color="var(--primary)"
                />
                <StatusCard
                    icon={Activity}
                    label="Total SOS"
                    value={stats?.totalSOS || 0}
                    color="var(--status-warning)"
                />
                <StatusCard
                    icon={Users}
                    label="Total Nodes"
                    value={stats?.totalNodes || 0}
                    color="var(--status-online)"
                />
                <StatusCard
                    icon={CheckCircle}
                    label="Response Rate"
                    value={stats?.responseRate ? `${stats.responseRate}%` : 'N/A'}
                    color="var(--status-online)"
                />
            </div>

            <div className="active-sos-section">
                <div className="section-header">
                    <h2>Active Emergencies</h2>
                    <span className="count-badge">{activeSOS.length}</span>
                </div>

                {activeSOS.length === 0 ? (
                    <div className="empty-state">
                        <CheckCircle size={64} color="var(--status-online)" />
                        <h3>No Active Emergencies</h3>
                        <p>All SOS signals have been responded to</p>
                    </div>
                ) : (
                    <div className="sos-grid">
                        {activeSOS.map((sos) => (
                            <SosCard key={sos.sos_id} sos={sos} onUpdate={fetchData} />
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
