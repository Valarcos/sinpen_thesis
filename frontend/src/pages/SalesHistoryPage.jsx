import React, { useState, useEffect, useCallback } from 'react';
import api from '../services/api';
import { formatCurrency, formatDate } from '../utils/format';
import SalesDetailModal from '../components/SalesDetailModal';
import CancellationModal from '../components/CancellationModal';
import PartialReturnModal from '../components/PartialReturnModal';
import { generateReceipt, generateDebtorReceipt } from '../utils/pdfGenerator';
import toast from 'react-hot-toast';
import './SalesHistoryPage.css';

export default function SalesHistoryPage() {
    // Default: Last 30 days
    const [startDate, setStartDate] = useState(() => {
        const d = new Date();
        d.setDate(d.getDate() - 30);
        return d.toISOString().split('T')[0];
    });
    const [endDate, setEndDate] = useState(() => new Date().toISOString().split('T')[0]);
    const [searchId, setSearchId] = useState('');

    const [page, setPage] = useState(0);
    const [pageSize] = useState(15);
    const [sales, setSales] = useState([]);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [loading, setLoading] = useState(true);
    const [selectedSale, setSelectedSale] = useState(null);
    const [saleToCancel, setSaleToCancel] = useState(null);
    const [saleToReturn, setSaleToReturn] = useState(null); // For PartialReturnModal
    const [paymentMethods, setPaymentMethods] = useState([]);
    const [isPrinting, setIsPrinting] = useState(false);
    const isMounted = React.useRef(true);

    useEffect(() => {
        return () => { isMounted.current = false; };
    }, []);

    useEffect(() => {
        const fetchPaymentMethods = async () => {
            try {
                const res = await api.get('/ventas/metodos-pago');
                if (isMounted.current) setPaymentMethods(res.data);
            } catch (error) {
                console.error('Error fetching payment methods:', error);
            }
        };
        fetchPaymentMethods();
    }, []);

    const handleSearch = () => {
        if (validateDateRange(startDate, endDate, true)) {
            setPage(0);
            loadSales(0);
        }
    };

    const validateDateRange = (start, end, showToast = true) => {
        const d1 = new Date(start);
        const d2 = new Date(end);
        const diffTime = Math.abs(d2 - d1);
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

        if (d1 > d2) {
            if (showToast) toast.error("Fecha inicio no puede ser mayor a fin");
            return false;
        }
        if (diffDays > 60) {
            if (showToast) toast.error("El rango de fechas elegido no debe superar los 60 días");
            return false;
        }
        return true;
    };

    const loadSales = useCallback(async (pageOverride = null) => {
        setLoading(true);
        try {
            const currentPage = pageOverride !== null ? pageOverride : page;
            const params = {
                page: currentPage,
                size: pageSize,
            };

            // Override date filters if searchId is present
            if (searchId) {
                params.searchId = searchId;
            } else {
                params.startDate = startDate;
                params.endDate = endDate;
            }

            const res = await api.get('/ventas', { params });
            if (!isMounted.current) return;
            // New PageResponse structure
            setSales(res.data.content);
            setTotalPages(res.data.totalPages);
            setTotalElements(res.data.totalElements);
        } catch (error) {
            console.error("Error loading sales", error);
            const msg = error.response?.data?.message;
            if (msg) toast.error(msg);
        } finally {
            if (isMounted.current) setLoading(false);
        }
    }, [page, pageSize, startDate, endDate, searchId]);

    // Debounce the searchId changes to avoid spamming the backend
    useEffect(() => {
        const timeoutId = setTimeout(() => {
            loadSales();
        }, 300);
        return () => clearTimeout(timeoutId);
    }, [searchId, startDate, endDate, loadSales]);

    const handleOpenDetails = async (saleId) => {
        try {
            const res = await api.get(`/ventas/${saleId}`);
            // If it's a FIADO sale, we need the debt info for the modal's printing feature
            let debtorInfo = null;
            if (res.data.tipoVenta === 'FIADO') {
                try {
                    const deudorRes = await api.get(`/deudores/venta/${saleId}`);
                    debtorInfo = deudorRes.data;
                } catch (err) {
                    console.error("Error fetching debtor info for modal:", err);
                }
            }
            if (isMounted.current) setSelectedSale({ ...res.data, debtorInfo });
        } catch (error) {
            console.error("Error fetching sale details", error);
        }
    };
    const handlePrintDirect = async (saleId, printItems = true) => {
        setIsPrinting(true);
        try {
            const saleRes = await api.get(`/ventas/${saleId}`);
            const pagosRes = await api.get(`/ventas/${saleId}/pagos`);
            const methodsRes = await api.get('/ventas/metodos-pago');

            const sale = saleRes.data;
            const pagos = pagosRes.data;
            const methods = methodsRes.data;

            const enrichedPayments = pagos.map(p => {
                const method = methods.find(m => m.id === p.metodoPagoId);
                return {
                    name: method ? method.descripcion : 'Desconocido',
                    amount: p.monto,
                    pagoId: p.id,
                    date: p.fechaPago
                };
            });

            // For historic sales, we also fetch cheques to display explicit cheque dates instead of generic payments
            let cheques = [];
            try {
                const chequesRes = await api.get(`/alertas/cheques/venta/${saleId}`);
                cheques = chequesRes.data;
            } catch (err) {
                console.error('Error fetching cheques for historic PDF:', err);
            }

            const receiptData = {
                id: sale.id,
                date: sale.fecha,
                client: sale.clienteNombre,
                vendedor: sale.vendedorNombre,
                saleType: sale.tipoVenta,
                items: (sale.items || []).map(d => ({
                    codigo: d.productoCodigo || d.codigoSnapshot,
                    descripcion: d.productoNombre || d.descripcionSnapshot,
                    quantity: d.cantidad,
                    unitPrice: d.precioLista || (d.precioUnitario + (d.descuentoValor || 0)),
                    discount: d.descuentoValor || 0,
                    returnedQuantity: d.cantidadDevuelta || 0,
                    reason: d.razonDescuento || null
                })),
                payments: enrichedPayments,
                cheques: cheques,
                total: sale.totalVenta,
                globalDiscount: Math.max(0, Number(sale.descuentoGlobal) || 0),
                globalSurcharge: Math.max(0, Number(sale.recargoGlobal) || 0)
            };

            // If FIADO, fetch debt info and print Debt Receipt
            if (sale.tipoVenta === 'FIADO') {
                let debtorInfo = null;
                let pagosDeuda = [];
                try {
                    const deudorRes = await api.get(`/deudores/venta/${saleId}`);
                    debtorInfo = deudorRes.data;
                    const pagosDeudaRes = await api.get(`/deudores/${debtorInfo.id}/pagos`);
                    pagosDeuda = pagosDeudaRes.data;
                } catch (err) {
                    console.error("Error fetching debtor info for direct print:", err);
                    toast.error("No se pudo cargar la información de la deuda.");
                    setIsPrinting(false);
                    return;
                }

                const debtorData = {
                    ventaId: debtorInfo.ventaId,
                    clienteNombre: debtorInfo.clienteNombre,
                    fechaDeuda: debtorInfo.fechaDeuda,
                    estado: debtorInfo.estado,
                    montoOriginal: debtorInfo.montoOriginal,
                    montoDeuda: debtorInfo.montoDeuda,
                    saleDate: sale.fecha,
                    user: sale.vendedorNombre || 'Sistema',
                    saleType: sale.tipoVenta || 'ESTÁNDAR',
                    items: receiptData.items,
                    pagosDeuda: pagosDeuda,
                    salePayments: enrichedPayments,
                    cheques: cheques,
                    globalDiscount: Math.max(0, Number(sale.descuentoGlobal) || 0),
                    globalSurcharge: Math.max(0, Number(sale.recargoGlobal) || 0)
                };
                generateDebtorReceipt(debtorData, { printItems });
            } else {
                generateReceipt(receiptData, { printItems });
            }
        } catch (error) {
            console.error('Error generating direct print:', error);
            toast.error('Error al generar el PDF. Intente abriendo el detalle.');
        } finally {
            if (isMounted.current) setIsPrinting(false);
        }
    };

    const [isCancelling, setIsCancelling] = useState(false);

    const confirmAnularVenta = async () => {
        if (!saleToCancel) return;
        setIsCancelling(true);
        try {
            await api.post(`/ventas/${saleToCancel}/anular`);
            toast.success("Venta anulada exitosamente.");
            setSaleToCancel(null);
            loadSales();
        } catch (error) {
            console.error("Error anular venta", error);
            // toast.error is handled globally by interceptor now, but keeping this as fallback or letting interceptor handle it
            setSaleToCancel(null);
            loadSales(); // Auto-refresh in case it was a concurrency issue
        } finally {
            if (isMounted.current) setIsCancelling(false);
        }
    };

    return (
        <div className="history-page container">
            <div className="history-header-column">
                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
                    <h1>Historial de Ventas</h1>
                    <input
                        type="text"
                        value={searchId}
                        onChange={(e) => {
                            setSearchId(e.target.value.replace(/\D/g, ''));
                            setPage(0);
                        }}
                        placeholder="Filtrar por ID de venta"
                        style={{ padding: '0.5rem', borderRadius: '4px', border: '1px solid #ccc', minWidth: '200px' }}
                    />
                </div>

                {/* 1. Date Filters Row (Above Pagination) */}
                <div className="date-filters-row" style={{ opacity: searchId ? 0.5 : 1, pointerEvents: searchId ? 'none' : 'auto' }}>
                    <div className="filter-group">
                        <label>Desde:</label>
                        <input
                            type="date"
                            value={startDate}
                            onChange={(e) => setStartDate(e.target.value)}
                            className="date-filter"
                        />
                    </div>
                    <div className="filter-group">
                        <label>Hasta:</label>
                        <input
                            type="date"
                            value={endDate}
                            onChange={(e) => setEndDate(e.target.value)}
                            className="date-filter"
                        />
                    </div>
                    <button onClick={handleSearch} className="primary">
                        Filtrar
                    </button>
                </div>

                {/* 2. Pagination Row (Label Left | Buttons Right) */}
                <div className="pagination-row">
                    <span className="total-label">
                        Total: {totalElements} ventas
                    </span>

                    <div className="pagination-controls">
                        <button
                            onClick={() => setPage(p => p - 1)}
                            disabled={page === 0}
                            className="btn-pagination"
                        >
                            ← Anterior
                        </button>
                        <span className="page-indicator">
                            {page + 1} / {totalPages || 1}
                        </span>
                        <button
                            onClick={() => setPage(p => p + 1)}
                            disabled={page === (totalPages - 1) || totalPages === 0}
                            className="btn-pagination"
                        >
                            Siguiente →
                        </button>
                    </div>
                </div>
            </div>

            {loading ? (
                <p>Cargando ventas...</p>
            ) : (
                <>
                    <div className="table-responsive">
                        <table className="history-table">
                            <thead>
                            <tr>
                                <th>ID</th>
                                <th>Fecha</th>
                                <th>Cliente</th>
                                <th>Tipo</th>
                                <th>Cantidad Productos</th>
                                <th>Costo Total</th>
                                <th>Total</th>
                                <th>Acciones</th>
                            </tr>
                            </thead>
                            <tbody>
                            {/* Req 5: ANULADA rows receive a muted background class for clear visual distinction.
                                 "Ver" button always remains functional for auditing. The "Anular" button is replaced
                                 by a passive read-only label when the sale is already cancelled. */}
                            {(sales || []).map(sale => (
                                <tr key={sale.id} className={sale.estado === 'ANULADA' ? 'row-anulada' : ''}>
                                    <td data-label="ID">#{sale.id}</td>
                                    <td data-label="Fecha">{formatDate(sale.fecha)}</td>
                                    <td data-label="Cliente">{sale.clienteNombre || 'Consumidor Final'}</td>
                                    <td data-label="Tipo">
                                            <span className={`badge ${sale.tipoVenta === 'MAYORISTA' ? 'badge-wholesale' : 'badge-retail'}`}>
                                                {sale.tipoVenta || 'ESTÁNDAR'}
                                            </span>
                                    </td>
                                    <td data-label="Cantidad Productos" style={{textAlign: 'center'}}>{sale.cantidadProductos ?? sale.cantidad_productos ?? 0}</td>
                                    <td data-label="Costo Total" className="amount-cell">{formatCurrency(sale.costoTotal)}</td>
                                    <td data-label="Total" className="amount-cell">{formatCurrency(sale.totalVenta)}</td>
                                    <td data-label="Acciones">
                                        <div className="action-buttons">
                                            <div className="action-buttons-row">
                                                <button className="btn-details" onClick={() => handleOpenDetails(sale.id)}>
                                                    👁️ Ver Detalle
                                                </button>
                                                <button
                                                    className="btn-print"
                                                    onClick={() => handlePrintDirect(sale.id, true)}
                                                    disabled={isPrinting}
                                                    title="Imprimir Remito Completo"
                                                >
                                                    🖨️ Imprimir
                                                </button>
                                                <button
                                                    className="btn-print"
                                                    style={{ backgroundColor: '#6366f1', border: '1px solid #4f46e5' }}
                                                    onClick={() => handlePrintDirect(sale.id, false)}
                                                    disabled={isPrinting}
                                                    title="Imprimir resumen de pagos únicamente"
                                                >
                                                    📄 Pagos
                                                </button>
                                            </div>
                                            <div className="action-buttons-row">
                                                {sale.estado === 'ANULADA' ? (
                                                    <span className="label-cancelada" style={{ width: '100%', textAlign: 'center' }}>CANCELADA</span>
                                                ) : (
                                                    <>
                                                        {sale.estado === 'DEVUELTA_PARCIAL' && (
                                                            <span className="label-devuelta-parcial" style={{background: '#fef3c7', color: '#92400e', padding: '0.4rem 0.5rem', borderRadius: '4px', fontWeight: 'bold', fontSize: '0.75rem'}}>DEV. PARCIAL</span>
                                                        )}
                                                        <button
                                                            className="btn-return"
                                                            onClick={() => setSaleToReturn(sale)}
                                                            title="Registrar devolución parcial"
                                                        >
                                                            ↩ Devolver
                                                        </button>
                                                        {sale.estado !== 'DEVUELTA_PARCIAL' && (
                                                            <button className="btn-delete" onClick={() => setSaleToCancel(sale.id)}>
                                                                ❌ Anular
                                                            </button>
                                                        )}
                                                    </>
                                                )}
                                            </div>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            {sales.length === 0 && (
                                <tr>
                                    <td colSpan="8" style={{ textAlign: 'center' }}>No se encontraron ventas en este período.</td>
                                </tr>
                            )}
                            </tbody>
                        </table>


                    </div>


                    {/* Bottom Pagination - Aligned Right */}
                    {totalPages > 1 && (
                        <div className="pagination-controls bottom-pagination">
                            <button
                                onClick={() => setPage(p => p - 1)}
                                disabled={page === 0}
                                className="btn-pagination"
                            >
                                ← Anterior
                            </button>
                            <span className="page-indicator">
                                {page + 1} / {totalPages || 1}
                            </span>
                            <button
                                onClick={() => setPage(p => p + 1)}
                                disabled={page === (totalPages - 1) || totalPages === 0}
                                className="btn-pagination"
                            >
                                Siguiente →
                            </button>
                        </div>
                    )}
                </>
            )}

            {selectedSale && (
                <SalesDetailModal
                    sale={selectedSale}
                    onClose={() => setSelectedSale(null)}
                    printMode={selectedSale.tipoVenta === 'FIADO' ? 'debtor' : 'ticket'}
                    debtorInfo={selectedSale.debtorInfo}
                />
            )}

            <PartialReturnModal
                isOpen={!!saleToReturn}
                sale={saleToReturn}
                onClose={() => setSaleToReturn(null)}
                onSuccess={loadSales}
            />

            <CancellationModal
                isOpen={!!saleToCancel}
                title="Anular Venta"
                message="¿Está seguro de que desea anular esta venta? Esta acción devolverá el stock y anulará los pagos/deudas. No se puede deshacer."
                onConfirm={confirmAnularVenta}
                onCancel={() => setSaleToCancel(null)}
                isSubmitting={isCancelling}
            />
        </div>
    );
}
