import { useState, useEffect, useRef } from 'react';
import toast from 'react-hot-toast';
import api from '../services/api';
import ConfirmationModal from './ConfirmationModal';
import './StockManagementModal.css';

/**
 * Modal for managing stock of a specific product across locations.
 * Shows current stock per location and allows adding/subtracting.
 *
 * Props:
 *  - product: the product object
 *  - allowNegativeSubtract (default false): if false, blocks subtract below 0 (Issue #3.2)
 *  - confirmationMode (default false): if true, requires a confirmation step before adding (Issue #3.3)
 *  - onClose, onSuccess
 */
export default function StockManagementModal({ product, onClose, onSuccess, allowNegativeSubtract = false, confirmationMode = false }) {
    const [stockLocations, setStockLocations] = useState([]);
    const [allLocations, setAllLocations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [adjusting, setAdjusting] = useState(false);
    const isMounted = useRef(true);

    // Adjustment form state
    const [selectedLocation, setSelectedLocation] = useState('');
    const [quantity, setQuantity] = useState('');
    const [operation, setOperation] = useState('add'); // 'add' or 'subtract'

    // Issue #3.3: confirmation state before adding in correction context
    const [confirmAddAction, setConfirmAddAction] = useState(null); // { qty, locationId, locationName }

    // Load stock data and locations
    useEffect(() => {
        isMounted.current = true;
        const loadData = async () => {
            try {
                if (isMounted.current) setLoading(true);
                const [stockRes, locationsRes] = await Promise.all([
                    api.get(`/stock/producto/${product.id}`),
                    api.get('/locations')
                ]);
                if (!isMounted.current) return;
                setStockLocations(stockRes.data);
                setAllLocations(locationsRes.data);

                // Default to first location if available
                if (locationsRes.data.length > 0) {
                    setSelectedLocation(locationsRes.data[0].id.toString());
                }
            } catch (error) {
                console.error('Error loading stock data:', error);
                // Error handled by global api interceptor
            } finally {
                if (isMounted.current) setLoading(false);
            }
        };
        loadData();
        return () => { isMounted.current = false; };
    }, [product.id]);

    // Refresh stock after adjustment
    const refreshStock = async () => {
        try {
            const response = await api.get(`/stock/producto/${product.id}`);
            if (isMounted.current) setStockLocations(response.data);
        } catch (error) {
            console.error('Error refreshing stock:', error);
        }
    };

    // Get stock quantity for a specific location
    const getStockForLocation = (locationId) => {
        const stockEntry = stockLocations.find(s => s.ubicacionId === parseInt(locationId, 10));
        return stockEntry ? stockEntry.cantidad : 0;
    };

    // Get location name for display
    const getLocationName = (locationId) => {
        const loc = allLocations.find(l => l.id === parseInt(locationId, 10));
        return loc ? loc.nombre : `Ubicación ${locationId}`;
    };

    const executeAdjust = async (qty, locationId, op) => {
        try {
            if (isMounted.current) setAdjusting(true);
            const endpoint = op === 'add' ? '/stock/add' : '/stock/subtract';

            await api.post(endpoint, {
                productoId: product.id,
                ubicacionId: parseInt(locationId, 10),
                cantidad: qty
            });

            const actionText = op === 'add' ? 'agregadas' : 'quitadas';
            toast.success(`${qty} unidades ${actionText} correctamente`);

            // Reset form and refresh
            if (isMounted.current) setQuantity('');
            await refreshStock();

            if (isMounted.current && onSuccess) {
                onSuccess();
            }
        } catch (error) {
            console.error('Error adjusting stock:', error);
            // Vector 2: Force re-fetch on failure to resync state
            await refreshStock();
        } finally {
            if (isMounted.current) setAdjusting(false);
        }
    };

    const handleAdjust = async (e) => {
        e.preventDefault();

        if (adjusting) return;
        const qty = parseInt(quantity, 10);
        if (!qty || qty <= 0) {
            toast.error('Ingrese una cantidad válida mayor a 0');
            return;
        }

        if (!selectedLocation) {
            toast.error('Seleccione una ubicación');
            return;
        }

        // Issue #3.2: Block subtract if result would go negative (when not in Sales context)
        if (operation === 'subtract' && !allowNegativeSubtract) {
            const currentStock = getStockForLocation(selectedLocation);
            if (currentStock - qty < 0) {
                const locationName = getLocationName(selectedLocation);
                toast.error(`Intento de quitar más productos de los disponibles en ${locationName}`);
                return;
            }
        }

        // Issue #3.3: If in confirmation mode and adding, require a confirmation step
        if (operation === 'add' && confirmationMode) {
            const locationName = getLocationName(selectedLocation);
            setConfirmAddAction({ qty, locationId: selectedLocation, locationName });
            return; // Wait for confirmation
        }

        await executeAdjust(qty, selectedLocation, operation);
    };

    // Handler called when user confirms the add action in confirmation mode
    const handleConfirmAdd = async () => {
        if (!confirmAddAction) return;
        const { qty, locationId } = confirmAddAction;
        setConfirmAddAction(null);
        await executeAdjust(qty, locationId, 'add');
    };

    // Compute total stock from stockLocations (auto-refreshes after adjustments)
    const totalStock = (stockLocations || []).reduce((sum, loc) => sum + loc.cantidad, 0);

    // Context-aware filter:
    // Normal mode  → only show locations with positive stock (clean UI, no noise)
    // Correction mode → show ALL locations including zero/negative (diagnose the problem)
    const visibleLocations = confirmationMode
        ? (stockLocations || [])
        : (stockLocations || []).filter(loc => loc.cantidad > 0);

    return (
        <>
            <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
                <div className="stock-modal" onClick={e => e.stopPropagation()}>
                    <div className="stock-modal-header">
                        <p className="product-info">
                            <strong>{product.descripcion}</strong>
                            {product.codigo && <span className="product-code"> (Cód: {product.codigo})</span>}
                        </p>
                    </div>

                    {loading ? (
                        <div className="stock-loading">Cargando...</div>
                    ) : (
                        <>
                            {/* Current Stock by Location */}
                            <div className="stock-modal-body">
                                <h3>Stock por Ubicación</h3>
                                {visibleLocations.length === 0 ? (
                                    <p className="no-stock-message">
                                        Este producto no tiene stock asignado a ninguna ubicación.
                                    </p>
                                ) : (
                                    <table className="stock-table">
                                        <thead>
                                        <tr>
                                            <th>Ubicación</th>
                                            <th>Cantidad</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {(visibleLocations || []).map((loc) => (
                                            <tr key={loc.id} className={loc.cantidad < 0 ? 'negative-stock' : ''}>
                                                <td>{loc.nombreUbicacion}</td>
                                                <td className={loc.cantidad < 0 ? 'negative' : ''}>
                                                    {loc.cantidad} unidades
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                        <tfoot>
                                        <tr className="total-row">
                                            <td><strong>Total</strong></td>
                                            <td><strong>{totalStock} unidades</strong></td>
                                        </tr>
                                        </tfoot>
                                    </table>
                                )}
                            </div>

                            {/* Adjustment Form */}
                            <form onSubmit={handleAdjust} className="adjust-form">
                                <h3>Ajustar Stock</h3>

                                <div className="form-row">
                                    <div className="form-group">
                                        <label htmlFor="location-select">Ubicación</label>
                                        <select
                                            id="location-select"
                                            value={selectedLocation}
                                            onChange={(e) => setSelectedLocation(e.target.value)}
                                            required
                                        >
                                            {(allLocations || []).map((loc) => (
                                                <option key={loc.id} value={loc.id}>
                                                    {loc.nombre} ({getStockForLocation(loc.id)} uds)
                                                </option>
                                            ))}
                                        </select>
                                    </div>

                                    <div className="form-group">
                                        <label htmlFor="quantity-input">Cantidad</label>
                                        <input
                                            id="quantity-input"
                                            type="number"
                                            min="1"
                                            value={quantity}
                                            onChange={(e) => setQuantity(e.target.value)}
                                            placeholder="Ej: 10"
                                            required
                                        />
                                    </div>
                                </div>

                                <div className="operation-buttons">
                                    <button
                                        type="submit"
                                        className={`adjust-btn add ${operation === 'add' ? 'selected' : ''}`}
                                        onClick={() => setOperation('add')}
                                        disabled={adjusting}
                                    >
                                        ➕ Agregar
                                    </button>
                                    <button
                                        type="submit"
                                        className={`adjust-btn subtract ${operation === 'subtract' ? 'selected' : ''}`}
                                        onClick={() => setOperation('subtract')}
                                        disabled={adjusting}
                                    >
                                        ➖ Quitar
                                    </button>
                                </div>
                            </form>
                        </>
                    )}

                    <div className="stock-modal-actions">
                        <button onClick={onClose} className="secondary" autoFocus>
                            Cerrar
                        </button>
                    </div>
                </div>
            </div>

            {/* Issue #3.3: Confirmation modal before adding in correction context */}
            {confirmAddAction && (
                <ConfirmationModal
                    title="Confirmar Agregado de Stock"
                    message={`Está a punto de agregar ${confirmAddAction.qty} unidades en la ubicación ${confirmAddAction.locationName}. ¿Está seguro?`}
                    confirmText="Agregar"
                    cancelText="Cancelar"
                    isWarning={false}
                    onConfirm={handleConfirmAdd}
                    onCancel={() => setConfirmAddAction(null)}
                />
            )}
        </>
    );
}
