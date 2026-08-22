import { useState, useEffect, useRef } from 'react';
import './MorningAlert.css';

/**
 * Morning Alert Modal - displays low stock warnings on dashboard load.
 * Fetches /api/productos/alerts and shows modal if products are below stock_minimo.
 */
export default function MorningAlert({ lowStockProducts, onDismiss }) {
    const dismissBtnRef = useRef(null);

    useEffect(() => {
        dismissBtnRef.current?.focus();
    }, []);

    if (!lowStockProducts || lowStockProducts.length === 0) {
        return null;
    }

    return (
        <div className="morning-alert-overlay" onClick={onDismiss}>
            <div
                className="morning-alert-modal"
                onClick={(e) => e.stopPropagation()}
                role="alertdialog"
                aria-labelledby="morning-alert-title"
                aria-describedby="morning-alert-description"
            >
                <div className="morning-alert-header">
                    <span className="alert-icon" aria-hidden="true">⚠️</span>
                    <h2 id="morning-alert-title">¡Atención! Productos con bajo stock</h2>
                </div>

                <div id="morning-alert-description" className="morning-alert-content">
                    <p className="alert-intro">
                        Los siguientes productos están por debajo del stock mínimo:
                    </p>

                    <ul className="low-stock-list" aria-label="Lista de productos con bajo stock">
                        {(lowStockProducts || []).map((product) => (
                            <li key={product.id} className="low-stock-item">
                                <span className="product-name">{product.nombre}</span>
                                <span className="product-stock">
                                    <strong>{product.stock}</strong> / {product.stockMinimo} unidades
                                </span>
                            </li>
                        ))}
                    </ul>

                    <p className="alert-action">
                        💡 <strong>Sugerencia:</strong> Realice un pedido a sus proveedores pronto.
                    </p>
                </div>

                <div className="morning-alert-actions">
                    <button
                        ref={dismissBtnRef}
                        onClick={onDismiss}
                        className="primary"
                        aria-label="Entendido, cerrar alerta"
                    >
                        Entendido
                    </button>
                </div>
            </div>
        </div>
    );
}
