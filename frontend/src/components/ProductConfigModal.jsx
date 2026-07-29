import React, { useState, useEffect, useRef } from 'react';
import toast from 'react-hot-toast';
import './ProductConfigModal.css';
import { blockNonNumericKeys, sanitizeNumericPaste, enforceMoneyFormat } from '../utils/numericInput';

export default function ProductConfigModal({ isOpen, onClose, onSave, item }) {
    const [subItems, setSubItems] = useState([]);
    const modalRef = useRef(null);
    const listRef = useRef(null);
    const lastPriceRef = useRef(null);

    // Auto-scroll on new subItem
    useEffect(() => {
        if (listRef.current) {
            listRef.current.scrollTop = listRef.current.scrollHeight;
        }
    }, [subItems.length]);

    useEffect(() => {
        if (isOpen && item) {
            if (item.subItems && item.subItems.length > 0) {
                setSubItems([...item.subItems]);
            } else {
                setSubItems([{
                    quantity: item.quantity,
                    discount: item.discount || 0,
                    reason: ''
                }]);
            }
        }
    }, [isOpen, item]);

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

    if (!isOpen || !item || subItems.length === 0) return null;

    const totalQuantity = item.quantity;
    const basePrice = item.unitPrice;

    const handleAddRow = () => {
        const newSubItems = [...subItems];
        // Ensure default row has at least 1 item to give
        if (newSubItems[0].quantity > 0) {
            newSubItems[0].quantity -= 1;
            newSubItems.push({ quantity: 1, discount: 0, reason: '' });
            setSubItems(newSubItems);

            setTimeout(() => {
                if (lastPriceRef.current) {
                    lastPriceRef.current.focus();
                    lastPriceRef.current.select();
                }
            }, 50);
        } else {
            toast.error("No hay más cantidad disponible para dividir.");
        }
    };

    const handleRemoveRow = (index) => {
        if (index === 0) return; // Cannot remove default row
        const newSubItems = [...subItems];
        const removedQty = newSubItems[index].quantity;
        newSubItems[0].quantity += removedQty;
        newSubItems.splice(index, 1);
        setSubItems(newSubItems);
    };

    const handleQtyChange = (index, value) => {
        const val = parseInt(value, 10);
        if (isNaN(val) || val < 1) return;

        const newSubItems = [...subItems];

        if (index !== 0) {
            const diff = val - newSubItems[index].quantity;
            if (newSubItems[0].quantity - diff < 0) {
                toast.error("La cantidad supera el total disponible.");
                return;
            }
            newSubItems[0].quantity -= diff;
        } else {
            // If user directly edits the default row, we might need to adjust totals,
            // but the rule is the modal sum must equal totalQuantity.
            // Better to make default row qty read-only or auto-calculated.
            return;
        }

        newSubItems[index].quantity = val;
        setSubItems(newSubItems);
    };

    const handlePriceChange = (index, value) => {
        const val = parseFloat(value) || 0;
        const discount = Math.max(0, basePrice - val);
        const newSubItems = [...subItems];
        newSubItems[index].discount = discount;
        setSubItems(newSubItems);
    };

    const handleReasonChange = (index, value) => {
        const newSubItems = [...subItems];
        newSubItems[index].reason = value;
        setSubItems(newSubItems);
    };

    const currentTotalQty = subItems.reduce((acc, curr) => acc + curr.quantity, 0);
    const isValid = currentTotalQty === totalQuantity;

    const handleSave = () => {
        if (!isValid) {
            toast.error("Las cantidades no coinciden con el total.");
            return;
        }
        onSave(item.product.id, subItems);
    };

    return (
        <div className="modal-overlay">
            <div className="modal-content product-config-modal" ref={modalRef}>
                <div className="config-modal-header">
                    <h2>Configurar Producto: {item.product.descripcion}</h2>
                </div>

                <div className="config-modal-body">
                    <div className="config-info-box">
                        <div className="config-info-row">
                            <div className="config-info-label"><strong>Precio Base:</strong></div>
                            <div className="config-info-value">${basePrice.toFixed(2)}</div>
                        </div>
                        <div className="config-info-row">
                            <div className="config-info-label"><strong>Cantidad Total:</strong></div>
                            <div className="config-info-value">{totalQuantity} unidades</div>
                        </div>
                    </div>

                    <div className="config-list-header">
                        <span className="col-qty">Cant.</span>
                        <span className="col-price">Precio Final</span>
                        <span className="col-reason">Razón de Descuento</span>
                        <span className="col-action"></span>
                    </div>

                    <div className="config-list-container" ref={listRef}>
                        {subItems.map((sub, index) => (
                            <div key={index} className="config-row">
                                <div className="col-qty">
                                    <input
                                        type="number"
                                        min="1"
                                        className="config-qty-input"
                                        value={sub.quantity}
                                        readOnly={index === 0}
                                        onChange={(e) => handleQtyChange(index, e.target.value)}
                                        title={index === 0 ? "Cantidad calculada automáticamente" : "Cantidad"}
                                    />
                                </div>
                                <div className="col-price">
                                    <div className="price-input-wrapper">
                                        <span className="currency-symbol">$</span>
                                        <input
                                            type="text"
                                            inputMode="decimal"
                                            className="config-price-input"
                                            value={sub.discount > 0 ? (basePrice - sub.discount).toFixed(2).replace(/\.00$/, '') : basePrice.toFixed(2).replace(/\.00$/, '')}
                                            ref={index === subItems.length - 1 ? lastPriceRef : null}
                                            onChange={(e) => handlePriceChange(index, enforceMoneyFormat(e.target.value))}
                                            onKeyDown={blockNonNumericKeys}
                                            onPaste={sanitizeNumericPaste}
                                        />
                                    </div>
                                </div>
                                <div className="col-reason">
                                    <input
                                        type="text"
                                        className="config-reason-input"
                                        placeholder="Explique el descuento"
                                        value={sub.reason || ''}
                                        onChange={(e) => handleReasonChange(index, e.target.value)}
                                        disabled={index === 0}
                                        title={index === 0 ? "El precio por defecto no lleva razón." : "Motivo del descuento"}
                                    />
                                </div>
                                <div className="col-action">
                                    {index > 0 ? (
                                        <button className="config-remove-btn" onClick={() => handleRemoveRow(index)}>×</button>
                                    ) : (
                                        <div className="config-remove-placeholder"></div>
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>

                    <button
                        className="config-add-btn"
                        onClick={handleAddRow}
                        disabled={subItems[0].quantity <= 1}
                    >
                        + Agregar configuración
                    </button>
                </div>

                <div className="config-modal-actions">
                    <button className="config-cancel-btn" onClick={onClose}>Cancelar</button>
                    <button
                        className="config-confirm-btn"
                        onClick={handleSave}
                        disabled={!isValid}
                    >
                        Guardar Cambios
                    </button>
                </div>
            </div>
        </div>
    );
}
