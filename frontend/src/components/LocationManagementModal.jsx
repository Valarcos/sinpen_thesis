import { useState, useEffect, useRef } from 'react';
import toast from 'react-hot-toast';
import api from '../services/api';

export default function LocationManagementModal({ onClose, onLocationAdded }) {
    const [locations, setLocations] = useState([]);
    const [newLocationName, setNewLocationName] = useState('');
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;
        fetchLocations();
        return () => { isMounted.current = false; };
    }, []);

    const fetchLocations = async () => {
        try {
            if (isMounted.current) setLoading(true);
            const response = await api.get('/locations');
            if (isMounted.current) setLocations(response.data);
        } catch (error) {
            console.error('Error fetching locations:', error);
            // Error handled by global interceptor, no duplicate toast
        } finally {
            if (isMounted.current) setLoading(false);
        }
    };

    const handleCreate = async (e) => {
        e.preventDefault();
        if (submitting) return;
        if (!newLocationName.trim()) return;

        // Frontend validation for number-only as per backend rule
        if (!/^\d+$/.test(newLocationName)) {
            toast.error('El nombre de la estantería debe ser un número.');
            return;
        }

        try {
            if (isMounted.current) setSubmitting(true);
            const response = await api.post('/locations', { nombre: newLocationName });
            toast.success('Estantería agregada correctamente');
            if (isMounted.current) setNewLocationName('');
            fetchLocations(); // Refresh list in modal
            if (onLocationAdded) {
                onLocationAdded(response.data); // Notify parent to refresh their dropdown
            }
        } catch (error) {
            console.error('Error creating location:', error);
            // Vector 2: Force re-fetch on failure to sync state
            fetchLocations();
        } finally {
            if (isMounted.current) setSubmitting(false);
        }
    };

    return (
        <div className="modal-overlay">
            <div className="modal-content" style={{ maxWidth: '400px' }}>
                <div className="modal-header">
                    <h2>Gestionar Ubicaciones</h2>
                    <button onClick={onClose} className="close-btn" aria-label="Cerrar">&times;</button>
                </div>

                <div className="modal-body">
                    <form onSubmit={handleCreate} className="location-form" style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
                        <input
                            type="text"
                            placeholder="Nro. Estantería (ej: 5)"
                            value={newLocationName}
                            onChange={(e) => setNewLocationName(e.target.value)}
                            disabled={submitting}
                            style={{ flex: 1, padding: '0.5rem' }}
                        />
                        <button type="submit" className="primary" disabled={submitting || !newLocationName}>
                            {submitting ? '...' : '+'}
                        </button>
                    </form>

                    <div className="locations-list" style={{ maxHeight: '200px', overflowY: 'auto', border: '1px solid #eee', borderRadius: '4px' }}>
                        {loading ? (
                            <p style={{ padding: '0.5rem', textAlign: 'center' }}>Cargando...</p>
                        ) : locations.length === 0 ? (
                            <p style={{ padding: '0.5rem', textAlign: 'center', color: '#666' }}>No hay ubicaciones registradas.</p>
                        ) : (
                            <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                                {(locations || []).map(loc => (
                                    <li key={loc.id} style={{
                                        padding: '0.5rem',
                                        borderBottom: '1px solid #f0f0f0',
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'center'
                                    }}>
                                        <span>Estantería <strong>{loc.nombre}</strong></span>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>
                </div>

                <div className="modal-actions">
                    <button onClick={onClose} className="secondary">Cerrar</button>
                </div>
            </div>
        </div>
    );
}
