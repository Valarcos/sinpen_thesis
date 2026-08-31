import { useState, useEffect, useRef, useMemo } from 'react';
import { useOutletContext, useBlocker, useLocation, useNavigate } from 'react-router-dom';
import useCart from '../hooks/useCart';
import api from '../services/api';
import toast from 'react-hot-toast';
import StockWarningModal from '../components/StockWarningModal';
import ConfirmationModal from '../components/ConfirmationModal';
import CheckoutChequeModal from '../components/CheckoutChequeModal';
import ProductConfigModal from '../components/ProductConfigModal';
import ClientChangeModal from '../components/ClientChangeModal';
import { generateReceipt } from '../utils/pdfGenerator';
import { blockNonNumericKeys, blockNonIntegerKeys, sanitizeNumericPaste, sanitizeIntegerPaste, enforceMoneyFormat } from '../utils/numericInput';
import './VentaPage.css';

/**
 * Groups raw product variants into family objects for the product grid.
 * Family key rules:
 *   - Standard (codigo != '1'): key = codigo
 *   - Generic  (codigo == '1'): key = '1|descripcion' (trimmed, lowercased)
 * The family representative exposes the newest variant's prices and the accumulated total stock.
 * Individual siblings are stored in _siblings[] for the inline picker.
 */
function groupProducts(rawProducts) {
    const familyMap = new Map();
    rawProducts.forEach(product => {
        const key = product.codigo !== '1'
            ? product.codigo
            : `1|${product.descripcion.trim().toLowerCase()}`;

        if (!familyMap.has(key)) {
            familyMap.set(key, {
                ...product,
                cantidadStock: 0,
                _siblings: [],
                _isGrouped: false
            });
        }

        const family = familyMap.get(key);
        family._siblings.push(product);
        family.cantidadStock += product.cantidadStock;

        // Always use the newest (highest ID) variant as the family price representative
        if (product.id > family.id) {
            family.id = product.id;
            family.precioCosto = product.precioCosto;
            family.precioMinorista = product.precioMinorista;
            family.precioMayorista = product.precioMayorista;
        }

        family._isGrouped = family._siblings.length > 1;
    });

    return Array.from(familyMap.values());
}

export default function VentaPage() {
    const {
        cartItems,
        clientName,
        setClientName,
        payments,
        saleType,
        setSaleType,
        addToCart,
        updateQuantity,
        updateProductData, // Adding just in case existing code relies on it
        updateMultipleProductsData,
        updateItemDiscount, // Restored
        updateItemSubItems, // New
        globalDiscount, // New
        setGlobalDiscount, // New
        globalSurcharge,
        setGlobalSurcharge,
        removeFromCart,
        addPaymentMethod,
        removePaymentMethod,
        totals,
        initialClientName,
        initialClientId,
        cartVersion,
        deletedPayments,
        deletedCheques,
        saldoGenerado,
        setSaldoGenerado,
        loadCartFromPendingSale
    } = useCart();

    // Variant picker state: tracks which family card is expanded
    const [expandedFamilyKey, setExpandedFamilyKey] = useState(null);

    // ... existing state ...
    const [lastSale, setLastSale] = useState(null); // Stores successful sale data for receipt

    // New: Editing Pending Sale State
    const location = useLocation();
    const navigate = useNavigate();
    const [editingPendingId, setEditingPendingId] = useState(null);
    const isRedirectingRef = useRef(false);

    // Issue #11 fix: only block navigation if cart has items AND sale is NOT yet completed
    const blocker = useBlocker(({ currentLocation, nextLocation }) => {
        if (isRedirectingRef.current) return false;
        // Ignore navigation if we are staying on the same page (e.g. clearing router state)
        if (currentLocation.pathname === nextLocation.pathname) return false;

        return cartItems.length > 0 && !lastSale;
    });
    useEffect(() => {
        if (blocker.state === 'blocked') {
            const leave = window.confirm('⚠️ ¿Desea salir?\n\nTiene productos en el carrito que se perderán si abandona esta página.');
            if (leave) {
                blocker.proceed();
            } else {
                blocker.reset();
            }
        }
    }, [blocker]);

    const [products, setProducts] = useState([]);
    const [paymentMethods, setPaymentMethods] = useState([]);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [cartSearchQuery, setCartSearchQuery] = useState(''); // New state for cart search
    const { salesActiveTab: activeTab } = useOutletContext();
    const [loading, setLoading] = useState(false);
    const [availableClients, setAvailableClients] = useState([]); // New Autocomplete State

    // Fetch Clients for Autocomplete — uses full ClienteResponse objects so we can
    // read saldo_a_favor for the payment method conditional logic.
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;
        return () => { isMounted.current = false; };
    }, []);

    useEffect(() => {
        const fetchClients = async () => {
            try {
                const res = await api.get('/clientes');
                if (isMounted.current) setAvailableClients(res.data);
            } catch (err) {
                console.error("Error loading clients", err);
            }
        };
        fetchClients();
    }, []);

    // Load Pending Sale if provided in state
    useEffect(() => {
        if (location.state?.pendingSaleToEdit && paymentMethods.length > 0) {
            const sale = location.state.pendingSaleToEdit;
            setEditingPendingId(sale.id);
            loadCartFromPendingSale(sale, paymentMethods);

            // Fetch actual catalog prices for items in the pending sale
            // Since pending sales only store a snapshot, we need real prices
            // for the saleType toggle (Minorista/Mayorista) to correctly recalculate.
            if (sale.items && sale.items.length > 0) {
                const itemIds = [...new Set(sale.items.map(i => i.productoId || i.id).filter(Boolean))];
                api.post('/productos/bulk', itemIds)
                    .then(r => {
                        if (r.data && r.data.length > 0) {
                            updateMultipleProductsData(r.data);
                        }
                    })
                    .catch(err => {
                        console.error('Failed to fetch bulk products for pending cart:', err);
                    });
            }

            // Clear state so it doesn't reload on refresh
            navigate(location.pathname, { replace: true, state: {} });
        }
    }, [location.state, paymentMethods, loadCartFromPendingSale, updateMultipleProductsData, navigate]);

    // Stock Warning State
    const [affectedProducts, setAffectedProducts] = useState([]);
    const [showStockModal, setShowStockModal] = useState(false);
    // Tracks the action that triggered the stock modal so onContinue routes correctly
    const [pendingAction, setPendingAction] = useState(null); // 'FINALIZE' | 'SAVE_PENDING' | null

    // Debt Warning State
    const [showDebtModal, setShowDebtModal] = useState(false);

    // Payment Form State
    const [selectedMethodId, setSelectedMethodId] = useState('');
    const [paymentAmount, setPaymentAmount] = useState('');

    // Overpayment Modal State (Issue #8)
    const [showOverpaidModal, setShowOverpaidModal] = useState(false);
    const [overpaidMaxAllowed, setOverpaidMaxAllowed] = useState(0);

    // Cheque Modal State
    const [showChequeModal, setShowChequeModal] = useState(false);
    const [pendingChequeAmount, setPendingChequeAmount] = useState(0);

    const [configModalOpen, setConfigModalOpen] = useState(false);
    const [configModalItem, setConfigModalItem] = useState(null);

    const [showClientChangeModal, setShowClientChangeModal] = useState(false);

    // Req 4: Ref for payment amount input — enables auto-focus + select when a method is chosen.
    const paymentAmountRef = useRef(null);

    // Req: Ref for search input — enables auto-focus + select on mount and after adding to cart
    const searchInputRef = useRef(null);

    // Anti-spam lock to prevent overlapping API calls if Enter is mashed rapidly
    const isEnterSearchingRef = useRef(false);

    const focusAndSelectSearch = () => {
        if (searchInputRef.current) {
            searchInputRef.current.focus();
            searchInputRef.current.select();
        }
    };

    // Auto-focus and select search bar on mount
    useEffect(() => {
        focusAndSelectSearch();
    }, []);

    // Req: Auto-scroll to bottom of cart when a NEW item is added
    const cartListRef = useRef(null);
    const prevCartLength = useRef(0);
    useEffect(() => {
        if (cartItems.length > prevCartLength.current) {
            if (cartListRef.current) {
                cartListRef.current.scrollTop = cartListRef.current.scrollHeight;
            }
        }
        prevCartLength.current = cartItems.length;
    }, [cartItems.length]);

    // Req 1: Local display buffer for quantity inputs.
    // useCart's updateQuantity guards against values < 1, so storing '' in cart state is not possible.
    // This Map (productId -> displayString) acts as an independent controlled-input buffer.
    // hasInvalidQty reads from this map to determine if any input is currently in an empty/zero state.
    // CRITICAL EXCEPTION: Discount inputs (item.discount, globalDiscount) are excluded by design —
    // an empty or zero discount is 100% valid per business rules and MUST NOT block the sale.
    const [localQtyValues, setLocalQtyValues] = useState({});

    // Req 1: Tracks the ID of the last quantity-input that was blurred while in an invalid state.
    // Only one warning label renders at a time, preventing visual pollution when multiple fields exist.
    const [lastInvalidFieldId, setLastInvalidFieldId] = useState(null);

    // Issue #7 + #9: Warn when payments exceed total (due to discount or sale type change)
    useEffect(() => {
        if (totals.isOverpaid) {
            toast.error("Ajustar montos y métodos de pago", { id: 'overpaid-warning' });
        }
    }, [totals.isOverpaid]);

    // --- LOGIC: Validate Stock ---
    const checkStockAvailability = () => {
        const issues = [];
        cartItems.forEach(item => {
            const originalReserved = item.originalReservedQuantity || 0;
            const availableStock = item.product.cantidadStock + originalReserved;

            if (item.quantity > availableStock) {
                issues.push({ ...item.product, cantidadStock: availableStock, cartQuantity: item.quantity });
            }
        });
        return issues;
    };

    const handlePrePaymentCheck = () => {
        // Sync any un-blurred inputs BEFORE checking stock!
        let hasPendingSync = false;
        Object.keys(localQtyValues).forEach(id => {
            const raw = localQtyValues[id];
            if (raw !== undefined) {
                const val = parseInt(raw, 10);
                if (!isNaN(val) && val >= 1) {
                    updateQuantity(Number(id), val);
                    hasPendingSync = true;
                }
            }
        });

        if (hasPendingSync) {
            setLocalQtyValues({}); // Clear the buffer
            setTimeout(() => handlePrePaymentCheck(), 0); // Retry after React state propagates
            return;
        }

        const issues = checkStockAvailability();
        if (issues.length > 0) {
            setPendingAction('FINALIZE'); // Stamp intent before showing modal
            setAffectedProducts(issues);
            setShowStockModal(true);
        } else {
            // Check for Debt logic
            // User Rule: "When the button is clicked with an amount of money that is not accounted for,
            // a warning modal must appear"
            const totalPaid = payments.reduce((sum, p) => sum + p.amount, 0);
            const remaining = totals.total - totalPaid;

            // Floating point safety check
            if (remaining > 0.01) {
                setShowDebtModal(true);
            } else {
                handleFinalizeSale();
            }
        }
    };

    /**
     * Handles a stock correction from StockWarningModal.
     * Fetches only the single corrected product (targeted GET) to avoid:
     *   1. Pagination misses if the product is on page 2+.
     *   2. Overwriting the catalog grid state (setProducts) with a single item.
     *
     * @param {number} productId - The ID of the product that was just corrected.
     */
    const handleStockCorrected = async (productId) => {
        try {
            // Targeted fetch: only the corrected product. Does NOT touch setProducts()
            // so the user's catalog search results remain intact.
            const res = await api.get(`/productos/${productId}`);
            const freshProduct = res.data;

            if (freshProduct) {
                // Update only this one product in the cart state
                updateProductData(freshProduct);
            }

            // Re-evaluate which items still have stock issues using updated cart data
            // We must compute this from the current cartItems + the fresh product data
            const updatedIssues = [];
            cartItems.forEach(item => {
                const originalReserved = item.originalReservedQuantity || 0;
                // Use freshProduct for the corrected one, otherwise use existing cart data
                const stockSource = item.product.id === productId ? freshProduct : item.product;
                if (!stockSource) return;
                const availableStock = stockSource.cantidadStock + originalReserved;
                if (item.quantity > availableStock) {
                    updatedIssues.push({ ...stockSource, cantidadStock: availableStock, cartQuantity: item.quantity });
                }
            });

            setAffectedProducts(updatedIssues);

            if (updatedIssues.length === 0) {
                // Auto-close modal if all issues are resolved — user must then re-click their action button
                setShowStockModal(false);
                setPendingAction(null); // Clear intent — user will re-confirm their action
            }
        } catch (error) {
            console.error('Error refreshing after stock correction', error);
            toast.error('Error al verificar el stock corregido');
        }
    };

    const handleFinalizeSale = async () => {
        if (isSubmitting) return;

        // 0. Empty check
        if (!cartItems || cartItems.length === 0) {
            toast.error("El carrito está vacío");
            return;
        }

        if (saleType === 'FIADO' && !clientName.trim()) {
            toast.error("Para FIADO, debe ingresar el Nombre del Cliente");
            return;
        }

        const totalPaid = payments.reduce((sum, p) => sum + p.amount, 0);

        setShowStockModal(false);
        setShowDebtModal(false);

        try {
            setIsSubmitting(true);

            // Split payments into standard pagos and cheques for the unified backend endpoint.
            const pagosPayload = payments
                .filter(p => !p.fechaCobro)
                .map(p => ({ metodoPagoId: p.methodId, monto: p.amount }));

            const chequesPayload = payments
                .filter(p => !!p.fechaCobro)
                .map(p => ({ monto: p.amount, fechaCobro: p.fechaCobro }));

            const saleData = {
                version: cartVersion,
                clienteNombre: clientName,
                clienteId: selectedClientObj?.id || null,
                tipoVenta: saleType,
                descuentoGlobal: globalDiscount,
                recargoGlobal: globalSurcharge,
                saldoGenerado: saldoGenerado,
                items: cartItems.flatMap(item => {
                    if (item.subItems && item.subItems.length > 0) {
                        return item.subItems.map(sub => ({
                            productoId: item.product.id,
                            cantidad: sub.quantity,
                            valorDescuento: sub.discount || 0,
                            razonDescuento: sub.reason || null
                        }));
                    }
                    return [{
                        productoId: item.product.id,
                        cantidad: item.quantity,
                        valorDescuento: item.discount || 0
                    }];
                }),
                pagos: pagosPayload,
                cheques: chequesPayload.length > 0 ? chequesPayload : undefined
            };

            let response;
            if (editingPendingId) {
                // Deletions
                for (const pid of deletedPayments) {
                    await api.delete(`/ventas/${editingPendingId}/pagos/${pid}`);
                }
                for (const cid of deletedCheques) {
                    await api.delete(`/alertas/cheques/${cid}`);
                }

                // 1. Update Cart
                await api.put(`/ventas/${editingPendingId}`, saleData);

                // 2. Register NEW payments (only non-persisted ones, filtering by absence of an id)
                const newPayments = payments.filter(p => !p.id);
                if (newPayments.length > 0) {
                    const pagosNew = newPayments.map(p => ({
                        montoPago: p.amount,
                        metodoPagoId: p.methodId,
                        fechaCobro: p.fechaCobro || null,
                        observaciones: ''
                    }));
                    if (pagosNew.length > 0) {
                        await api.post(`/ventas/${editingPendingId}/pagos`, pagosNew);
                    }
                }

                // 3. Finalize
                response = await api.post(`/ventas/${editingPendingId}/finalizar`);
                toast.success("Pedido finalizado con éxito");
            } else {
                response = await api.post('/ventas', saleData);
                toast.success("Venta registrada con éxito");
            }

            if (response.data.alertas && response.data.alertas.length > 0) {
                response.data.alertas.forEach(alert => toast(alert, { icon: '⚠️' }));
            }

            setLastSale({
                id: response.data.id,
                date: new Date(),
                client: clientName,
                user: localStorage.getItem('userName') || 'Sistema',
                saleType: saleType,
                items: cartItems.flatMap(i => {
                    if (i.subItems && i.subItems.length > 0) {
                        return i.subItems.map(sub => ({
                            ...i.product,
                            quantity: sub.quantity,
                            unitPrice: i.unitPrice,
                            discount: sub.discount,
                            reason: sub.reason
                        }));
                    }
                    return [{
                        ...i.product,
                        quantity: i.quantity,
                        unitPrice: i.unitPrice,
                        discount: i.discount,
                        reason: i.reason || null
                    }];
                }),
                payments: payments,
                total: totals.total,
                globalDiscount: globalDiscount,
                globalSurcharge: globalSurcharge
            });
        } catch (error) {
            console.error("Sale Error:", error);
            const msg = error.response?.data?.message;
            if (msg) toast.error(msg);
        } finally {
            if (isMounted.current) setIsSubmitting(false);
        }
    };

    /**
     * Executes the actual pending-sale API call after all pre-checks pass.
     * Defined as a separate function so it can be called BOTH from handleSaveAsPending
     * (direct path) AND from the StockWarningModal's onContinue callback (via pendingAction='SAVE_PENDING').
     * Kept inside VentaPage to maintain closure over fresh React state.
     */
    const executeSaveAsPending = async (overrideClientId, overrideClientName) => {
        try {
            setIsSubmitting(true);
            const pagosPayload = payments
                .filter(p => !p.fechaCobro)
                .map(p => ({ metodoPagoId: p.methodId, monto: p.amount }));

            const chequesPayload = payments
                .filter(p => !!p.fechaCobro)
                .map(p => ({ monto: p.amount, fechaCobro: p.fechaCobro }));

            const finalClientId = overrideClientId !== undefined ? overrideClientId : (selectedClientObj?.id || null);
            const finalClientName = overrideClientName !== undefined ? overrideClientName : clientName;

            const saleData = {
                version: cartVersion,
                clienteNombre: finalClientName,
                clienteId: finalClientId,
                tipoVenta: saleType,
                descuentoGlobal: globalDiscount,
                recargoGlobal: globalSurcharge,
                saldoGenerado: saldoGenerado,
                items: cartItems.flatMap(item => {
                    if (item.subItems && item.subItems.length > 0) {
                        return item.subItems.map(sub => ({
                            productoId: item.product.id,
                            cantidad: sub.quantity,
                            valorDescuento: sub.discount || 0,
                            razonDescuento: sub.reason || null
                        }));
                    }
                    return [{
                        productoId: item.product.id,
                        cantidad: item.quantity,
                        valorDescuento: item.discount || 0
                    }];
                }),
                pagos: pagosPayload,
                cheques: chequesPayload.length > 0 ? chequesPayload : undefined
            };

            if (editingPendingId) {
                for (const pid of deletedPayments) {
                    await api.delete(`/ventas/${editingPendingId}/pagos/${pid}`);
                }
                for (const cid of deletedCheques) {
                    await api.delete(`/alertas/cheques/${cid}`);
                }

                await api.put(`/ventas/${editingPendingId}`, saleData);
                // Also save new payments if any
                const newPayments = payments.filter(p => !p.id);
                if (newPayments.length > 0) {
                    const pagosNew = newPayments.map(p => ({
                        montoPago: p.amount,
                        metodoPagoId: p.methodId,
                        fechaCobro: p.fechaCobro || null,
                        observaciones: ""
                    }));
                    if (pagosNew.length > 0) {
                        await api.post(`/ventas/${editingPendingId}/pagos`, pagosNew);
                    }
                }
                toast.success("Pedido pendiente actualizado exitosamente.");
                isRedirectingRef.current = true;
                navigate('/cobros-y-pedidos', { state: { highlightedSaleId: editingPendingId } });
            } else {
                await api.post('/ventas/pendientes', saleData);
                toast.success("Pedido guardado como pendiente exitosamente.");
                handleNewSale();
            }
        } catch (error) {
            console.error("Pending Sale Error:", error);
            const msg = error.response?.data?.message;
            if (msg) toast.error(msg);
        } finally {
            if (isMounted.current) {
                setIsSubmitting(false);
                setPendingAction(null); // Always clear intent after execution
            }
        }
    };

    const handleSaveAsPending = async () => {
        if (isSubmitting) return;

        if (!cartItems || cartItems.length === 0) {
            toast.error("El carrito está vacío");
            return;
        }

        if (!clientName.trim()) {
            toast.error("Debe ingresar el Nombre del Cliente para guardar como pendiente");
            return;
        }

        if (editingPendingId && initialClientId && clientName.trim() !== initialClientName.trim()) {
            setShowClientChangeModal(true);
            return;
        }

        // Sync any un-blurred inputs BEFORE checking stock!
        let hasPendingSync = false;
        Object.keys(localQtyValues).forEach(id => {
            const raw = localQtyValues[id];
            if (raw !== undefined) {
                const val = parseInt(raw, 10);
                if (!isNaN(val) && val >= 1) {
                    updateQuantity(Number(id), val);
                    hasPendingSync = true;
                }
            }
        });

        if (hasPendingSync) {
            setLocalQtyValues({}); // Clear the buffer
            setTimeout(() => handleSaveAsPending(), 0); // Retry after React state propagates
            return;
        }

        const issues = checkStockAvailability();
        if (issues.length > 0) {
            setPendingAction('SAVE_PENDING'); // Stamp intent before showing modal
            setAffectedProducts(issues);
            setShowStockModal(true);
            return;
        }

        await executeSaveAsPending();
    };

    // New Handler for Reset
    const handleNewSale = () => {
        setLastSale(null);
        window.location.reload(); // Simple reset for MVP
    };

    const handlePrintReceipt = () => {
        if (lastSale) generateReceipt(lastSale);
    };

    // --- EFFECT: Fetch Payment Methods (Run Once) ---
    useEffect(() => {
        const fetchPaymentMethods = async () => {
            try {
                const res = await api.get('/ventas/metodos-pago');
                if (isMounted.current) setPaymentMethods(res.data);
            } catch (error) {
                console.error("Error fetching payment methods:", error);
                // toast.error("Error al cargar métodos de pago");
            }
        };
        fetchPaymentMethods();
    }, []);

    // --- EFFECT: Fetch Products (Debounced Search) ---
    useEffect(() => {
        const fetchProducts = async () => {
            setLoading(true);
            try {
                const params = { size: 50 };
                // If search is empty, backend might return nothing or all?
                // .cursorrules says "Search" does LIMIT 100.
                if (searchQuery) params.search = searchQuery;

                const response = await api.get('/productos', { params });
                if (isMounted.current) {
                    setProducts(groupProducts(response.data.content || []));
                }
            } catch (error) {
                console.error("Error fetching products:", error);
                // toast.error handled by interceptor if any
            } finally {
                if (isMounted.current) setLoading(false);
            }
        };

        const timeoutId = setTimeout(() => {
            fetchProducts();
        }, 300);

        return () => clearTimeout(timeoutId);
    }, [searchQuery]);

    // --- HANDLERS ---


    /**
     * Handles a click on a product card in the grid.
     * Single-variant cards add directly to cart.
     * Multi-variant (family) cards toggle the inline variant picker.
     */
    const handleFamilyCardClick = (product) => {
        if (!product._isGrouped) {
            handleAddToCart(product);
            return;
        }
        const key = product.codigo !== '1'
            ? product.codigo
            : `1|${product.descripcion.trim().toLowerCase()}`;
        setExpandedFamilyKey(prev => prev === key ? null : key);
    };

    const [pendingSaleType, setPendingSaleType] = useState(null);

    // Req: Global focus hijacking back to search bar
    // Placed here to avoid ReferenceError: Cannot access 'pendingProductToAdd' before initialization
    useEffect(() => {
        const handleGlobalClick = (e) => {
            // Do not hijack focus if any modal is open
            if (showStockModal || showDebtModal || showOverpaidModal || pendingSaleType) {
                return;
            }

            // Do not hijack focus on touch devices (mobiles/tablets) to avoid virtual keyboard popping up
            if (window.matchMedia && !window.matchMedia('(pointer: fine)').matches) {
                return;
            }

            setTimeout(() => {
                // Safety check: if a modal just opened in this render cycle, do not steal focus
                if (document.querySelector('.modal-overlay')) {
                    return;
                }

                const activeTag = document.activeElement?.tagName?.toLowerCase();
                const isInput = ['input', 'textarea', 'select'].includes(activeTag);

                if (!isInput && searchInputRef.current) {
                    searchInputRef.current.focus();
                    searchInputRef.current.select();
                }
            }, 0);
        };

        document.addEventListener('click', handleGlobalClick);
        return () => {
            document.removeEventListener('click', handleGlobalClick);
        };
    }, [showStockModal, showDebtModal, showOverpaidModal, pendingSaleType]);

    // MODIFIED: Simplified Add to Cart
    const handleAddToCart = (product) => {
        // Find existing quantity in cart
        const existingItem = cartItems.find(item => item.product.id === product.id);
        const currentQty = existingItem ? existingItem.quantity : 0;
        const originalReserved = existingItem ? (existingItem.originalReservedQuantity || 0) : 0;
        const availableStock = product.cantidadStock + originalReserved;

        // Check against stock. If it exceeds, just show a warning toast, but STILL ADD IT.
        if (currentQty + 1 > availableStock) {
            addToCart(product);
            toast.success(`Agregado (Sin Stock): ${product.descripcion}`, { icon: '⚠️', duration: 2000, position: 'bottom-left' });
        } else {
            addToCart(product);
            toast.success(`Agregado: ${product.descripcion}`, { duration: 1000, position: 'bottom-left' });
        }

        // Focus and select the search bar so the user can easily type the next item
        setTimeout(focusAndSelectSearch, 0);
    };

    const handleSaleTypeChange = (type) => {
        if (cartItems.length > 0 && type !== saleType) {
            setPendingSaleType(type);
        } else {
            setSaleType(type);
        }
    };

    const confirmSaleTypeChange = () => {
        if (pendingSaleType) {
            setSaleType(pendingSaleType);
            setPendingSaleType(null);
        }
    };

    // Determines if the currently selected payment method is a cheque type.
    // This drives the dynamic date input in the payment row.
    const isSelectedMethodCheque = () => {
        if (!selectedMethodId) return false;
        const method = paymentMethods.find(m => m.id === parseInt(selectedMethodId));
        if (!method) return false;
        const desc = (method.descripcion || '').toLowerCase();
        return desc.includes('cheque') || desc.includes('e-check');
    };

    const handleAddPayment = () => {
        if (!selectedMethodId) {
            toast.error("Seleccione un método de pago");
            return;
        }
        const amount = parseFloat(paymentAmount);
        if (isNaN(amount) || amount <= 0) {
            toast.error("Ingrese un monto válido");
            return;
        }

        // Issue #8: Detect overpayment
        const currentPaid = payments.reduce((sum, p) => sum + p.amount, 0);
        const maxAllowed = Math.max(0, totals.total - currentPaid);
        if (amount > maxAllowed + 0.01) {
            setOverpaidMaxAllowed(maxAllowed);
            setShowOverpaidModal(true);
            return;
        }

        // HALT LOGIC: If the selected method is a cheque, open the modal to capture cheques
        if (isSelectedMethodCheque()) {
            setPendingChequeAmount(amount);
            setShowChequeModal(true);
            return;
        }
        const method = paymentMethods.find(m => m.id === parseInt(selectedMethodId));
        addPaymentMethod({
            methodId: method.id,
            name: method.descripcion,
            amount: amount,
            fechaCobro: null
        });

        // Reset form
        setSelectedMethodId('');
        setPaymentAmount('');
    };

    const handleChequesConfirm = (chequesArray) => {
        const method = paymentMethods.find(m => m.id === parseInt(selectedMethodId));
        chequesArray.forEach(cheque => {
            addPaymentMethod({
                methodId: method.id,
                name: method.descripcion,
                amount: parseFloat(cheque.monto),
                fechaCobro: cheque.fechaCobro
            });
        });

        setShowChequeModal(false);
        setSelectedMethodId('');
        setPaymentAmount('');
    };

    // Issue #8: Auto-correct handler for overpayment modal
    const handleAutoCorrectPayment = () => {
        setShowOverpaidModal(false);
        const method = paymentMethods.find(m => m.id === parseInt(selectedMethodId));
        if (method && overpaidMaxAllowed > 0) {
            if (isSelectedMethodCheque()) {
                setPendingChequeAmount(overpaidMaxAllowed);
                setShowChequeModal(true);
            } else {
                addPaymentMethod({
                    methodId: method.id,
                    name: method.descripcion,
                    amount: overpaidMaxAllowed
                });
                setSelectedMethodId('');
                setPaymentAmount('');
            }
        } else {
            setPaymentAmount(overpaidMaxAllowed.toFixed(2));
        }
    };

    const handleSaldoAFavor = () => {
        setShowOverpaidModal(false);
        const method = paymentMethods.find(m => m.id === parseInt(selectedMethodId));
        const amount = parseFloat(paymentAmount);
        if (method && amount > 0) {
            const extraAmount = amount - overpaidMaxAllowed;
            setSaldoGenerado(prev => prev + extraAmount);

            if (isSelectedMethodCheque()) {
                setPendingChequeAmount(amount);
                setShowChequeModal(true);
            } else {
                addPaymentMethod({
                    methodId: method.id,
                    name: method.descripcion,
                    amount: amount
                });
                setSelectedMethodId('');
                setPaymentAmount('');
            }
        }
    };

    const formatCurrency = (amount) => {
        if (amount === undefined || amount === null) return '$ 0,00';
        const numStr = new Intl.NumberFormat('es-AR', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(amount);
        return `$ ${numStr}`;
    };

    // Calculate remaining amount to pay
    const totalPaid = payments.reduce((sum, p) => sum + p.amount, 0);
    const remaining = totals.total - totalPaid;

    // Smart Dropdown: Filter out methods that are already used in the current payment stack.
    // SALDO (Saldo a Favor) is additionally gated:
    //   - Only available once a client name has been entered AND matched to a registered client.
    //   - Only available when that client has saldo_a_favor > 0.
    //   - A registered client is identified by finding a matching ClienteResponse by nombre.
    const selectedClientObj = availableClients.find(
        c => c.nombre?.trim().toLowerCase() === clientName?.trim().toLowerCase()
    ) ?? null;
    const clientHasSaldo = selectedClientObj != null && (selectedClientObj.saldoAFavor ?? 0) > 0;

    const availableMethods = paymentMethods.filter(m => {
        if (m.activo === false) return false;
        const acronimo = (m.acronimo || '').toUpperCase();
        // Hide SALDO method entirely when no matching client with available balance
        if (acronimo === 'SALDO' && !clientHasSaldo) return false;
        return true;
    });


    // Auto-fill amount logic: When selecting a method, autofill with remaining.
    // For SALDO, autofill with the lesser of (remaining) and (client saldo_a_favor)
    // to prevent the cashier from accidentally entering more than the client has.
    const handleMethodSelect = (e) => {
        const methodId = e.target.value;
        setSelectedMethodId(methodId);

        const method = paymentMethods.find(m => m.id === parseInt(methodId));
        if (method) {
            const desc = (method.descripcion || '').toLowerCase();
            const acronimo = (method.acronimo || '').toUpperCase();
            const isCheque = desc.includes('cheque') || desc.includes('e-check');

            if (isCheque) {
                setPendingChequeAmount(remaining > 0.01 ? remaining : 0);
                setShowChequeModal(true);
                return;
            }

            if (acronimo === 'SALDO' && selectedClientObj) {
                // Cap at the lesser of: (amount still owed) and (client's available credit)
                const clientSaldo = selectedClientObj.saldoAFavor ?? 0;
                const saldoToUse = Math.min(remaining > 0.01 ? remaining : 0, clientSaldo);
                setPaymentAmount(saldoToUse > 0 ? saldoToUse.toFixed(2) : '');
                setTimeout(() => { paymentAmountRef.current?.focus(); paymentAmountRef.current?.select(); }, 0);
                return;
            }
        }

        if (remaining > 0.01) {
            setPaymentAmount(remaining.toFixed(2));
        } else {
            setPaymentAmount('');
        }
        // Req 4: After auto-filling the amount, focus and select the input so the user can
        // immediately overwrite the value on all devices (desktop & mobile).
        setTimeout(() => {
            paymentAmountRef.current?.focus();
            paymentAmountRef.current?.select();
        }, 0);
    };

    // Req 1: Determines whether any cart item has an invalid quantity in the local display buffer.
    // We check localQtyValues (the per-input display state) rather than cartItems because
    // useCart's updateQuantity rejects empty/zero values and won't store them in cart state.
    // CRITICAL EXCEPTION: Discount fields are intentionally excluded (empty discount = $0, which is valid).
    const hasInvalidQty = cartItems.some(item => {
        const localVal = localQtyValues[item.product.id];
        // If the local buffer has an entry for this item, check if it's empty or zero
        if (localVal !== undefined) {
            return localVal === '' || Number(localVal) <= 0;
        }
        // No local buffer entry means the cart holds the canonical value (always valid, >= 1)
        return false;
    });

    const filteredCartItems = useMemo(() => {
        if (!cartSearchQuery.trim()) return cartItems;

        const query = cartSearchQuery.trim().toLowerCase();
        const isNumericSearch = /^\d+$/.test(query);

        const filtered = cartItems.filter(item => {
            const code = item.product.codigo.toLowerCase();
            if (isNumericSearch) {
                return code.startsWith(query);
            }
            return code.includes(query) || item.product.descripcion.toLowerCase().includes(query);
        });

        // Sort logic: Exact matches at bottom, shorter matches near bottom, preserve original index for ties
        filtered.sort((a, b) => {
            const aExact = a.product.codigo.toLowerCase() === query;
            const bExact = b.product.codigo.toLowerCase() === query;

            if (aExact && !bExact) return 1;
            if (!aExact && bExact) return -1;

            if (a.product.codigo.length !== b.product.codigo.length) {
                return b.product.codigo.length - a.product.codigo.length;
            }

            return cartItems.indexOf(a) - cartItems.indexOf(b);
        });

        return filtered;
    }, [cartItems, cartSearchQuery]);

    // --- RENDER ---
    if (lastSale) {
        return (
            <div className="venta-page success-view" style={{ justifyContent: 'center', alignItems: 'center', flexDirection: 'column' }}>
                <div style={{ textAlign: 'center', padding: '2rem', background: 'white', borderRadius: '8px', boxShadow: '0 4px 6px rgba(0,0,0,0.1)' }}>
                    <h2 style={{ color: 'green', fontSize: '2rem' }}>¡Venta Exitosa!</h2>
                    <p>ID: #{lastSale.id}</p>
                    <p>Total: {formatCurrency(lastSale.total)}</p>

                    <div style={{ display: 'flex', gap: '1rem', marginTop: '2rem' }}>
                        <button
                            onClick={handlePrintReceipt}
                            style={{ padding: '1rem 2rem', fontSize: '1.2rem', background: '#007bff', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
                        >
                            🖨️ Imprimir Presupuesto
                        </button>
                        <button
                            onClick={handleNewSale}
                            style={{ padding: '1rem 2rem', fontSize: '1.2rem', background: '#28a745', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
                        >
                            ✨ Nueva Venta
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="venta-page">
            <div className={`catalog-panel ${activeTab === 'catalog' ? 'active' : ''}`}>
                <div className="catalog-header">
                    <input
                        ref={searchInputRef}
                        type="text"
                        className="search-bar-large"
                        placeholder="🔍 Buscar producto..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        onKeyDown={async (e) => {
                            // Ignore key-hold repeats to prevent spamming cart on a stuck Enter key
                            if ((e.key === 'Enter' || e.keyCode === 13) && !e.repeat) {
                                e.preventDefault();

                                // Use e.target.value to get the absolute latest string from the DOM,
                                // because rapid barcode scanners might fire Enter before React state fully updates
                                const currentSearchValue = e.target.value;

                                // Prevent overlapping network calls if the user rapidly mashes the Enter key
                                if (!currentSearchValue.trim() || isEnterSearchingRef.current) return;

                                try {
                                    isEnterSearchingRef.current = true;

                                    // Bypass debounce and fetch immediately to support rapid barcode scanners
                                    const params = { size: 50, search: currentSearchValue };
                                    const response = await api.get('/productos', { params });
                                    const fetchedProducts = groupProducts(response.data.content || []);
                                    setProducts(fetchedProducts);

                                    const exactMatch = fetchedProducts.find(p => p.codigo === currentSearchValue);

                                    if (exactMatch) {
                                        if (!exactMatch._isGrouped) {
                                            handleAddToCart(exactMatch);
                                            setSearchQuery('');
                                        } else {
                                            const key = exactMatch.codigo !== '1'
                                                ? exactMatch.codigo
                                                : `1|${exactMatch.descripcion.trim().toLowerCase()}`;
                                            setExpandedFamilyKey(prev => prev === key ? null : key);
                                        }
                                    } else if (fetchedProducts.length === 1) {
                                        const single = fetchedProducts[0];
                                        if (!single._isGrouped) {
                                            // Single variant: add directly to cart
                                            handleAddToCart(single);
                                            // Clear the search bar to prepare for the next scan (this also cancels the old debounce)
                                            setSearchQuery('');
                                        } else {
                                            // Multi-variant family: expand the inline variant picker
                                            const key = single.codigo !== '1'
                                                ? single.codigo
                                                : `1|${single.descripcion.trim().toLowerCase()}`;
                                            setExpandedFamilyKey(prev => prev === key ? null : key);
                                        }
                                    } else {
                                        // No perfect match and not exactly one result.
                                        // Highlight the text so the user or scanner can easily overwrite it with the next scan.
                                        e.target.select();
                                    }
                                } catch (error) {
                                    console.error("Error in instant search on Enter:", error);
                                    toast.error("Error al buscar el producto");
                                } finally {
                                    isEnterSearchingRef.current = false;
                                }
                            }
                        }}
                        autoFocus
                    />
                </div>
                <div className="product-grid">
                    {products.map(product => {
                        const familyKey = product.codigo !== '1'
                            ? product.codigo
                            : `1|${product.descripcion.trim().toLowerCase()}`;
                        const isExpanded = expandedFamilyKey === familyKey;
                        const isPerfectMatch = searchQuery && product.codigo === searchQuery;

                        return (
                            <div key={familyKey} className="product-card-wrapper">
                                <div
                                    className={`product-card ${product._isGrouped ? 'product-card-family' : ''} ${isPerfectMatch ? 'perfect-match-highlight' : ''}`}
                                    onClick={() => handleFamilyCardClick(product)}
                                >
                                    <h3>{product.descripcion}</h3>
                                    {/* Req 2: Show product code below the name, matching SalesDetailModal's text-muted style.
                                        'Cod: 1' is acceptable for generic products per .cursorrules spec. */}
                                    <small className="product-code-label">Cod: {product.codigo}</small>
                                    {product._isGrouped && (
                                        <span className="variant-badge">{product._siblings.length} variantes</span>
                                    )}
                                    <div className="price">
                                        {formatCurrency(saleType === 'MAYORISTA' ? product.precioMayorista : product.precioMinorista)}
                                    </div>
                                    <div className={`stock ${product.cantidadStock <= 0 ? 'stock-warning' : ''}`}>
                                        Stock total: {product.cantidadStock}
                                    </div>
                                    {product._isGrouped && (
                                        <div className="expand-hint">{isExpanded ? '▲ Ocultar variantes' : '▼ Ver variantes'}</div>
                                    )}
                                </div>

                                {isExpanded && product._isGrouped && (
                                    <div className="variant-picker">
                                        {product._siblings.map(variant => (
                                            <div
                                                key={variant.id}
                                                className="variant-row"
                                                onClick={() => {
                                                    handleAddToCart(variant);
                                                    setExpandedFamilyKey(null);
                                                }}
                                            >
                                                <span className="variant-cost">Costo: {formatCurrency(variant.precioCosto)}</span>
                                                <span className={`variant-stock ${variant.cantidadStock <= 0 ? 'stock-warning' : ''}`}>
                                                    Stock: {variant.cantidadStock}
                                                </span>
                                                <button className="variant-add-btn">+ Agregar</button>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>

            <div className={`ticket-panel theme-${saleType.toLowerCase()} ${activeTab === 'ticket' ? 'active' : ''}`}>
                <div className="ticket-header">
                    <div className="ticket-header-left">
                        <h2>Carrito de venta</h2>
                        <span className="total-products-label">
                            Total Productos: {cartItems.reduce((sum, item) => sum + item.quantity, 0)}
                        </span>
                    </div>

                    <div className="cart-search-container">
                        <input
                            type="text"
                            className="cart-search-input"
                            placeholder="🔍 Buscar en carrito..."
                            value={cartSearchQuery}
                            onChange={(e) => setCartSearchQuery(e.target.value)}
                            onKeyDown={(e) => {
                                if (e.key === 'Escape') {
                                    setCartSearchQuery('');
                                    searchInputRef.current?.focus();
                                } else if (e.key === 'Enter') {
                                    e.target.select();
                                }
                            }}
                            title="Presione ESC para volver a la búsqueda principal"
                        />
                    </div>

                    <div className="sale-type-toggle">
                        <button className={`toggle-btn ${saleType === 'MINORISTA' ? 'active' : ''}`} onClick={() => handleSaleTypeChange('MINORISTA')}>Minorista</button>
                        <button className={`toggle-btn ${saleType === 'MAYORISTA' ? 'active' : ''}`} onClick={() => handleSaleTypeChange('MAYORISTA')}>Mayorista</button>
                    </div>
                </div>

                <div className="client-autocomplete-container">
                    <input
                        type="text"
                        list="client-suggestions"
                        className={`current-client-input ${!clientName ? 'required-empty' : ''}`}
                        placeholder={!clientName ? "Ingrese Cliente - Requerido" : "Cliente"}
                        value={clientName}
                        onChange={(e) => setClientName(e.target.value)}
                        tabIndex="1"
                    />
                    <datalist id="client-suggestions">
                        {(availableClients || []).filter(c => c.activo !== false).map((client) => (
                            <option key={client.id} value={client.nombre} />
                        ))}
                    </datalist>
                    {/* Show saldo badge when a matched client has available credit */}
                    {selectedClientObj && (selectedClientObj.saldoAFavor ?? 0) > 0 && (
                        <div style={{ fontSize: '0.78rem', color: '#0d9488', fontWeight: 600, marginTop: '2px', paddingLeft: '2px' }}>
                            💳 Saldo a Favor disponible: {new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2 }).format(selectedClientObj.saldoAFavor)}
                        </div>
                    )}
                </div>

                <div className="cart-items-list" ref={cartListRef}>
                    {filteredCartItems.map((item, index) => {
                        const isCartPerfectMatch = cartSearchQuery.trim() && item.product.codigo.toLowerCase() === cartSearchQuery.trim().toLowerCase();
                        return (
                            <div key={item.product.id || index} className={`cart-item ${item.quantity > item.product.cantidadStock ? 'stock-warning-row' : ''} ${isCartPerfectMatch ? 'perfect-match-highlight' : ''}`}>
                                {/* Row 1: Product Name & Code, and Discount */}
                                <div className="cart-row cart-row-top">
                                    <div className="cart-item-name-container">
                                        <b className="cart-item-name" title={item.product.descripcion}>{item.product.descripcion}</b>
                                    </div>
                                    <small className="product-code-label">{item.product.codigo}</small>
                                    <div className="item-discount">
                                        <label>Desc. Producto</label>
                                        <div style={{ display: 'flex', gap: '5px' }}>
                                            <input
                                                type="text"
                                                inputMode="decimal" min="0" step="0.01"
                                                value={item.unitPrice > 0 && item.discount > 0 ? ((item.discount / item.unitPrice) * 100).toFixed(2).replace(/\.00$/, '') : ''}
                                                onChange={(e) => {
                                                    const val = enforceMoneyFormat(e.target.value);
                                                    const perc = parseFloat(val) || 0;
                                                    const absDiscount = item.unitPrice * (perc / 100);
                                                    updateItemDiscount(item.product.id, absDiscount.toFixed(2));
                                                }}
                                                onKeyDown={blockNonNumericKeys}
                                                onPaste={sanitizeNumericPaste}
                                                placeholder="%"
                                                className="discount-input percentage-input"
                                                style={{ width: '45px' }}
                                                title="Descuento en %"
                                            />
                                            <input
                                                type="text"
                                                inputMode="decimal" min="0" step="0.01"
                                                value={item.discount || ''}
                                                onChange={(e) => {
                                                    const val = enforceMoneyFormat(e.target.value);
                                                    updateItemDiscount(item.product.id, val);
                                                }}
                                                onKeyDown={blockNonNumericKeys}
                                                onPaste={sanitizeNumericPaste}
                                                placeholder="$0"
                                                className="discount-input absolute-input"
                                                title="Descuento en $"
                                            />
                                        </div>
                                    </div>
                                </div>
                                {/* Row 2: Price, Qty Controls, Total, Remove */}
                                <div className="cart-row cart-row-bottom">
                                    <span className="price-label">{formatCurrency(item.unitPrice)}</span>
                                    <button
                                        className="config-item-btn"
                                        onClick={() => {
                                            setConfigModalItem(item);
                                            setConfigModalOpen(true);
                                        }}
                                        title="Configurar precios por cantidad"
                                    >
                                        ⚙️
                                    </button>
                                    <div className="cart-item-qty">
                                        {/* Req 1: [-] button disabled when local display value is empty/0 */}
                                        <button
                                            className="qty-btn"
                                            disabled={
                                                (localQtyValues[item.product.id] !== undefined &&
                                                    (localQtyValues[item.product.id] === '' || Number(localQtyValues[item.product.id]) <= 1)) ||
                                                item.quantity <= 1
                                            }
                                            onClick={() => {
                                                const result = updateQuantity(item.product.id, item.quantity - 1);
                                                if (result === 'zero_blocked') {
                                                    toast('Para eliminar producto tocar su botón ×', { icon: 'ℹ️', duration: 2000 });
                                                } else {
                                                    // Sync local buffer with new valid value
                                                    setLocalQtyValues(prev => {
                                                        const next = { ...prev };
                                                        delete next[item.product.id];
                                                        return next;
                                                    });
                                                }
                                            }}
                                        >-</button>
                                        <input
                                            type="text"
                                            inputMode="numeric" min="0" step="1"
                                            className="qty-input"
                                            // Req 1: Use localQtyValues as display buffer so empty string can be shown while editing.
                                            // item.quantity (always >= 1) is only used when no local override exists.
                                            value={localQtyValues[item.product.id] !== undefined
                                                ? localQtyValues[item.product.id]
                                                : item.quantity
                                            }
                                            onChange={(e) => {
                                                const raw = e.target.value.replace(/[^0-9]/g, '');
                                                if (raw === '') {
                                                    // Req 1: Store empty string locally — do NOT force a value while editing.
                                                    // hasInvalidQty detects this via localQtyValues and blocks action buttons.
                                                    setLocalQtyValues(prev => ({ ...prev, [item.product.id]: '' }));
                                                } else {
                                                    const val = parseInt(raw, 10);
                                                    if (!isNaN(val) && val >= 1) {
                                                        updateQuantity(item.product.id, val);
                                                        // Clear local buffer once a valid int is committed
                                                        setLocalQtyValues(prev => {
                                                            const next = { ...prev };
                                                            delete next[item.product.id];
                                                            return next;
                                                        });
                                                    }
                                                }
                                            }}
                                            onBlur={(e) => {
                                                // Req 1: On blur, show a non-disruptive warning ONLY if the field remains invalid.
                                                // We do NOT auto-revert to 1 — the user must correct it manually.
                                                const val = e.target.value;
                                                if (!val || parseInt(val, 10) <= 0) {
                                                    setLastInvalidFieldId(item.product.id);
                                                } else {
                                                    // Field is valid: clear any warning for this item
                                                    setLastInvalidFieldId(prev => prev === item.product.id ? null : prev);
                                                    // Also clear local buffer if a valid value was committed
                                                    setLocalQtyValues(prev => {
                                                        const next = { ...prev };
                                                        delete next[item.product.id];
                                                        return next;
                                                    });
                                                }
                                            }}
                                            onKeyDown={blockNonIntegerKeys}
                                            onPaste={sanitizeIntegerPaste}
                                        />
                                        {/* Req 1: [+] button disabled when local display value is empty/zero */}
                                        <button
                                            className="qty-btn"
                                            disabled={
                                                localQtyValues[item.product.id] !== undefined &&
                                                (localQtyValues[item.product.id] === '' || Number(localQtyValues[item.product.id]) <= 0)
                                            }
                                            onClick={() => {
                                                updateQuantity(item.product.id, item.quantity + 1);
                                                // Sync local buffer
                                                setLocalQtyValues(prev => {
                                                    const next = { ...prev };
                                                    delete next[item.product.id];
                                                    return next;
                                                });
                                            }}
                                        >+</button>
                                    </div>
                                    <span className="cart-item-total">{formatCurrency((Math.max(0, item.unitPrice - (item.discount || 0))) * item.quantity)}</span>
                                    <button className="remove-btn-new" onClick={() => removeFromCart(item.product.id)}>×</button>
                                </div>
                                {/* Req 1: Non-disruptive warning — only shown under the LAST field that was blurred invalid.
                                Appears after blur (onBlur), NOT while typing. Prevents flashing alerts mid-edit. */}
                                {lastInvalidFieldId === item.product.id && (
                                    <small className="qty-invalid-warning">
                                        Para continuar operacion, ingrese valor mayor a 0
                                    </small>
                                )}
                            </div>
                        );
                    })}
                </div>

                {/* PAYMENT STACK */}
                <div className="payment-section">
                    <div className="payment-stack">
                        {payments.map((p) => (
                            <div key={p._internalId} className="payment-item">
                                <span className="payment-name">{p.name}</span>
                                <span className="payment-amount-label">{formatCurrency(p.amount)}</span>
                                <button className="remove-btn-new" onClick={() => removePaymentMethod(p._internalId)}>×</button>
                            </div>
                        ))}
                    </div>

                    {/* New Payment Input Row — FIXED outside scroll area (Issue #26) */}
                    <div className="payment-row payment-row-new">
                        <select
                            className="payment-select"
                            value={selectedMethodId}
                            onChange={handleMethodSelect}
                            tabIndex="2"
                        >
                            <option value="" disabled>Elegir Método</option>
                            {availableMethods.map(m => (
                                <option key={m.id} value={m.id}>{m.descripcion}</option>
                            ))}
                        </select>
                        <input
                            ref={paymentAmountRef}
                            type="text"
                            inputMode="decimal" min="0" step="0.01"
                            className="payment-amount"
                            placeholder="$"
                            value={paymentAmount}
                            onChange={(e) => {
                                const val = enforceMoneyFormat(e.target.value);
                                setPaymentAmount(val);
                            }}
                            onKeyDown={(e) => {
                                if (e.key === 'Enter') { handleAddPayment(); return; }
                                blockNonNumericKeys(e);
                            }}
                            onPaste={sanitizeNumericPaste}
                            onFocus={(e) => e.target.select()}
                            tabIndex="3"
                        />
                        <button onClick={handleAddPayment} className="add-payment-btn" tabIndex="5">+</button>
                    </div>

                    <div className="totals-area">
                        <div className="totals-discount-col">
                            <label className="discount-global-label">Desc. Global</label>
                            <div style={{ display: 'flex', gap: '5px' }}>
                                <input
                                    type="text"
                                    inputMode="decimal" min="0" step="0.01"
                                    value={totals.subtotal > 0 && globalDiscount > 0 ? ((globalDiscount / totals.subtotal) * 100).toFixed(2).replace(/\.00$/, '') : ''}
                                    onChange={(e) => {
                                        const val = enforceMoneyFormat(e.target.value);
                                        const perc = parseFloat(val) || 0;
                                        const absDiscount = totals.subtotal * (perc / 100);
                                        setGlobalDiscount(absDiscount);
                                    }}
                                    onKeyDown={blockNonNumericKeys}
                                    onPaste={sanitizeNumericPaste}
                                    placeholder="%"
                                    className="discount-global-input percentage-input"
                                    style={{ width: '50px' }}
                                    title="Descuento global en %"
                                />
                                <input
                                    type="text"
                                    inputMode="decimal" min="0" step="0.01"
                                    value={globalDiscount || ''}
                                    onChange={(e) => {
                                        const val = enforceMoneyFormat(e.target.value);
                                        setGlobalDiscount(parseFloat(val) || 0);
                                    }}
                                    onKeyDown={blockNonNumericKeys}
                                    onPaste={sanitizeNumericPaste}
                                    placeholder="$0"
                                    className="discount-global-input absolute-input"
                                    title="Descuento global en $"
                                />
                            </div>
                        </div>
                        <div className="totals-discount-col">
                            <label className="discount-global-label" style={{color: '#d9534f'}}>Recargo Global</label>
                            <div style={{ display: 'flex', gap: '5px' }}>
                                <input
                                    type="text"
                                    inputMode="decimal" min="0" step="0.01"
                                    value={totals.subtotal > 0 && globalSurcharge > 0 ? ((globalSurcharge / totals.subtotal) * 100).toFixed(2).replace(/\.00$/, '') : ''}
                                    onChange={(e) => {
                                        const val = enforceMoneyFormat(e.target.value);
                                        const perc = parseFloat(val) || 0;
                                        const absSurcharge = totals.subtotal * (perc / 100);
                                        setGlobalSurcharge(absSurcharge);
                                    }}
                                    onKeyDown={blockNonNumericKeys}
                                    onPaste={sanitizeNumericPaste}
                                    placeholder="%"
                                    className="discount-global-input percentage-input"
                                    style={{ width: '50px', borderColor: '#d9534f' }}
                                    title="Recargo global en %"
                                />
                                <input
                                    type="text"
                                    inputMode="decimal" min="0" step="0.01"
                                    value={globalSurcharge || ''}
                                    onChange={(e) => {
                                        const val = enforceMoneyFormat(e.target.value);
                                        setGlobalSurcharge(parseFloat(val) || 0);
                                    }}
                                    onKeyDown={blockNonNumericKeys}
                                    onPaste={sanitizeNumericPaste}
                                    placeholder="$0"
                                    className="discount-global-input absolute-input"
                                    style={{ borderColor: '#d9534f' }}
                                    title="Recargo global en $"
                                />
                            </div>
                        </div>
                        <div className="totals-numbers-col">
                            <div className="totals-line">Subtotal: {formatCurrency(totals.subtotal)}</div>
                            <div className="totals-line totals-total">Total: {formatCurrency(totals.total)}</div>
                            <div className={`totals-line ${totals.isOverpaid ? 'totals-excedido' :
                                remaining > 0.01 ? 'totals-falta' : 'totals-cubierto'
                            }`}>
                                {totals.isOverpaid
                                    ? `Excedido: ${formatCurrency(totals.totalPaid - totals.total)}`
                                    : remaining > 0.01
                                        ? `Falta: ${formatCurrency(remaining)}`
                                        : 'Cubierto'}
                            </div>
                        </div>
                    </div>
                    <div style={{ display: 'flex', gap: '10px', marginTop: '15px' }}>
                        {/* Req 1: FINALIZAR disabled when any cart item has an invalid (empty or 0) quantity.
                           Discount fields are explicitly excluded from this check per business rules. */}
                        <button
                            className="pay-btn"
                            disabled={!!editingPendingId || cartItems.length === 0 || payments.length === 0 || !clientName.trim() || isSubmitting || totals.isOverpaid || hasInvalidQty}
                            onClick={handlePrePaymentCheck}
                            style={{ flex: 2, opacity: (!!editingPendingId || cartItems.length === 0 || payments.length === 0 || !clientName.trim() || isSubmitting || totals.isOverpaid || hasInvalidQty) ? 0.5 : 1 }}
                        >
                            {isSubmitting ? "PROCESANDO..." : "FINALIZAR"}
                        </button>
                        {/* Req 1: GUARDAR PENDIENTE also blocked when any qty is invalid */}
                        <button
                            className="pay-btn"
                            disabled={cartItems.length === 0 || !clientName.trim() || isSubmitting || hasInvalidQty}
                            onClick={handleSaveAsPending}
                            style={{ flex: 1, backgroundColor: '#f59e0b', color: 'white', border: '1px solid #d97706', opacity: (cartItems.length === 0 || !clientName.trim() || isSubmitting || hasInvalidQty) ? 0.5 : 1 }}
                        >
                            Guardar Pendiente
                        </button>
                    </div>
                </div>

                {/* MODALS */}

                {pendingSaleType && (
                    <ConfirmationModal
                        title="Cambiar Tipo de Venta"
                        message="Al cambiar el tipo de venta, se recalcularán todos los precios del carrito. ¿Desea continuar?"
                        confirmText="Sí, Cambiar"
                        cancelText="Cancelar"
                        isWarning={true}
                        onConfirm={confirmSaleTypeChange}
                        onCancel={() => setPendingSaleType(null)}
                    />
                )}

                {showStockModal && (
                    <StockWarningModal
                        affectedProducts={affectedProducts}
                        onClose={() => {
                            setShowStockModal(false);
                            setPendingAction(null); // Clear intent on cancel to prevent state leakage
                        }}
                        onContinue={() => {
                            setShowStockModal(false);
                            // Branch on the original intent: prevents "Ignorar y Continuar"
                            // from always triggering a Direct Sale when user intended a Pending Save.
                            const action = pendingAction;
                            setPendingAction(null); // Clear before async call
                            // Set a small timeout to allow state to settle before next operation
                            setTimeout(() => {
                                if (action === 'SAVE_PENDING') {
                                    executeSaveAsPending();
                                } else {
                                    // Default / 'FINALIZE': run debt check then finalize
                                    const totalPaid = payments.reduce((sum, p) => sum + p.amount, 0);
                                    const remaining = totals.total - totalPaid;
                                    if (remaining > 0.01) {
                                        setShowDebtModal(true);
                                    } else {
                                        handleFinalizeSale();
                                    }
                                }
                            }, 50);
                        }}
                        onStockCorrected={handleStockCorrected}
                    />
                )}

                {showDebtModal && (
                    <ConfirmationModal
                        title="Venta con Deuda"
                        message={`Esta por registrar una venta con deuda para el cliente: ${clientName || 'Desconocido'}. ¿Desea continuar?`}
                        confirmText="Confirmar Venta"
                        cancelText="Cancelar"
                        isWarning={true}
                        onConfirm={handleFinalizeSale}
                        onCancel={() => setShowDebtModal(false)}
                    />
                )}

                {/* Issue #8: Overpayment Modal */}
                {showOverpaidModal && (
                    <ConfirmationModal
                        title="⚠️ Monto Excedido"
                        message={`El monto ingresado excede el total de la venta. Cambio a devolver: ${formatCurrency(parseFloat(paymentAmount) - overpaidMaxAllowed)}.`}
                        confirmText={`Devolver Cambio en Mano`}
                        cancelText="Cancelar"
                        isWarning={true}
                        onConfirm={handleAutoCorrectPayment}
                        onCancel={() => setShowOverpaidModal(false)}
                    />
                )}

                {/* Epic 2: Cheque Modal Integration */}
                <CheckoutChequeModal
                    isOpen={showChequeModal}
                    onClose={() => {
                        setShowChequeModal(false);
                        setSelectedMethodId('');
                    }}
                    onConfirm={handleChequesConfirm}
                    totalAmount={pendingChequeAmount}
                    clientName={clientName}
                />

                {/* Tab switching is now handled by the contextual bottom-nav in AppLayout */}
            </div>

            {configModalOpen && configModalItem && (
                <ProductConfigModal
                    isOpen={configModalOpen}
                    onClose={() => setConfigModalOpen(false)}
                    item={configModalItem}
                    onSave={(productId, subItems) => {
                        updateItemSubItems(productId, subItems);
                        setConfigModalOpen(false);
                    }}
                />
            )}

            <ClientChangeModal
                isOpen={showClientChangeModal}
                onCancel={() => setShowClientChangeModal(false)}
                initialName={initialClientName}
                newName={clientName}
                isSubmitting={isSubmitting}
                onOption1={async () => {
                    if (isSubmitting) return;
                    try {
                        setIsSubmitting(true);
                        await api.put(`/clientes/${initialClientId}/nombre`, { nombre: clientName });
                        await executeSaveAsPending(initialClientId, clientName);
                        setShowClientChangeModal(false);
                    } catch (error) {
                        toast.error(error.response?.data?.message || "Error al actualizar el nombre del cliente");
                        setIsSubmitting(false);
                    }
                }}
                onOption2={() => {
                    if (isSubmitting) return;
                    setShowClientChangeModal(false);
                    executeSaveAsPending();
                }}
                onOption3={() => {
                    if (isSubmitting) return;
                    setClientName(initialClientName);
                    setShowClientChangeModal(false);
                    executeSaveAsPending(initialClientId, initialClientName);
                }}
            />
        </div>
    );
}
