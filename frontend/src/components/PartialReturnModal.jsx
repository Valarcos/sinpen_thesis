import { useState, useEffect, useRef } from 'react';
import api from '../services/api';
import toast from 'react-hot-toast';
import { formatCurrency } from '../utils/format';
import './PremiumModal.css';

/**
 * PartialReturnModal
 * Allows the cashier to process a partial return of items from an ACTIVA sale.
 *
 * Business rules enforced here (mirrored server-side):
 *  - Returned qty must be > 0 and <= net remaining qty for each item.
 *  - If the sale has an active debt, the refund is first applied to reduce it.
 *    The remaining refundable amount (after debt offset) can go to:
 *      SALDO   → add to clientes.saldo_a_favor (only if a client is linked)
 *      EFECTIVO → physical cash back (negative payment record)
 *  - If returning 100% of all remaining items a severe warning is shown
 *    (this would effectively annul the sale).
 *  - The backend re-validates everything atomically; this is only a UX guard.
 */
export default function PartialReturnModal({ isOpen, sale, onClose, onSuccess }) {
    // --- State ---
    const [details, setDetails]     = useState([]); // Full sale detail objects from backend
    const [quantities, setQuantities] = useState({}); // { detalleId: returnQty (number) }
    const [tipoReembolso, setTipoReembolso] = useState('SALDO');
    const [observaciones, setObservaciones] = useState('');
    const [loading, setLoading]     = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [showFullReturnWarning, setShowFullReturnWarning] = useState(false);

    const modalRef = useRef(null);

    // --- Load sale details when modal opens ---
    useEffect(() => {
        if (!isOpen || !sale?.id) return;

        setLoading(true);
        setShowFullReturnWarning(false);
        setObservaciones('');
        setTipoReembolso('SALDO');

        api.get(`/ventas/${sale.id}`)
            .then(res => {
                // Only show non-annulled line items that still have returnable quantity
                const activeDetails = (res.data.items || []).filter(d => !d.anulado);
                setDetails(activeDetails);
                // Initialise each quantity entry to 0
                const initQty = {};
                activeDetails.forEach(d => { initQty[d.id] = 0; });
                setQuantities(initQty);
            })
            .catch(err => {
                console.error('Error loading sale details for return:', err);
                toast.error('Error al cargar los detalles de la venta.');
                onClose();
            })
            .finally(() => setLoading(false));
    }, [isOpen, sale?.id]); // eslint-disable-line react-hooks/exhaustive-deps

    // Focus trap
    useEffect(() => {
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') { onClose(); return; }
            if (e.key !== 'Tab' || !modalRef.current) return;
            const focusable = modalRef.current.querySelectorAll(
                'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
            );
            if (!focusable.length) return;
            const first = focusable[0];
            const last  = focusable[focusable.length - 1];
            if (e.shiftKey ? document.activeElement === first : document.activeElement === last) {
                (e.shiftKey ? last : first).focus();
                e.preventDefault();
            }
        };
        if (isOpen) document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    if (!isOpen || !sale) return null;

    // --- Derived calculations ---

    // Net remaining qty for a detail (original qty minus previously returned qty)
    const netRemaining = (detail) => {
        // The backend stores yaDevuelta in the detail; if missing, assume 0
        const already = detail.cantidadDevuelta ?? 0;
        return detail.cantidad - already;
    };

    // Items that have at least 1 returnable unit
    const returnableDetails = details.filter(d => netRemaining(d) > 0);

    // Items the user has actually selected (qty > 0)
    const selectedItems = returnableDetails.filter(d => (quantities[d.id] || 0) > 0);

    // Estimated refund total
    const estimatedRefund = selectedItems.reduce((sum, d) => {
        return sum + (d.precioUnitario || 0) * (quantities[d.id] || 0);
    }, 0);

    // Total net remaining units across all items
    const totalNetRemaining  = returnableDetails.reduce((s, d) => s + netRemaining(d), 0);
    const totalReturnSelected = selectedItems.reduce((s, d) => s + (quantities[d.id] || 0), 0);
    const isFullReturn = totalReturnSelected > 0 && totalReturnSelected >= totalNetRemaining;

    const hasSelectedItems = selectedItems.length > 0;
    const hasLinkedClient  = !!sale.clienteId;

    // If the sale has no linked client, SALDO is not allowed → force EFECTIVO
    const effectiveTipoReembolso = hasLinkedClient ? tipoReembolso : 'EFECTIVO';

    // --- Handlers ---

    const handleQtyChange = (detailId, value) => {
        const max = netRemaining(returnableDetails.find(d => d.id === detailId));
        const parsed = Math.min(Math.max(0, parseInt(value, 10) || 0), max);
        setQuantities(prev => ({ ...prev, [detailId]: parsed }));
    };

    const handleConfirmClick = () => {
        if (!hasSelectedItems) {
            toast.error('Seleccione al menos un producto a devolver.');
            return;
        }
        if (isFullReturn && !showFullReturnWarning) {
            setShowFullReturnWarning(true);
            return;
        }
        submitReturn();
    };

    const submitReturn = async () => {
        setSubmitting(true);
        try {
            const items = selectedItems.map(d => ({
                detalleVentaId: d.id,
                cantidadDevuelta: quantities[d.id]
            }));

            await api.post(`/ventas/${sale.id}/devolucion-parcial`, {
                items,
                tipoReembolso: effectiveTipoReembolso,
                observaciones: observaciones.trim() || null
            });

            toast.success('Devolución registrada exitosamente.');
            onSuccess();
            onClose();
        } catch (err) {
            console.error('Error submitting return:', err);
            toast.error(err.response?.data?.message || 'Error al registrar la devolución.');
        } finally {
            setSubmitting(false);
            setShowFullReturnWarning(false);
        }
    };

    // --- Render ---

    // Full-return confirmation overlay (shown over the modal body)
    if (showFullReturnWarning) {
        return (
            <div className="modal-overlay">
                <div className="modal-content premium-modal-content pm-sm" ref={modalRef}>
                    <div className="pm-header pm-header-danger">
                        <h2>⚠️ Confirmación de Devolución Total</h2>
                    </div>
                    <div className="pm-body">
                        <div className="pm-danger-banner">
                            <span className="pm-banner-icon">🔴</span>
                            <span>
                                Está a punto de devolver <strong>todos</strong> los productos restantes
                                de la venta <strong>#{sale.id}</strong>. Esto es equivalente a una
                                anulación completa y no se puede deshacer fácilmente.
                                ¿Está completamente seguro?
                            </span>
                        </div>
                        <p style={{ fontSize: '0.9rem', color: '#64748b', margin: 0 }}>
                            El stock será restaurado y el cliente recibirá un reembolso de{' '}
                            <strong>{formatCurrency(estimatedRefund)}</strong> en{' '}
                            {effectiveTipoReembolso === 'SALDO' ? 'Saldo a Favor' : 'Efectivo'}.
                        </p>
                    </div>
                    <div className="pm-footer">
                        <button
                            className="pm-btn-cancel"
                            onClick={() => setShowFullReturnWarning(false)}
                            disabled={submitting}
                        >
                            Volver
                        </button>
                        <button
                            className="pm-btn-danger"
                            onClick={submitReturn}
                            disabled={submitting}
                            id="btn-confirm-full-return"
                        >
                            {submitting ? 'Procesando...' : 'Sí, Confirmar Devolución Total'}
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="modal-overlay">
            <div className="modal-content premium-modal-content pm-md" ref={modalRef}>

                {/* Header */}
                <div className="pm-header">
                    <h2>Devolución Parcial — Venta #{sale.id}</h2>
                </div>

                {/* Body */}
                <div className="pm-body">

                    {/* Sale summary */}
                    <div className="pm-info-box">
                        <div className="pm-info-row">
                            <span className="pm-info-label">Cliente:</span>
                            <span className="pm-info-value">{sale.clienteNombre || 'Consumidor Final'}</span>
                        </div>
                        <div className="pm-info-row">
                            <span className="pm-info-label">Total original:</span>
                            <span className="pm-info-value">{formatCurrency(sale.totalVenta)}</span>
                        </div>
                        {estimatedRefund > 0 && (
                            <div className="pm-info-row">
                                <span className="pm-info-label">Reembolso estimado:</span>
                                <span className="pm-info-value" style={{ color: '#0d9488', fontWeight: 700 }}>
                                    {formatCurrency(estimatedRefund)}
                                </span>
                            </div>
                        )}
                    </div>

                    {/* Warning if no linked client and SALDO would have been chosen */}
                    {!hasLinkedClient && (
                        <div className="pm-warning-banner">
                            <span className="pm-banner-icon">⚠️</span>
                            <span>
                                Esta venta no tiene un cliente registrado. El reembolso
                                se realizará únicamente en <strong>Efectivo</strong>.
                            </span>
                        </div>
                    )}

                    {/* Products list */}
                    {loading ? (
                        <p style={{ textAlign: 'center', color: '#64748b' }}>Cargando productos...</p>
                    ) : returnableDetails.length === 0 ? (
                        <p style={{ textAlign: 'center', color: '#64748b' }}>
                            No hay productos devolvibles en esta venta.
                        </p>
                    ) : (
                        <div className="pm-list">
                            {returnableDetails.map(detail => {
                                const remaining = netRemaining(detail);
                                const qty = quantities[detail.id] || 0;
                                return (
                                    <div key={detail.id} className="pm-row" style={{ flexWrap: 'wrap', gap: '0.5rem' }}>
                                        {/* Product name */}
                                        <span style={{ flex: '1 1 200px', fontWeight: 600, fontSize: '0.9rem', color: '#1e293b' }}>
                                            {detail.descripcionSnapshot || detail.productoNombre}
                                        </span>
                                        {/* Net remaining badge */}
                                        <span style={{ fontSize: '0.8rem', color: '#64748b', whiteSpace: 'nowrap' }}>
                                            Disponible: <strong>{remaining}</strong> ud.
                                        </span>
                                        {/* Unit price */}
                                        <span style={{ fontSize: '0.8rem', color: '#64748b', whiteSpace: 'nowrap' }}>
                                            {formatCurrency(detail.precioUnitario)} / ud.
                                        </span>
                                        {/* Quantity input */}
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', flexShrink: 0 }}>
                                            <button
                                                className="pm-qty-btn"
                                                onClick={() => handleQtyChange(detail.id, qty - 1)}
                                                disabled={qty <= 0}
                                            >−</button>
                                            <input
                                                type="number"
                                                min={0}
                                                max={remaining}
                                                value={qty}
                                                onChange={e => handleQtyChange(detail.id, e.target.value)}
                                                style={{ width: 50, textAlign: 'center', padding: '4px', border: '1px solid #cbd5e1', borderRadius: 4 }}
                                                aria-label={`Cantidad a devolver de ${detail.descripcionSnapshot}`}
                                            />
                                            <button
                                                className="pm-qty-btn"
                                                onClick={() => handleQtyChange(detail.id, qty + 1)}
                                                disabled={qty >= remaining}
                                            >+</button>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}

                    {/* Refund type toggle — only when there are selected items and client exists */}
                    {hasSelectedItems && hasLinkedClient && (
                        <>
                            <label style={{ display: 'block', fontWeight: 600, fontSize: '0.9rem', marginBottom: '0.4rem', color: '#475569' }}>
                                Tipo de Reembolso:
                            </label>
                            <div className="pm-refund-toggle">
                                <button
                                    className={tipoReembolso === 'SALDO' ? 'active' : ''}
                                    onClick={() => setTipoReembolso('SALDO')}
                                    id="btn-refund-saldo"
                                >
                                    💳 Saldo a Favor (Recomendado)
                                </button>
                                <button
                                    className={tipoReembolso === 'EFECTIVO' ? 'active-cash' : ''}
                                    onClick={() => setTipoReembolso('EFECTIVO')}
                                    id="btn-refund-efectivo"
                                >
                                    💵 Efectivo / Directo
                                </button>
                            </div>
                        </>
                    )}

                    {/* Notes */}
                    <div style={{ marginTop: '0.75rem' }}>
                        <label style={{ display: 'block', fontWeight: 600, fontSize: '0.9rem', marginBottom: '0.4rem', color: '#475569' }}>
                            Observaciones (opcional):
                        </label>
                        <textarea
                            value={observaciones}
                            onChange={e => setObservaciones(e.target.value)}
                            placeholder="Motivo de la devolución..."
                            rows={2}
                            style={{ width: '100%', padding: '0.5rem', borderRadius: 6, border: '1px solid #cbd5e1', fontSize: '0.9rem', resize: 'vertical', boxSizing: 'border-box' }}
                        />
                    </div>
                </div>

                {/* Footer */}
                <div className="pm-footer">
                    <button
                        className="pm-btn-cancel"
                        onClick={onClose}
                        disabled={submitting}
                    >
                        Cancelar
                    </button>
                    <button
                        className="pm-btn-confirm"
                        onClick={handleConfirmClick}
                        disabled={submitting || !hasSelectedItems || loading}
                        id="btn-submit-return"
                    >
                        {submitting ? 'Procesando...' : `Confirmar Devolución${estimatedRefund > 0 ? ` (${formatCurrency(estimatedRefund)})` : ''}`}
                    </button>
                </div>
            </div>
        </div>
    );
}
