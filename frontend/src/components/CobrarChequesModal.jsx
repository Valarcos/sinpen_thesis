import { useState, useEffect, useRef } from 'react';
import api from '../services/api';
import { formatDate } from '../utils/format';
import toast from 'react-hot-toast';
import './CobrarChequesModal.css';

/**
 * CobrarChequesModal — Blueprint §4 & §5 "The Custom Pago Modal".
 *
 * Displays all individual cheque installments (alertas_cheques) for a given venta.
 * Allows collecting each installment individually via POST /alertas/cheques/{id}/cobrar.
 * Only PENDIENTE installments are actionable; already COBRADO ones are shown as read-only
 * history for full context.
 *
 * @param {boolean} isOpen
 * @param {function} onClose
 * @param {function} onSuccess  - Called after any successful cobro so the parent can refresh
 * @param {object}  ventaItem   - The row from CobrosYPedidosPage (needs venta_id, cliente_nombre)
 * @param {Array}   paymentMethods  - Pre-loaded list of { id, descripcion }
 */
export default function CobrarChequesModal({ isOpen, onClose, onSuccess, ventaItem, paymentMethods }) {
    const [installments, setInstallments] = useState([]);
    const [loading, setLoading] = useState(false);

    // Per-installment state: maps cheque id → { metodoPagoId, isSubmitting }
    const [cobrandoId, setCobrandoId] = useState(null);
    const [selectedMethods, setSelectedMethods] = useState({});
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;
        if (isOpen && ventaItem?.venta_id) {
            fetchInstallments();
        }
        return () => { isMounted.current = false; };
    }, [isOpen, ventaItem]);

    const fetchInstallments = async () => {
        setLoading(true);
        try {
            const res = await api.get(`/alertas/cheques/venta/${ventaItem.venta_id}`);
            if (!isMounted.current) return;
            setInstallments(res.data);
            // Pre-initialize method selectors
            const methods = {};
            res.data.forEach(i => {
                if (i.estado === 'PENDIENTE') methods[i.id] = '';
            });
            setSelectedMethods(methods);
        } catch (err) {
            console.error('Error loading installments:', err);
            // Error handled by global api interceptor
        } finally {
            if (isMounted.current) setLoading(false);
        }
    };

    const handleCobrar = async (cheque) => {
        const metodoPagoId = selectedMethods[cheque.id];
        if (!metodoPagoId) {
            toast.error('Seleccione un método de pago para cobrar esta cuota.');
            return;
        }

        setCobrandoId(cheque.id);
        try {
            await api.post(`/alertas/cheques/${cheque.id}/cobrar`, null, {
                params: { metodoPagoId }
            });
            toast.success(`Cuota de $${cheque.monto.toFixed(2)} cobrada exitosamente.`);
            // Refresh installments list
            await fetchInstallments();
            // Notify parent to refresh the table
            if (onSuccess) onSuccess();
        } catch (err) {
            console.error('Error al cobrar cuota:', err);
            // Vector 2: Force re-fetch on failure
            await fetchInstallments();
        } finally {
            if (isMounted.current) setCobrandoId(null);
        }
    };

    const handleCancelarCobro = async (cheque) => {
        if (!window.confirm(`¿Está seguro de que desea anular el cobro de esta cuota de $${cheque.monto.toFixed(2)}?`)) return;
        setCobrandoId(cheque.id);
        try {
            await api.post(`/alertas/cheques/${cheque.id}/cancelar-cobro`);
            toast.success("Cobro anulado exitosamente.");
            await fetchInstallments();
            if (onSuccess) onSuccess();
        } catch (err) {
            console.error('Error al anular cobro:', err);
            // Vector 2: Force re-fetch on failure
            await fetchInstallments();
        } finally {
            if (isMounted.current) setCobrandoId(null);
        }
    };

    const pendingTotal = (installments || [])
        .filter(i => i.estado === 'PENDIENTE')
        .reduce((sum, i) => sum + i.monto, 0);

    const cobradoTotal = (installments || [])
        .filter(i => i.estado === 'COBRADO')
        .reduce((sum, i) => sum + i.monto, 0);

    if (!isOpen) return null;

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div
                className="ccm-modal-content"
                onClick={e => e.stopPropagation()}
                role="dialog"
                aria-modal="true"
                aria-label="Cobrar Cuotas de Cheque"
            >
                {/* Header */}
                <div className="ccm-header">
                    <div className="ccm-header-info">
                        <h2>💳 Cobro de Cheques</h2>
                        <p>{ventaItem?.cliente_nombre}</p>
                    </div>
                    <button className="ccm-close-btn" onClick={onClose} aria-label="Cerrar">✕</button>
                </div>

                {/* Summary row */}
                <div className="ccm-summary-row">
                    <div className="ccm-summary-box ccm-summary-pending">
                        <span className="ccm-summary-label">Pendiente de cobro</span>
                        <span className="ccm-summary-amount">${pendingTotal.toFixed(2)}</span>
                    </div>
                    <div className="ccm-summary-box ccm-summary-paid">
                        <span className="ccm-summary-label">Ya cobrado</span>
                        <span className="ccm-summary-amount">${cobradoTotal.toFixed(2)}</span>
                    </div>
                </div>

                {/* Installments list */}
                <div className="ccm-installments">
                    {loading ? (
                        <div className="ccm-loading">Cargando cuotas...</div>
                    ) : (installments || []).length === 0 ? (
                        <div className="ccm-empty">No se encontraron cuotas para esta venta.</div>
                    ) : (
                        (installments || []).map((cheque) => {
                            const isPending = cheque.estado === 'PENDIENTE';
                            const isOverdue = isPending && new Date(cheque.fechaCobro) <= new Date();
                            const isCobrandoThis = cobrandoId === cheque.id;

                            return (
                                <div
                                    key={cheque.id}
                                    className={`ccm-installment-row ${isPending ? (isOverdue ? 'ccm-row-overdue' : 'ccm-row-pending') : 'ccm-row-paid'}`}
                                >
                                    <div className="ccm-installment-info">
                                        <div className="ccm-installment-date">
                                            <span className="ccm-date-label">
                                                {isOverdue ? '⚠️ Vencido' : isPending ? '📅 Vence' : '✅ Cobrado'}
                                            </span>
                                            <strong>{formatDate(cheque.fechaCobro)}</strong>
                                        </div>
                                        <div className="ccm-installment-amount">
                                            ${cheque.monto.toFixed(2)}
                                        </div>
                                        <span className={`ccm-badge ccm-badge-${cheque.estado.toLowerCase()}`}>
                                            {cheque.estado}
                                        </span>
                                    </div>

                                    {isPending && (
                                        <div className="ccm-installment-action">
                                            <select
                                                value={selectedMethods[cheque.id] || ''}
                                                onChange={e => setSelectedMethods(prev => ({
                                                    ...prev,
                                                    [cheque.id]: e.target.value
                                                }))}
                                                className="ccm-method-select"
                                                disabled={isCobrandoThis}
                                                aria-label={`Método de pago para cuota del ${formatDate(cheque.fechaCobro)}`}
                                            >
                                                <option value="">Método de pago...</option>
                                                {(paymentMethods || [])
                                                    .filter(m => {
                                                        const desc = (m.descripcion || '').toLowerCase();
                                                        return !desc.includes('cheque') && !desc.includes('e-check') && m.activo !== false;
                                                    })
                                                    .map(m => (
                                                        <option key={m.id} value={m.id}>{m.descripcion}</option>
                                                    ))}
                                            </select>
                                            <button
                                                className="ccm-cobrar-btn"
                                                onClick={() => handleCobrar(cheque)}
                                                disabled={!selectedMethods[cheque.id] || isCobrandoThis || cobrandoId !== null}
                                                aria-label={`Cobrar cuota de $${cheque.monto.toFixed(2)}`}
                                            >
                                                {isCobrandoThis ? 'Cobrando...' : '💰 Cobrar'}
                                            </button>
                                        </div>
                                    )}

                                    {!isPending && cheque.estado === 'COBRADO' && (
                                        <div className="ccm-installment-action" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
                                            <span style={{ fontSize: '0.85rem', color: '#0f766e', backgroundColor: '#ccfbf1', padding: '4px 8px', borderRadius: '4px' }}>
                                                Método: {cheque.metodoPagoNombre || 'Desconocido'}
                                            </span>
                                            <button
                                                className="ccm-cobrar-btn"
                                                onClick={() => handleCancelarCobro(cheque)}
                                                disabled={cobrandoId !== null}
                                                style={{ backgroundColor: 'transparent', border: '1px solid #dc3545', color: '#dc3545' }}
                                                aria-label={`Anular cobro de $${cheque.monto.toFixed(2)}`}
                                            >
                                                {isCobrandoThis ? 'Anulando...' : 'Anular'}
                                            </button>
                                        </div>
                                    )}
                                </div>
                            );
                        })
                    )}
                </div>

                {/* Footer */}
                <div className="ccm-footer">
                    <button className="secondary ccm-cancel-btn" onClick={onClose}>
                        Cerrar
                    </button>
                </div>
            </div>
        </div>
    );
}
