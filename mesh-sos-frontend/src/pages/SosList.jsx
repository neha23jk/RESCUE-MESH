// SOS List Page - Full list with filtering
import { useState, useEffect } from 'react';
import { AlertCircle } from 'lucide-react';
import SosCard from '../components/SosCard';
import { sosAPI } from '../api/client';
import './SosList.css';

export default function SosList() {
    const [allSOS, setAllSOS] = useState([]);
    const [filteredSOS, setFilteredSOS] = useState([]);
    const [filter, setFilter] = useState('ALL');
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetchData();
        const interval = setInterval(fetchData, 10000);
        return () => clearInterval(interval);
    }, []);

    useEffect(() => {
        if (filter === 'ALL') {
            setFilteredSOS(allSOS);
        } else {
            setFilteredSOS(allSOS.filter(sos => sos.status === filter));
        }
    }, [filter, allSOS]);

    const fetchData = async () => {
        try {
            const data = await sosAPI.getAll();
            setAllSOS(data);
        } catch (err) {
            console.error('Error fetching SOS list:', err);
        } finally {
            setLoading(false);
        }
    };

    if (loading) {
        return <div className="sos-list-loading">Loading...</div>;
    }

    return (
        <div className="sos-list">
            <div className="sos-list-header">
                <h1>SOS History</h1>
                <div className="filter-buttons">
                    <button
                        className={`filter-btn ${filter === 'ALL' ? 'active' : ''}`}
                        onClick={() => setFilter('ALL')}
                    >
                        All ({allSOS.length})
                    </button>
                    <button
                        className={`filter-btn ${filter === 'PENDING' ? 'active' : ''}`}
                        onClick={() => setFilter('PENDING')}
                    >
                        Pending
                    </button>
                    <button
                        className={`filter-btn ${filter === 'DELIVERED' ? 'active' : ''}`}
                        onClick={() => setFilter('DELIVERED')}
                    >
                        Delivered
                    </button>
                    <button
                        className={`filter-btn ${filter === 'RESPONDED' ? 'active' : ''}`}
                        onClick={() => setFilter('RESPONDED')}
                    >
                        Responded
                    </button>
                </div>
            </div>

            {filteredSOS.length === 0 ? (
                <div className="empty-list">
                    <AlertCircle size={64} />
                    <h3>No SOS signals found</h3>
                    <p>Filtered SOS list is empty</p>
                </div>
            ) : (
                <div className="sos-grid">
                    {filteredSOS.map((sos) => (
                        <SosCard key={sos.sos_id} sos={sos} />
                    ))}
                </div>
            )}
        </div>
    );
}
