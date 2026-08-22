import React from 'react';
import './ConfirmationModal.css';

export default function ClientChangeModal({
                                              isOpen,
                                              onCancel,
                                              onOption1,
                                              onOption2,
                                              onOption3,
                                              initialName,
                                              newName,
                                              isSubmitting
                                          }) {
    if (!isOpen) return null;

    return (
        <div className="modal-overlay">
            <div className="modal-content stock-warning-modal">
                <div className="modal-header">
                    <h2>⚠️ Cambio de Cliente Detectado</h2>
                </div>

                <div className="modal-body" style={{ textAlign: 'left', fontSize: '0.95rem' }}>
                    <p>
                        Se detectó un cambio en el nombre del cliente asociado a este pedido pendiente:
                    </p>
                    <ul style={{ margin: '10px 0', paddingLeft: '20px' }}>
                        <li><strong>Original:</strong> {initialName}</li>
                        <li><strong>Nuevo:</strong> {newName}</li>
                    </ul>
                    <p style={{ marginBottom: '15px' }}>¿Qué desea hacer?</p>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                        <button
                            className="btn-primary"
                            onClick={onOption1}
                            style={{ textAlign: 'left', padding: '12px' }}
                            disabled={isSubmitting}
                        >
                            <strong>1. Corregir error de tipeo</strong><br/>
                            <span style={{ fontSize: '0.85rem', fontWeight: 'normal' }}>
                                Mantiene el cliente original, sus créditos y deudas, pero actualiza su nombre en todo el sistema.
                            </span>
                        </button>

                        <button
                            className="btn-primary"
                            style={{ backgroundColor: '#f39c12', textAlign: 'left', padding: '12px' }}
                            onClick={onOption2}
                            disabled={isSubmitting}
                        >
                            <strong>2. Cambiar de cliente</strong><br/>
                            <span style={{ fontSize: '0.85rem', fontWeight: 'normal' }}>
                                Asigna este pedido a un cliente completamente distinto (nuevo o existente).
                            </span>
                        </button>

                        <button
                            className="btn-secondary"
                            onClick={onOption3}
                            style={{ textAlign: 'left', padding: '12px' }}
                            disabled={isSubmitting}
                        >
                            <strong>3. Cancelar cambio</strong><br/>
                            <span style={{ fontSize: '0.85rem', fontWeight: 'normal' }}>
                                Restaura el nombre original ({initialName}) y guarda el pedido.
                            </span>
                        </button>
                    </div>
                </div>

                <div className="modal-actions" style={{ justifyContent: 'center', marginTop: '15px' }}>
                    <button className="btn-secondary" onClick={onCancel} disabled={isSubmitting}>
                        Cerrar y seguir editando
                    </button>
                </div>
            </div>
        </div>
    );
}
