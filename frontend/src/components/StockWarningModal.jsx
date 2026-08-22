import { useState, useEffect, useRef } from 'react';
import api from '../services/api';
import toast from 'react-hot-toast';
import './StockWarningModal.css';

/**
 * Renders a single warning list item with an inline location selector
 * and a 1-click auto-correct button. Each item manages its own locations
 * state independently to prevent cross-item interference.
 */
function WarningListItem({ product, onStockCorrected }) {
    const [locations, setLocations] = useState([]);
    const [selectedLocation, setSelectedLocation] = useState('');
    const [isCorrecting, setIsCorrecting] = useState(false);
    const [loadingLocs, setLoadingLocs] = useState(true);
    const isMounted = useRef(true);

    // Calculate the exact missing amount — the system does the math for the user.
    const missingQty = Math.max(0, product.cartQuantity - product.cantidadStock);

    useEffect(() => {
        isMounted.current = true;
        // Defensive: guard against invalid productId
        if (!product?.id) return;

        setLoadingLocs(true);
        api.get(`/stock/producto/${product.id}`)
            .then(res => {
                if (!isMounted.current) return;
                const locs = res.data || [];
                setLocations(locs);
                // Defensive: fallback to primary location (1) if no locations exist yet
                // (new product that never had stock initialized)
                if (locs.length > 0) {
                    setSelectedLocation(String(locs[0].ubicacionId));
                } else {
                    setSelectedLocation('1');
                }
            })
            .catch(err => {
                console.error(`Error fetching locations for product ${product.id}`, err);
                // Error handled by global api interceptor
                // Fallback: allow correction against primary location
                if (isMounted.current) setSelectedLocation('1');
            })
            .finally(() => {
                if (isMounted.current) setLoadingLocs(false);
            });

        return () => { isMounted.current = false; };
    }, [product.id]);

    const handleAutoCorrect = async () => {
        if (isCorrecting) return; // Double-submit protection (GEMINI.md Rule 3)
        if (!selectedLocation) {
            toast.error('Seleccione una ubicación');
            return;
        }
        if (missingQty <= 0) {
            toast.error('No hay déficit de stock que corregir');
            return;
        }

        setIsCorrecting(true);
        try {
            await api.post('/stock/add', {
                productoId: product.id,
                ubicacionId: parseInt(selectedLocation, 10),
                cantidad: missingQty
            });
            toast.success(`Stock de "${product.descripcion}" corregido (+${missingQty})`);
            // Notify parent with the specific productId so VentaPage can do a targeted refresh
            if (isMounted.current && onStockCorrected) onStockCorrected(product.id);
        } catch (error) {
            console.error('Error auto-correcting stock', error);
            // Vector 2: Force re-fetch on failure to resync state
            api.get(`/stock/producto/${product.id}`).then(res => {
                if (isMounted.current) setLocations(res.data || []);
            });
            // Error handled by global api interceptor
        } finally {
            if (isMounted.current) setIsCorrecting(false);
        }
    };

    return (
        <li className="warning-item">
            <div className="warning-info">
                <span className="warning-name">{product.descripcion}</span>
                <span className="warning-stock">
                    Stock: {product.cantidadStock} | A vender: {product.cartQuantity} | Faltante: {missingQty}
                </span>
                {loadingLocs ? (
                    <span style={{ fontSize: '0.8rem', color: '#666', marginTop: '0.3rem' }}>
                        Cargando ubicaciones...
                    </span>
                ) : (
                    <div className="warning-inline-controls">
                        <select
                            value={selectedLocation}
                            onChange={e => setSelectedLocation(e.target.value)}
                            className="warning-location-select"
                            disabled={isCorrecting}
                        >
                            {(locations || []).length > 0 ? (
                                (locations || []).map(loc => (
                                    <option key={loc.id} value={String(loc.ubicacionId)}>
                                        {loc.nombreUbicacion || `Ubicación ${loc.ubicacionId}`} (Actual: {loc.cantidad})
                                    </option>
                                ))
                            ) : (
                                <option value="1">Ubicación Principal</option>
                            )}
                        </select>
                        <button
                            className="correct-btn"
                            onClick={handleAutoCorrect}
                            disabled={isCorrecting}
                        >
                            {isCorrecting ? 'Corrigiendo...' : `Auto-Corregir (+${missingQty})`}
                        </button>
                    </div>
                )}
            </div>
        </li>
    );
}

export default function StockWarningModal({ affectedProducts, onClose, onContinue, onStockCorrected }) {
    if (!affectedProducts || affectedProducts.length === 0) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content warning-modal">
                <div className="modal-header">
                    <h2>⚠️ Advertencia de Stock Negativo</h2>
                </div>

                <p>Los siguientes productos tienen stock insuficiente:</p>
                <ul className="warning-list">
                    {(affectedProducts || []).map(p => (
                        <WarningListItem
                            key={p.id}
                            product={p}
                            onStockCorrected={onStockCorrected}
                        />
                    ))}
                </ul>

                <div className="modal-actions">
                    <button className="cancel-btn" onClick={onClose}>Cancelar Venta</button>
                    <button className="continue-btn" onClick={onContinue}>Ignorar y Continuar</button>
                </div>
            </div>
        </div>
    );
}
