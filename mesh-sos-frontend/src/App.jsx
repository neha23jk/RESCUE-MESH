// Main App Component with React Router
import { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Header from './components/Header';
import Dashboard from './pages/Dashboard';
import SosList from './pages/SosList';
import CreateSos from './pages/CreateSos';
import { sosAPI } from './api/client';

function App() {
  const [stats, setStats] = useState(null);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const data = await sosAPI.getStats();
        setStats(data);
      } catch (err) {
        console.error('Error fetching stats:', err);
      }
    };

    fetchStats();
    const interval = setInterval(fetchStats, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <Router>
      <div className="app">
        <Header stats={stats} />
        <main>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/sos-list" element={<SosList />} />
            <Route path="/create" element={<CreateSos />} />
          </Routes>
        </main>
      </div>
    </Router>
  );
}

export default App;
