import { useState } from 'react';
import StockManagementModal from './StockManagementModal';
import './NegativeStockCorrectionModal.css';

/**
 * Specialized modal for correcting negative stock from the Dashboard.
 * Shows only products with negative stock.
 * Issues #3.1, #3.2, #3.3
 */
export default function NegativeStockCorrectionModal({ products, onClose, onCorrectionComplete }) {
    const [selectedProduct, setSelectedProduct] = useState(null);

    const handleCorrectionSuccess = () => {
        // Close the inner modal and notify parent to re-fetch stock data
        setSelectedProduct(null);
        if (onCorrectionComplete) {
            onCorrectionComplete();
        }
    };


    return (
        <>
            {/* Outer modal */}
            <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true" aria-label="Corrección de Inventario">
                <div className="neg-correction-modal" onClick={(e) => e.stopPropagation()}>
                    <div className="neg-correction-header">
                        <h2>🔴 Corrección de Inventario</h2>
                        <p>Los siguientes productos tienen stock negativo. Seleccione uno para corregirlo.</p>
                    </div>

                    <div className="neg-correction-table-wrapper">
                        <table className="neg-correction-table">
                            <thead>
                            <tr>
                                <th>Código</th>
                                <th>Descripción</th>
                                <th>Stock</th>
                                <th>Acciones</th>
                            </tr>
                            </thead>
                            <tbody>
                            {(products || []).map((product) => (
                                <tr key={product.id}>
                                    <td data-label="Código">{product.codigo || '-'}</td>
                                    <td data-label="Descripción" className="neg-product-name">{product.descripcion}</td>
                                    <td data-label="Stock">
                                            <span className="neg-stock-cell">
                                                {product.cantidadStock}
                                            </span>
                                    </td>
                                    <td data-label="Acciones">
                                        <button
                                            className="neg-correct-btn"
                                            onClick={() => setSelectedProduct(product)}
                                        >
                                            Corregir Stock
                                        </button>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>

                    <div className="neg-correction-footer">
                        <button onClick={onClose} className="secondary">
                            Cerrar
                        </button>
                    </div>
                </div>
            </div>

            {/* When a product is selected, open StockManagementModal in confirmation mode */}
            {selectedProduct && (
                <StockManagementModal
                    product={selectedProduct}
                    allowNegativeSubtract={false}
                    confirmationMode={true}
                    onClose={() => setSelectedProduct(null)}
                    onSuccess={handleCorrectionSuccess}
                />
            )}
        </>
    );
}
