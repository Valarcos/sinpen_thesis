import { useState, useEffect, useCallback, useRef } from 'react';
import { useBlocker } from 'react-router-dom';
import toast from 'react-hot-toast';
import api from '../services/api';
import ProductFormModal from '../components/ProductFormModal';
import DeleteProductModal from '../components/DeleteProductModal';
import StockManagementModal from '../components/StockManagementModal';
import LocationManagementModal from '../components/LocationManagementModal';
import StockEntryModal from '../components/StockEntryModal'; // Imported
import JumpButtons from '../components/JumpButtons';
import './InventarioPage.css';

export default function InventarioPage() {
    const [products, setProducts] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const isMounted = useRef(true);

    // ... modals state ...
    const [showProductForm, setShowProductForm] = useState(false);
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [showStockModal, setShowStockModal] = useState(false);
    const [showLocationModal, setShowLocationModal] = useState(false);
    const [showPurchaseModal, setShowPurchaseModal] = useState(false); // New State

    const [editingProduct, setEditingProduct] = useState(null);
    const [deletingProduct, setDeletingProduct] = useState(null);
    const [isDeleting, setIsDeleting] = useState(false);
    const [stockProduct, setStockProduct] = useState(null);

    // Issue #63: Warn user before navigating away with open data-entry modals
    const hasOpenModal = showProductForm || showStockModal || showLocationModal || showPurchaseModal;
    const blocker = useBlocker(hasOpenModal);
    useEffect(() => {
        if (blocker.state === 'blocked') {
            const leave = window.confirm('⚠️ ¿Desea salir?\n\nTiene cambios sin guardar que se perderán si abandona esta página.');
            if (leave) {
                blocker.proceed();
            } else {
                blocker.reset();
            }
        }
    }, [blocker]);

    const fetchProducts = useCallback(async (currentPage = 0, isBackground = false) => {
        try {
            if (!isBackground && isMounted.current) {
                setLoading(true);
            }

            // Determine if we are searching or browsing
            const isSearching = searchQuery && searchQuery.trim().length > 0;

            // Build Params
            const params = {};
            if (isSearching) {
                params.search = searchQuery;
                // Backend handles search with LIMIT 100, ignores page/size usually
                // but we pass them anyway just in case
            } else {
                params.page = currentPage;
                params.size = 15; // Updated to 15 per user request
            }

            const response = await api.get('/productos', { params });

            if (!isMounted.current) return;

            // Unified Response: Always PageResponse
            setProducts(response.data.content);
            setTotalPages(response.data.totalPages);
            setTotalElements(response.data.totalElements);

            // Only update page if browsing (or reset to 0 if searching returns page 0)
            if (!isSearching) {
                setPage(response.data.page);
            } else {
                setPage(0); // Search always returns page 0
            }

        } catch (error) {
            console.error('Error fetching products:', error);
            // Error handled by global api interceptor
        } finally {
            if (!isBackground && isMounted.current) {
                setLoading(false);
            }
        }
    }, [searchQuery]);

    // Initial Load
    useEffect(() => {
        isMounted.current = true;
        setPage(0); // Reset page on simple reload? Or keep? Reset seems safer.
        fetchProducts(0, false);
        return () => { isMounted.current = false; };
    }, [fetchProducts]); // Dependencies: fetchProducts depends on 'searchQuery'

    // Auto-refresh interval
    useEffect(() => {
        const intervalId = setInterval(() => {
            // fetchProducts(page, true);
        }, 30000);

        return () => clearInterval(intervalId);
    }, [fetchProducts]);

    // Handle Search Input Change
    useEffect(() => {
    }, [searchQuery]);


    const handlePageChange = (newPage) => {
        if (newPage >= 0 && newPage < totalPages) {
            setPage(newPage);
            fetchProducts(newPage, false);
        }
    };

    // ... Modals Handlers ...
    const handleCreateProduct = () => {
        setEditingProduct(null);
        setShowProductForm(true);
    };

    const handleEditProduct = (product) => {
        setEditingProduct(product);
        setShowProductForm(true);
    };

    const handleDeleteClick = (product) => {
        setDeletingProduct(product);
        setShowDeleteModal(true);
    };

    const handleStockClick = (product) => {
        setStockProduct(product);
        setShowStockModal(true);
    };

    const handleStockSuccess = () => fetchProducts(page);
    const handleProductFormSuccess = () => {
        setShowProductForm(false);
        setEditingProduct(null);
        fetchProducts(page);
    };
    const handleDeleteConfirm = async () => {
        if (!deletingProduct || isDeleting) return;
        try {
            setIsDeleting(true);
            await api.delete(`/productos/${deletingProduct.id}`);
            toast.success('Producto eliminado correctamente');
            setShowDeleteModal(false);
            setDeletingProduct(null);
            fetchProducts(page);
        } catch (error) {
            console.error('Error deleting product:', error);
            const message = error.response?.data?.message;
            if (message) toast.error(message);
            // Vector 2: Force re-fetch on failure to resync state
            fetchProducts(page);
        } finally {
            if (isMounted.current) setIsDeleting(false);
        }
    };

    // Purchase Success
    const handlePurchaseSuccess = () => {
        setShowPurchaseModal(false);
        fetchProducts(page); // Refresh inventory check
    };

    const getStockClass = (product) => {
        const stock = product.cantidadStock || 0;
        if (stock < 0) return 'stock-badge negative-stock';
        if (stock === 0) return 'stock-badge out-of-stock';
        if (stock <= 5) return 'stock-badge low-stock';
        return 'stock-badge in-stock';
    };

    /**
     * Returns 'variant-accent' CSS class if the product belongs to a family with multiple variants.
     * Generic products (codigo='1') are excluded because '1' is a shared bucket for unrelated items.
     */
    const getVariantAccentClass = (product, allProducts) => {
        if (product.codigo === '1') return '';
        const siblings = allProducts.filter(p => p.codigo === product.codigo);
        return siblings.length > 1 ? 'variant-accent' : '';
    };

    const formatCurrency = (amount) => {
        if (amount == null) return '-';
        return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(amount);
    };

    return (
        <div className="inventario-page container">
            <header className="inventario-header">
                <h1>📦 Gestión de Inventario</h1>
                <div className="header-actions">
                    <button
                        onClick={() => setShowPurchaseModal(true)}
                        className="action-btn stock"
                        style={{ marginRight: '1rem', backgroundColor: '#d97706', fontSize: '0.9rem' }}
                    >
                        📥 Registrar Compra
                    </button>

                    <button onClick={() => setShowLocationModal(true)} className="secondary" style={{ marginRight: '1rem' }}>
                        📍 Gestionar Ubicaciones
                    </button>
                    <button onClick={handleCreateProduct} className="primary" aria-label="Agregar nuevo producto">
                        + Agregar Producto
                    </button>
                </div>
            </header>

            {/* Search Bar */}
            <div className="search-container">
                <input
                    id="search-input"
                    type="text"
                    placeholder="🔍 Buscar por descripción o código de artículo..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="search-input"
                    aria-label="Buscar productos"
                />
                {searchQuery && (
                    <button onClick={() => setSearchQuery('')} className="clear-search" aria-label="Limpiar búsqueda">✕</button>
                )}
            </div>

            {/* Results Info & Pagination (Top) */}
            <div className="results-info-bar">
                <p className="results-info" aria-live="polite">
                    {loading ? 'Cargando...' :
                        searchQuery ? `Mostrando ${products.length} resultados (Top 100)` :
                            `Total: ${totalElements} productos | Página ${page + 1} de ${totalPages}`
                    }
                </p>

                {/* Pagination Controls */}
                {!searchQuery && totalPages > 1 && (
                    <div className="pagination-controls">
                        <button
                            onClick={() => handlePageChange(page - 1)}
                            disabled={page === 0 || loading}
                            className="btn-pagination"
                        >
                            ← Anterior
                        </button>
                        <span className="page-indicator">
                            {page + 1} / {totalPages}
                        </span>
                        <button
                            onClick={() => handlePageChange(page + 1)}
                            disabled={page >= totalPages - 1 || loading}
                            className="btn-pagination"
                        >
                            Siguiente →
                        </button>
                    </div>
                )}
            </div>

            {loading ? (
                <div className="loading-state"><p>Cargando productos...</p></div>
            ) : products.length === 0 ? (
                <div className="empty-state">
                    <p>{searchQuery ? 'No se encontraron productos' : 'No hay productos registrados'}</p>
                </div>
            ) : (
                <>

                    <div className="products-table-container">
                        <table className="products-table" aria-label="Lista de productos">
                            <thead>
                            <tr>
                                <th scope="col">Código</th>
                                <th scope="col">Descripción</th>
                                <th scope="col" className="cost-column">Costo</th>
                                <th scope="col">Mayorista</th>
                                <th scope="col">Minorista</th>
                                <th scope="col">Stock</th>
                                <th scope="col">Acciones</th>
                            </tr>
                            </thead>
                            <tbody>
                            {(products || []).map((product) => (
                                <tr key={product.id} className={getVariantAccentClass(product, products)}>
                                    <td data-label="Código">{product.codigo || '-'}</td>
                                    <td data-label="Descripción" className="product-name-cell">{product.descripcion}</td>
                                    <td data-label="Costo" className="cost-column">{formatCurrency(product.precioCosto)}</td>
                                    <td data-label="Mayorista">{formatCurrency(product.precioMayorista)}</td>
                                    <td data-label="Minorista" className="price-retail">{formatCurrency(product.precioMinorista)}</td>
                                    <td data-label="Stock">
                                            <span className={getStockClass(product)}>
                                                {product.cantidadStock} unidades
                                            </span>
                                    </td>
                                    <td className="inventario-actions-cell">
                                        <div className="actions-container">
                                            <button onClick={() => handleStockClick(product)} className="action-btn stock">📦 Stock</button>
                                            <button onClick={() => handleEditProduct(product)} className="action-btn edit">✏️ Editar</button>
                                            <button onClick={() => handleDeleteClick(product)} className="action-btn delete">🗑️ Eliminar</button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>



                    {/* Bottom Pagination - Aligned Right */}
                    {totalPages > 1 && (
                        <div className="pagination-controls" style={{ marginTop: '1rem' }}>
                            <button
                                onClick={() => handlePageChange(page - 1)}
                                disabled={page === 0 || loading}
                                className="btn-pagination"
                            >
                                ← Anterior
                            </button>
                            <span className="page-indicator">
                                {page + 1} / {totalPages}
                            </span>
                            <button
                                onClick={() => handlePageChange(page + 1)}
                                disabled={page >= totalPages - 1 || loading}
                                className="btn-pagination"
                            >
                                Siguiente →
                            </button>
                        </div>
                    )}
                </>
            )}
            <JumpButtons targetTopSelector=".inventario-page" targetBottomSelector="#inventario-bottom-marker" />


            {showProductForm && (
                <ProductFormModal
                    product={editingProduct}
                    onSuccess={handleProductFormSuccess}
                    onCancel={() => {
                        setShowProductForm(false);
                        setEditingProduct(null);
                    }}
                />
            )}

            {showDeleteModal && deletingProduct && (
                <DeleteProductModal
                    product={deletingProduct}
                    onConfirm={handleDeleteConfirm}
                    loading={isDeleting}
                    onCancel={() => {
                        if (!isDeleting) {
                            setShowDeleteModal(false);
                            setDeletingProduct(null);
                        }
                    }}
                />
            )}

            {showStockModal && stockProduct && (
                <StockManagementModal
                    product={stockProduct}
                    onClose={() => {
                        setShowStockModal(false);
                        setStockProduct(null);
                    }}
                    onSuccess={handleStockSuccess}
                />
            )}

            {showLocationModal && (
                <LocationManagementModal
                    onClose={() => setShowLocationModal(false)}
                    onLocationAdded={() => {
                        toast.success('Lista de ubicaciones actualizada');
                    }}
                />
            )}

            {showPurchaseModal && (
                <StockEntryModal
                    onClose={() => setShowPurchaseModal(false)}
                    onSuccess={handlePurchaseSuccess}
                />
            )}
            <div id="inventario-bottom-marker" style={{ height: 1 }} />
        </div>
    );
}
