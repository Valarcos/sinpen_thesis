import { useState, useRef, useEffect } from 'react';
import toast from 'react-hot-toast';
import './CheckoutChequeModal.css';
import { blockNonNumericKeys, sanitizeNumericPaste, enforceMoneyFormat } from '../utils/numericInput';

export default function CheckoutChequeModal({ isOpen, onClose, onConfirm, totalAmount, clientName }) {
    const [cheques, setCheques] = useState([{ monto: '', fechaCobro: '' }]);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const listRef = useRef(null);
    const lastMontoRef = useRef(null);
    const modalRef = useRef(null);
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;
        return () => { isMounted.current = false; };
    }, []);

    // Initial pre-fill and focus
    useEffect(() => {
        if (isOpen) {
            if (cheques.length === 1 && !cheques[0].monto && totalAmount > 0) {
                setCheques([{ monto: totalAmount.toFixed(2), fechaCobro: '' }]);
            }
            setTimeout(() => {
                if (lastMontoRef.current) {
                    lastMontoRef.current.focus();
                    lastMontoRef.current.select();
                }
            }, 50);
        } else {
            setCheques([{ monto: '', fechaCobro: '' }]);
        }
    }, [isOpen, totalAmount]);

    // Auto-scroll on new cheque
    useEffect(() => {
        if (listRef.current) {
            listRef.current.scrollTop = listRef.current.scrollHeight;
        }
    }, [cheques.length]);

    // Focus Trap
    useEffect(() => {
        const handleKeyDown = (e) => {
            if (e.key === 'Tab' && modalRef.current) {
                const focusableElements = modalRef.current.querySelectorAll(
                    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
                );
                if (focusableElements.length === 0) return;

                const firstElement = focusableElements[0];
                const lastElement = focusableElements[focusableElements.length - 1];

                if (e.shiftKey) {
                    if (document.activeElement === firstElement) {
                        lastElement.focus();
                        e.preventDefault();
                    }
                } else {
                    if (document.activeElement === lastElement) {
                        firstElement.focus();
                        e.preventDefault();
                    }
                }
            }
        };

        if (isOpen) {
            document.addEventListener('keydown', handleKeyDown);
        }
        return () => {
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [isOpen]);

    if (!isOpen) return null;

    const handleAddCheque = () => {
        const hasEmptyDate = cheques.some(c => !c.fechaCobro);
        if (hasEmptyDate) {
            toast.error("Ingrese fecha de cobro del cheque");
            return;
        }

        const totalIngresado = cheques.reduce((sum, c) => sum + (parseFloat(c.monto) || 0), 0);
        const restante = Math.max(0, totalAmount - totalIngresado);

        setCheques([...cheques, { monto: restante > 0 ? restante.toFixed(2) : '', fechaCobro: '' }]);

        // Auto focus the new input
        setTimeout(() => {
            if (lastMontoRef.current) {
                lastMontoRef.current.focus();
                lastMontoRef.current.select();
            }
        }, 50);
    };

    const handleRemoveCheque = (index) => {
        const newCheques = [...cheques];
        newCheques.splice(index, 1);
        setCheques(newCheques);
    };

    const handleChequeChange = (index, field, value) => {
        const newCheques = [...cheques];
        newCheques[index][field] = value;
        setCheques(newCheques);
    };

    const totalIngresado = cheques.reduce((sum, c) => sum + (parseFloat(c.monto) || 0), 0);
    const restante = Math.max(0, totalAmount - totalIngresado);
    const validCheques = cheques.every(c => parseFloat(c.monto) > 0 && c.fechaCobro);
    const isTotalValid = Math.abs(totalAmount - totalIngresado) < 0.01;

    const handleMontoFocus = (e, index) => {
        e.target.select(); // Highlight the whole content

        // Auto calculate if empty
        if (!cheques[index].monto && restante > 0) {
            handleChequeChange(index, 'monto', restante.toFixed(2));
        }
    };

    const handleConfirm = async () => {
        if (!validCheques) return;

        setIsSubmitting(true);
        try {
            await onConfirm(cheques);
        } finally {
            if (isMounted.current) setIsSubmitting(false);
        }
    };

    return (
        <div className="modal-overlay">
            <div className="modal-content cheque-modal-content" ref={modalRef}>
                <div className="modal-header-cheque">
                    <h2>Cobro con Cheques</h2>
                </div>

                <div className="cheque-modal-body">
                    <div className="cheque-info-box">
                        <div className="info-row">
                            <div className="info-label"><strong>Cliente:</strong></div>
                            <div className="info-value">{clientName}</div>
                        </div>
                        <div className="info-row">
                            <div className="info-label"><strong>Total Venta:</strong></div>
                            <div className="info-value">${totalAmount.toFixed(2)}</div>
                        </div>
                        <div className="info-row">
                            <div className="info-label"><strong>Falta Asignar:</strong></div>
                            <div className="info-value">
                               <span style={{ color: restante > 0 ? '#dc2626' : '#16a34a' }}>
                                   ${restante.toFixed(2)}
                               </span>
                            </div>
                        </div>
                    </div>

                    <div className="cheques-list" ref={listRef}>
                        {(cheques || []).map((cheque, index) => (
                            <div key={index} className="cheque-row">
                                <label>Monto</label>
                                <input
                                    type="text"
                                    inputMode="decimal"
                                    min="0"
                                    placeholder="$"
                                    value={cheque.monto}
                                    ref={index === cheques.length - 1 ? lastMontoRef : null}
                                    onChange={(e) => handleChequeChange(index, 'monto', enforceMoneyFormat(e.target.value))}
                                    onFocus={(e) => handleMontoFocus(e, index)}
                                    onKeyDown={blockNonNumericKeys}
                                    onPaste={sanitizeNumericPaste}
                                />
                                <label>Fecha de Cobro</label>
                                <input
                                    type="date"
                                    className="cheque-date-input"
                                    value={cheque.fechaCobro}
                                    onChange={(e) => handleChequeChange(index, 'fechaCobro', e.target.value)}
                                    min={new Date().toISOString().split('T')[0]} // Prevents past dates based on client's local TZ
                                />
                                {cheques.length > 1 ? (
                                    <button
                                        className="remove-cheque-btn"
                                        onClick={() => handleRemoveCheque(index)}
                                    >
                                        ×
                                    </button>
                                ) : (
                                    <div className="remove-cheque-placeholder"></div>
                                )}
                            </div>
                        ))}
                    </div>

                    <button
                        className="add-cheque-btn"
                        onClick={handleAddCheque}
                        disabled={restante <= 0}
                    >
                        + Agregar otro cheque
                    </button>
                </div>

                <div className="modal-actions-cheque">
                    <button
                        className="cancel-btn"
                        onClick={onClose}
                        disabled={isSubmitting}
                    >
                        Cancelar
                    </button>
                    <button
                        className="confirm-btn"
                        onClick={handleConfirm}
                        disabled={!validCheques || isSubmitting}
                    >
                        {isSubmitting ? 'Procesando...' : 'Confirmar Cheques'}
                    </button>
                </div>
            </div>
        </div>
    );
}
