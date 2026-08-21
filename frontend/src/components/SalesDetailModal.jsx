import { useState, useEffect, useRef } from 'react';
import api from '../services/api';
import { formatCurrency } from '../utils/format';
import { generateReceipt, generateDebtorReceipt } from '../utils/pdfGenerator';
import toast from 'react-hot-toast';
import '../pages/SalesHistoryPage.css';

export default function SalesDetailModal({ sale, onClose, printMode = 'ticket', debtorInfo = null }) {
    const [paymentMethods, setPaymentMethods] = useState([]);
    const isMounted = useRef(true);

    useEffect(() => {
        isMounted.current = true;
        const fetchPaymentMethods = async () => {
            try {
                const res = await api.get('/ventas/metodos-pago');
                if (isMounted.current) setPaymentMethods(res.data);
            } catch (error) {
                console.error("Error fetching payment methods", error);
            }
        };
        fetchPaymentMethods();
        return () => { isMounted.current = false; };
    }, []);

    if (!sale) return null;

    const handlePrintTicket = async () => {
        if (!sale || !(paymentMethods || []).length) return;

        const enrichedPayments = (sale.pagos || []).map(p => {
            const method = (paymentMethods || []).find(m => m.id === p.metodoPagoId);
            return {
                name: method ? method.descripcion : 'Desconocido',
                amount: p.monto,
                pagoId: p.id
            };
        });

        let cheques = [];
        try {
            const chequesRes = await api.get(`/alertas/cheques/venta/${sale.id}`);
            cheques = chequesRes.data;
        } catch (err) {
            console.error('Error fetching cheques for modal PDF:', err);
        }

        const receiptData = {
            id: sale.id,
            date: sale.fecha,
            client: sale.clienteNombre,
            user: sale.vendedorNombre || 'Sistema',
            saleType: sale.tipoVenta || 'ESTÁNDAR',
            items: (sale.items || []).map(d => ({
                codigo: d.productoCodigo || d.codigoSnapshot,
                descripcion: d.productoNombre || d.descripcionSnapshot,
                quantity: d.cantidad,
                returnedQuantity: d.cantidadDevuelta || 0,
                unitPrice: d.precioLista || (d.precioUnitario + (d.descuentoValor || 0)),
                discount: d.descuentoValor || 0,
                reason: d.razonDescuento || null,
                subtotal: d.subtotal
            })),
            payments: enrichedPayments,
            cheques: cheques,
            total: sale.totalVenta,
            globalDiscount: Math.max(0, Number(sale.descuentoGlobal) || 0),
            globalSurcharge: Math.max(0, Number(sale.recargoGlobal) || 0)
        };

        generateReceipt(receiptData);
    };

    // Lazy-fetch: only called when user clicks the print button in debtor context
    const handlePrintDeuda = async () => {
        if (!sale || !debtorInfo) return;
        try {
            const pagosRes = await api.get(`/deudores/${debtorInfo.id}/pagos`);
            const pagos = pagosRes.data;
            const methods = paymentMethods || [];

            const enrichedSalePayments = (sale.pagos || []).map(p => {
                const method = methods.find(m => m.id === p.metodoPagoId);
                return { name: method ? method.descripcion : 'Desconocido', amount: p.monto, pagoId: p.id };
            });

            let cheques = [];
            try {
                const chequesRes = await api.get(`/alertas/cheques/venta/${sale.id}`);
                cheques = chequesRes.data;
            } catch (err) {
                console.error('Error fetching cheques for debtor modal PDF:', err);
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
                items: (sale.items || []).map(d => ({
                    codigo: d.productoCodigo || d.codigoSnapshot,
                    descripcion: d.productoNombre || d.descripcionSnapshot,
                    quantity: d.cantidad,
                    returnedQuantity: d.cantidadDevuelta || 0,
                    unitPrice: d.precioLista || (d.precioUnitario + (d.descuentoValor || 0)),
                    discount: d.descuentoValor || 0,
                    reason: d.razonDescuento || null,
                    subtotal: d.subtotal
                })),
                pagosDeuda: pagos,
                salePayments: enrichedSalePayments,
                cheques: cheques,
                globalDiscount: Math.max(0, Number(sale.descuentoGlobal) || 0),
                globalSurcharge: Math.max(0, Number(sale.recargoGlobal) || 0)
            };

            generateDebtorReceipt(debtorData);
        } catch (error) {
            console.error('Error generating debtor PDF from modal:', error);
            toast.error('Error al generar el PDF de deuda');
        }
    };

    return (
        <div className="modal-overlay">
            {/* Class handles width now */}
            <div className="sales-detail-modal-content">
                <div className="modal-header">
                    <h2>Detalle de Venta #{sale.id}</h2>
                    {/* X button removed per request */}
                </div>

                <div className="sale-info">
                    <p><strong>Fecha:</strong> {sale.fecha}</p>
                    <p><strong>Cliente:</strong> {sale.clienteNombre || 'Consumidor Final'}</p>
                    <p><strong>Total:</strong> {formatCurrency(sale.totalVenta)}</p>
                    <p><strong>Cantidad de Productos:</strong> {sale.cantidadProductos || sale.items?.reduce((sum, item) => sum + (item.cantidad || 0), 0) || 0}</p>
                    {sale.descuentoGlobal > 0 && (
                        <p style={{ color: 'green' }}><strong>Descuento Global:</strong> -{formatCurrency(sale.descuentoGlobal)}</p>
                    )}
                    {sale.recargoGlobal > 0 && (
                        <p style={{ color: '#dc2626' }}><strong>Recargo Global:</strong> +{formatCurrency(sale.recargoGlobal)}</p>
                    )}
                </div>

                <div className="table-responsive modal-table-container">
                    <table className="history-table">
                        <thead>
                        <tr>
                            <th>Producto</th>
                            <th>Cant</th>
                            <th>P. Unit</th>
                            <th>Subtotal</th>
                        </tr>
                        </thead>
                        <tbody>
                        {(sale.items || []).map((d, index) => (
                            <tr key={index}>
                                <td data-label="Producto">
                                    <div className="product-name-cell">{d.descripcionSnapshot || d.productoNombre}</div>
                                    <small className="text-muted">{d.codigoSnapshot || d.productoCodigo}</small>
                                </td>
                                <td data-label="Cant">
                                    {d.cantidad}
                                    {d.cantidadDevuelta > 0 && (
                                        <span style={{color: '#dc2626', fontSize: '0.8rem', marginLeft: '4px', fontWeight: 'bold'}}>
                                            (-{d.cantidadDevuelta})
                                        </span>
                                    )}
                                </td>
                                <td data-label="P. Unit">{formatCurrency(d.precioUnitario)}</td>
                                <td data-label="Subtotal">{formatCurrency(d.delineaTotal || d.subtotal)}</td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>

                <div className="modal-actions">
                    <button onClick={onClose} className="btn-confirm secondary-action">Cerrar</button>
                    {printMode === 'debtor' ? (
                        <button
                            onClick={handlePrintDeuda}
                            className="btn-print primary-action"
                        >
                            🖨️ Imprimir Deuda
                        </button>
                    ) : (
                        <button
                            onClick={handlePrintTicket}
                            className="btn-print primary-action"
                        >
                            🖨️ Imprimir Ticket
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}
