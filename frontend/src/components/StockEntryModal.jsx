import { useState, useEffect, useRef } from 'react';
import api from '../services/api';
import toast from 'react-hot-toast';
import VariantConfirmationModal from './VariantConfirmationModal';
import ProductFormModal from './ProductFormModal';
import { blockNonIntegerKeys, blockNonNumericKeys, sanitizeIntegerPaste, sanitizeNumericPaste, enforceMoneyFormat } from '../utils/numericInput';
import './StockEntryModal.css';

export default function StockEntryModal({ onClose, onSuccess }) {
    const [provider, setProvider] = useState('');
    const [invoiceNo, setInvoiceNo] = useState('');
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState([]);
    const [locations, setLocations] = useState([]);
    const [selectedLocationId, setSelectedLocationId] = useState('');

    // Draft Items: { product, quantity, cost, error }
    // Draft Items: { product, quantity, cost, error }
    const [draftItems, setDraftItems] = useState([]);
    const [isSubmitting, setIsSubmitting] = useState(false);

    // Variant Handling State
    const [verifyingIndex, setVerifyingIndex] = useState(null); // Index of item being verified
    const [showVariantModal, setShowVariantModal] = useState(false);
    const [showProductForm, setShowProductForm] = useState(false);
    const [variantSourceProduct, setVariantSourceProduct] = useState(null);
    const [isNewProductContext, setIsNewProductContext] = useState(false); // Track if creating brand new product

    // Mobile Tab State (datos | items)
    const [activeTab, setActiveTab] = useState('datos');
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;
        // Fetch Locations
        const fetchLocations = async () => {
            try {
                const res = await api.get('/locations');
                if (isMounted.current) setLocations(res.data);
                // Default to empty so user must select
            } catch (err) {
                console.error("Error fetching locations", err);
                // Error handled by global api interceptor
            }
        };
        fetchLocations();
        return () => { isMounted.current = false; };
    }, []);

    // --- SEARCH LOGIC ---
    useEffect(() => {
        const fetchProducts = async () => {
            if (!searchQuery) {
                setSearchResults([]);
                return;
            }
            try {
                const res = await api.get('/productos', { params: { search: searchQuery, size: 20 } });
                if (isMounted.current) setSearchResults(res.data.content);
            } catch (err) {
                console.error(err);
            }
        };
        const timeout = setTimeout(fetchProducts, 300);
        return () => clearTimeout(timeout);
    }, [searchQuery]);

    // --- HANDLERS ---
    const addToDraft = (product) => {
        if ((draftItems || []).some(item => item.product.id === product.id)) {
            toast.error("El producto ya está en la lista");
            return;
        }

        setDraftItems(prev => [...prev, {
            product,
            quantity: 1,
            cost: product.precioCosto,
            error: null
        }]);
        // setSearchQuery(''); // User requested to KEEP search query after adding
    };

    const updateItem = (index, field, value) => {
        const newItems = [...draftItems];
        const item = newItems[index];

        if (field === 'quantity') {
            item.quantity = parseInt(value) || 0;
            setDraftItems(newItems);
        } else if (field === 'cost') {
            const newCost = parseFloat(value) || 0;
            item.cost = newCost;

            // VALIDATION with Modal
            const dbCost = item.product.precioCosto;
            if (Math.abs(newCost - dbCost) > 0.01) {
                // Mark error blue (warning)
                item.error = "Costo modificado. Verificando...";
                // Trigger Modal
                setVerifyingIndex(index);
                setShowVariantModal(true);
            } else {
                item.error = null;
            }
            setDraftItems(newItems);
        }
    };

    const handleConfirmVariant = () => {
        // User wants to create variant
        const item = draftItems[verifyingIndex];
        setVariantSourceProduct(item.product);
        setShowVariantModal(false);
        setIsNewProductContext(false);
        setShowProductForm(true); // Open ProductForm
    };

    // New Handlers for "Create New Product"
    const handleCreateNewProduct = () => {
        setVariantSourceProduct(null);
        setIsNewProductContext(true);
        setShowProductForm(true);
    };

    const handleCorrectCost = () => {
        // User wants to correct/revert manual input.
        const newItems = [...draftItems];
        if (verifyingIndex !== null && newItems[verifyingIndex]) {
            // Revert cost to original product cost
            newItems[verifyingIndex].cost = newItems[verifyingIndex].product.precioCosto;
            newItems[verifyingIndex].error = null; // Clear error since it matches now
            setDraftItems(newItems);
            toast.success("Costo restaurado al valor original");
        }
        setShowVariantModal(false);
        setVerifyingIndex(null);
    };

    const handleProductFormSuccess = (newProduct) => {
        if (isNewProductContext) {
            // Brand new product created -> Add to draft list
            addToDraft(newProduct);
        } else {
            // Variant created -> Update existing item
            const newItems = [...draftItems];
            newItems[verifyingIndex] = {
                product: newProduct,
                quantity: newItems[verifyingIndex].quantity,
                cost: newProduct.precioCosto, // Should match what they just created
                error: null
            };
            setDraftItems(newItems);
            setVerifyingIndex(null);
        }
        setShowProductForm(false);
        setVariantSourceProduct(null);
        setIsNewProductContext(false);
    };

    const removeItem = (index) => {
        setDraftItems(prev => prev.filter((_, i) => i !== index));
    };

    const handleSubmit = async () => {
        if (isSubmitting) return;

        if (!provider || invoiceNo === null || invoiceNo === undefined || String(invoiceNo).trim() === '') {
            toast.error("Complete Proveedor y Nro Comprobante");
            return;
        }

        if (!selectedLocationId) {
            toast.error("Seleccione ubicacion para agregar productos");
            return;
        }

        const hasErrors = (draftItems || []).some(i => i.error);
        if (hasErrors) {
            toast.error("Hay items con diferencias de costo. Corrija o cree variantes.");
            return;
        }

        if (!(draftItems || []).length) {
            toast.error("La lista está vacía");
            return;
        }

        try {
            if (isMounted.current) setIsSubmitting(true);
            const payload = {
                proveedor: provider,
                nroComprobante: invoiceNo,
                items: draftItems.map(i => ({
                    productoId: i.product.id,
                    cantidad: i.quantity,
                    costoUnitario: i.cost,
                    ubicacionId: parseInt(selectedLocationId)
                }))
            };

            await api.post('/compras', payload);
            toast.success("Ingreso Registrado Correctamente");
            if (isMounted.current && onSuccess) onSuccess();
        } catch (error) {
            console.error(error);
            // Error handled by global api interceptor
        } finally {
            if (isMounted.current) setIsSubmitting(false);
        }
    };

    const total = (draftItems || []).reduce((sum, item) => sum + (item.quantity * item.cost), 0);

    return (
        <div className="stock-entry-modal-overlay">
            <div className="stock-entry-modal">
                <div className="modal-header">
                    <h2>📥 Registrar Ingreso de Stock</h2>
                    <button onClick={onClose} className="close-btn" disabled={isSubmitting}>×</button>
                </div>

                <div className="modal-body-layout">
                    {/* LEFT: Search & Inputs (Datos Tab on Mobile) */}
                    <div className={`left-panel ${activeTab === 'datos' ? 'active-tab' : ''}`}>
                        <div className="form-group-row">
                            <input
                                className="provider-input"
                                placeholder="Proveedor"
                                value={provider}
                                onChange={e => setProvider(e.target.value)}
                            />
                            <input
                                className="invoice-input"
                                placeholder="Nro Factura"
                                value={invoiceNo}
                                onChange={e => setInvoiceNo(e.target.value)}
                            />
                        </div>

                        <div style={{ marginBottom: '1rem' }}>
                            <select
                                className="location-select"
                                value={selectedLocationId}
                                onChange={e => setSelectedLocationId(e.target.value)}
                            >
                                <option value="">Seleccione ubicación para productos</option>
                                {(locations || []).filter(loc => loc.activo !== false).map(loc => (
                                    <option key={loc.id} value={loc.id}>{loc.nombre}</option>
                                ))}
                            </select>
                        </div>

                        <div className="search-container-row">
                            <input
                                className="search-bar-large"
                                placeholder="🔍 Buscar producto a reponer..."
                                value={searchQuery}
                                onChange={e => setSearchQuery(e.target.value)}
                                autoFocus
                            />
                            <button
                                onClick={handleCreateNewProduct}
                                className="add-product-btn"
                                title="Crear Nuevo Producto"
                            >
                                +
                            </button>
                        </div>

                        <div className="search-results">
                            {(searchResults || []).map(p => (
                                <div key={p.id} className="search-item" onClick={() => addToDraft(p)}>
                                    <strong>{p.descripcion}</strong>
                                    <div>Costo: ${p.precioCosto}</div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* RIGHT: List (Items Tab on Mobile) */}
                    <div className={`right-panel ${activeTab === 'items' ? 'active-tab' : ''}`}>
                        <h3>Items a Ingresar ({(draftItems || []).length})</h3>
                        <div className="draft-list-scroll">
                            {(draftItems || []).map((item, i) => (
                                <div key={i} className={`draft-item ${item.error ? 'item-error' : ''}`}>
                                    <div className="item-info">
                                        <span className="item-name">{item.product.descripcion}</span>
                                        {item.error && <span className="error-badge">⚠️ Costo Difiere</span>}
                                    </div>
                                    <div className="item-inputs">
                                        <div className="input-group-col">
                                            <label>Cantidad</label>
                                            <input
                                                type="text"
                                                inputMode="numeric"
                                                min="1"
                                                className="qty-input"
                                                value={item.quantity}
                                                onChange={e => updateItem(i, 'quantity', e.target.value)}
                                                onKeyDown={blockNonIntegerKeys}
                                                onPaste={sanitizeIntegerPaste}
                                            />
                                        </div>
                                        <div className="input-group-col">
                                            <label>Costo</label>
                                            <input
                                                type="text"
                                                inputMode="decimal"
                                                min="0"
                                                className="cost-input"
                                                value={item.cost}
                                                onBlur={e => updateItem(i, 'cost', e.target.value)}
                                                onChange={e => {
                                                    const val = enforceMoneyFormat(e.target.value);
                                                    const newItems = [...draftItems];
                                                    newItems[i].cost = val;
                                                    setDraftItems(newItems);
                                                }}
                                                onKeyDown={blockNonNumericKeys}
                                                onPaste={sanitizeNumericPaste}
                                            />
                                        </div>
                                        <button onClick={() => removeItem(i)} className="remove-btn">×</button>
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className="footer-actions">
                            <div className="total-display">Total: ${total.toFixed(2)}</div>
                            <button
                                className="submit-btn"
                                onClick={handleSubmit}
                                disabled={isSubmitting}
                            >
                                {isSubmitting ? "GUARDANDO..." : "CONFIRMAR"}
                            </button>
                        </div>
                    </div>
                </div>

                {/* Mobile Tabs Navigation (Visible only on mobile) */}
                <div className="mobile-tabs">
                    <button
                        className={`modal-tab-btn ${activeTab === 'datos' ? 'active' : ''}`}
                        onClick={() => setActiveTab('datos')}
                        type="button"
                    >
                        <span className="icon">📋</span>
                        <span className="label">Datos</span>
                    </button>
                    <button
                        className={`modal-tab-btn ${activeTab === 'items' ? 'active' : ''}`}
                        onClick={() => setActiveTab('items')}
                        type="button"
                    >
                        <span className="icon">📦</span>
                        <span className="label">Items ({draftItems.length})</span>
                    </button>
                </div>
            </div>

            {showVariantModal && (
                <VariantConfirmationModal
                    currentCost={draftItems[verifyingIndex]?.product.precioCosto}
                    newCost={draftItems[verifyingIndex]?.cost}
                    onConfirmVariant={handleConfirmVariant}
                    onCorrect={handleCorrectCost}
                />
            )}

            {showProductForm && (
                <ProductFormModal
                    product={variantSourceProduct}
                    isVariant={!isNewProductContext} // Variant if NOT new product context
                    isPurchaseContext={true} // Always hide location/stock section (handled by StockEntry)
                    initialCost={!isNewProductContext ? draftItems[verifyingIndex]?.cost : undefined}
                    onSuccess={handleProductFormSuccess}
                    onCancel={() => {
                        setShowProductForm(false);
                        setIsNewProductContext(false);
                        if (!isNewProductContext) handleCorrectCost();
                    }}
                />
            )}
        </div>
    );
}
