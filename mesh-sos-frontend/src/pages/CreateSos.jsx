// Create SOS Page - Manual SOS creation form
import { useState } from 'react';
import { AlertCircle, MapPin, MessageSquare } from 'lucide-react';
import { sosAPI } from '../api/client';
import './CreateSos.css';

const emergencyTypes = ['MEDICAL', 'FIRE', 'FLOOD', 'EARTHQUAKE', 'GENERAL'];

export default function CreateSos() {
    const [formData, setFormData] = useState({
        emergency_type: 'GENERAL',
        latitude: 0,
        longitude: 0,
        battery_percentage: 100,
        optional_message: '',
    });
    const [loading, setLoading] = useState(false);
    const [success, setSuccess] = useState(false);
    const [error, setError] = useState(null);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError(null);

        try {
            await sosAPI.create(formData);
            setSuccess(true);
            setTimeout(() => {
                setSuccess(false);
                setFormData({
                    emergency_type: 'GENERAL',
                    latitude: 0,
                    longitude: 0,
                    battery_percentage: 100,
                    optional_message: '',
                });
            }, 3000);
        } catch (err) {
            setError('Failed to create SOS. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="create-sos">
            <div className="create-sos-header">
                <h1>Create Manual SOS</h1>
                <p className="text-secondary">Testing & administrative SOS creation</p>
            </div>

            {success && (
                <div className="alert alert-success">
                    <AlertCircle />
                    <span>SOS signal created successfully!</span>
                </div>
            )}

            {error && (
                <div className="alert alert-error">
                    <AlertCircle />
                    <span>{error}</span>
                </div>
            )}

            <form onSubmit={handleSubmit} className="sos-form">
                <div className="form-group">
                    <label>Emergency Type</label>
                    <select
                        value={formData.emergency_type}
                        onChange={(e) => setFormData({ ...formData, emergency_type: e.target.value })}
                        required
                    >
                        {emergencyTypes.map((type) => (
                            <option key={type} value={type}>
                                {type}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="form-row">
                    <div className="form-group">
                        <label>
                            <MapPin size={16} />
                            Latitude
                        </label>
                        <input
                            type="number"
                            step="0.000001"
                            value={formData.latitude}
                            onChange={(e) => setFormData({ ...formData, latitude: parseFloat(e.target.value) })}
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label>
                            <MapPin size={16} />
                            Longitude
                        </label>
                        <input
                            type="number"
                            step="0.000001"
                            value={formData.longitude}
                            onChange={(e) => setFormData({ ...formData, longitude: parseFloat(e.target.value) })}
                            required
                        />
                    </div>
                </div>

                <div className="form-group">
                    <label>Battery Percentage</label>
                    <input
                        type="number"
                        min="0"
                        max="100"
                        value={formData.battery_percentage}
                        onChange={(e) => setFormData({ ...formData, battery_percentage: parseInt(e.target.value) })}
                        required
                    />
                </div>

                <div className="form-group">
                    <label>
                        <MessageSquare size={16} />
                        Optional Message
                    </label>
                    <textarea
                        rows="4"
                        value={formData.optional_message}
                        onChange={(e) => setFormData({ ...formData, optional_message: e.target.value })}
                        placeholder="Additional details about the emergency..."
                    />
                </div>

                <button type="submit" className="btn btn-primary btn-large" disabled={loading}>
                    {loading ? 'Creating SOS...' : 'Create SOS'}
                </button>
            </form>
        </div>
    );
}
