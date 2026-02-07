// MeshSOS Header Component
import { Link, useLocation } from 'react-router-dom';
import { Activity, List, PlusCircle } from 'lucide-react';
import './Header.css';

export default function Header({ stats }) {
    const location = useLocation();

    const isActive = (path) => location.pathname === path;

    return (
        <header className="header">
            <div className="header-container">
                <div className="header-left">
                    <Link to="/" className="logo">
                        <span className="logo-text">Mesh<span className="logo-accent">SOS</span></span>
                    </Link>

                    <nav className="nav">
                        <Link
                            to="/"
                            className={`nav-link ${isActive('/') ? 'active' : ''}`}
                        >
                            <Activity size={18} />
                            <span>Dashboard</span>
                        </Link>

                        <Link
                            to="/sos-list"
                            className={`nav-link ${isActive('/sos-list') ? 'active' : ''}`}
                        >
                            <List size={18} />
                            <span>SOS List</span>
                        </Link>

                        <Link
                            to="/create"
                            className={`nav-link ${isActive('/create') ? 'active' : ''}`}
                        >
                            <PlusCircle size={18} />
                            <span>Create SOS</span>
                        </Link>
                    </nav>
                </div>

                <div className="header-right">
                    {stats && (
                        <div className="header-stats">
                            <div className="stat-item">
                                <span className="stat-label">Active</span>
                                <span className="stat-value active">{stats.activeSOS || 0}</span>
                            </div>
                            <div className="stat-divider"></div>
                            <div className="stat-item">
                                <span className="stat-label">Total</span>
                                <span className="stat-value">{stats.totalSOS || 0}</span>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </header>
    );
}
