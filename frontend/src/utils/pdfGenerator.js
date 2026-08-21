import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { LOGO_BASE64, LOGO_FORMAT } from './logoBase64';

// --- HELPERS ---

/**
 * Formats a number as $X,XXX,XXX.XX with thousand separators.
 */
const formatMoney = (val) => {
    const num = (val || 0).toFixed(2);
    return '$' + num.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
};

/**
 * Formats a date string as DD/MM/YYYY — the user-facing format required for PDFs.
 * Parses dates safely to avoid UTC offset bugs.
 */
const formatDateDDMMYYYY = (dateStr) => {
    if (!dateStr) return 'N/A';
    const match = String(dateStr).match(/(\d{4})-(\d{2})-(\d{2})/);
    if (match) return `${match[3]}/${match[2]}/${match[1]}`;
    // Fallback: parse with local time forced
    const safe = String(dateStr).includes('T') ? dateStr : dateStr + 'T12:00:00';
    const d = new Date(safe);
    if (isNaN(d.getTime())) return String(dateStr);
    return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
};

/**
 * Draws the company logo in the upper-right corner of the given jsPDF document.
 * The image is drawn BEFORE any tables/text so it acts as a background layer —
 * jsPDF renders in draw order, meaning subsequent text/tables appear on top.
 * This is a no-op if LOGO_BASE64 is empty (see logoBase64.js).
 *
 * @param {jsPDF} doc - The active jsPDF document instance.
 */
const addLogoToDoc = (doc) => {
    if (!LOGO_BASE64) return;
    const pageWidth = doc.internal.pageSize.width;
    // Logo dimensions and position (upper-right corner, 8mm from edges)
    const logoWidth = 40;
    const logoHeight = 20;
    const logoX = pageWidth - logoWidth - 8;
    const logoY = 5;
    doc.addImage(LOGO_BASE64, LOGO_FORMAT, logoX, logoY, logoWidth, logoHeight);
};

// --- SALE RECEIPT PDF ---

/**
 * Generates a sale receipt PDF (Ticket de Venta).
 * Shows both the sale registration date and the PDF print date clearly.
 */
export const generateReceipt = (saleData, options = { printItems: true }) => {
    try {
        const doc = new jsPDF();
        const pageWidth = doc.internal.pageSize.width;

        // --- LOGO (drawn first so it renders as background layer) ---
        addLogoToDoc(doc);

        // --- HEADER ---
        doc.setFontSize(18);
        doc.text("REMITO", pageWidth / 2, 15, { align: 'center' });

        doc.setFontSize(10);
        doc.setFont(undefined, 'bold');
        doc.text('Presupuesto', 14, 25);
        doc.setFont(undefined, 'normal');

        doc.setFontSize(10);
        doc.text(`Venta ID: #${saleData.id}`, 14, 32);
        doc.text(`Fecha de Venta: ${formatDateDDMMYYYY(saleData.date)}`, 14, 37);

        // PDF print date (smaller, gray)
        doc.setFontSize(8);
        doc.setTextColor(120);
        doc.text(`Fecha de Impresión: ${formatDateDDMMYYYY(new Date().toISOString())}`, 14, 42);
        doc.setTextColor(0);
        doc.setFontSize(10);

        doc.text(`Cliente: ${saleData.client || 'Consumidor Final'}`, 14, 49);
        doc.text(`Vendedor: ${saleData.vendedor || saleData.user || 'Sistema'}`, 14, 54);
        doc.text(`Tipo Venta: ${saleData.saleType}`, 14, 59);

        if (options.printItems) {
            const totalUnidades = saleData.items.reduce((acc, item) => acc + (Number(item.quantity) || 0), 0);
            doc.setFont(undefined, 'bold');
            doc.text(`Total de Artículos: ${totalUnidades}`, 14, 64);
            doc.setFont(undefined, 'normal');
        } else {
            doc.setFontSize(12);
            doc.setFont(undefined, 'bold');
            doc.text("RESUMEN DE PAGOS", 14, 64);
            doc.setFont(undefined, 'normal');
            doc.lastAutoTable = { finalY: 64 };
        }

        // --- TABLE 1: ITEMS ---
        let hasReturns = false;
        let totalDevueltoFromItems = 0;

        if (options.printItems) {

            const itemsBody = saleData.items.map(item => {
                const unitPrice = item.unitPrice || 0;
                const discount = item.discount || 0;
                const finalUnit = Math.max(0, unitPrice - discount);
                const returnedQty = item.returnedQuantity || 0;
                const effectiveQty = item.quantity - returnedQty;
                const subtotal = finalUnit * effectiveQty;

                if (returnedQty > 0) {
                    hasReturns = true;
                    totalDevueltoFromItems += finalUnit * returnedQty;
                }

                return [
                    item.codigo || 'N/A',
                    item.descripcion,
                    returnedQty > 0 ? `${effectiveQty} (Orig: ${item.quantity}, Dev: ${returnedQty})` : `${item.quantity}`,
                    formatMoney(unitPrice),
                    discount > 0 ? `-${formatMoney(discount)}` : '-',
                    formatMoney(subtotal)
                ];
            });

            autoTable(doc, {
                startY: 69,
                head: [['Código', 'Descripción', 'Cant.', 'Precio', 'Desc.', 'Subtotal']],
                body: itemsBody,
                theme: 'grid',
                headStyles: { fillColor: [0, 0, 0], textColor: 255 },
                columnStyles: {
                    0: { cellWidth: 20 },
                    1: { cellWidth: 'auto' },
                    2: { cellWidth: 22, halign: 'center' },
                    3: { cellWidth: 22, halign: 'right' },
                    4: { cellWidth: 22, halign: 'right' },
                    5: { cellWidth: 28, halign: 'right' }
                },
                styles: { fontSize: 8, cellPadding: 1 },
            });
        } // End options.printItems

        // --- TABLE 2: DISCOUNTS SUMMARY (before payments, black header) ---
        const discountRows = [];
        const hasReason = saleData.items.some(item => item.discount > 0 && item.reason);

        saleData.items.forEach(item => {
            const discount = item.discount || 0;
            const returnedQty = item.returnedQuantity || 0;
            const effectiveQty = item.quantity - returnedQty;

            if (discount > 0 && effectiveQty > 0) {
                if (hasReason) {
                    discountRows.push([
                        `${item.descripcion} (x${effectiveQty})`,
                        item.reason || '-',
                        `-${formatMoney(discount * effectiveQty)}`
                    ]);
                } else {
                    discountRows.push([
                        `${item.descripcion} (x${effectiveQty})`,
                        `-${formatMoney(discount * effectiveQty)}`
                    ]);
                }
            }
        });

        if (saleData.globalDiscount > 0) {
            if (hasReason) {
                discountRows.push([
                    'Descuento Global',
                    '-',
                    `-${formatMoney(saleData.globalDiscount)}`
                ]);
            } else {
                discountRows.push([
                    'Descuento Global',
                    `-${formatMoney(saleData.globalDiscount)}`
                ]);
            }
        }

        if (saleData.globalSurcharge > 0) {
            if (hasReason) {
                discountRows.push([
                    'Recargo Global',
                    '-',
                    `${formatMoney(saleData.globalSurcharge)}`
                ]);
            } else {
                discountRows.push([
                    'Recargo Global',
                    `${formatMoney(saleData.globalSurcharge)}`
                ]);
            }
        }

        if (discountRows.length > 0) {
            let discountY = doc.lastAutoTable.finalY + 10;
            const title = (saleData.globalSurcharge > 0) ? 'Descuento / Recargo' : 'Descuento';

            autoTable(doc, {
                startY: discountY,
                head: hasReason ? [[title, 'Motivo', 'Monto']] : [[title, 'Monto']],
                body: discountRows,
                theme: 'grid',
                headStyles: { fillColor: [0, 0, 0], textColor: 255 },
                columnStyles: hasReason
                    ? { 0: { cellWidth: 'auto' }, 1: { cellWidth: 'auto' }, 2: { cellWidth: 40, halign: 'right' } }
                    : { 0: { cellWidth: 'auto' }, 1: { cellWidth: 40, halign: 'right' } },
                styles: { fontSize: 8, cellPadding: 1 },
            });
        }
        // End Table 2

        // --- TABLE 3: PAYMENT METHODS (blue header) ---
        let payY = doc.lastAutoTable.finalY + 10;
        let negativePaymentsExist = false;
        let totalReembolsoEfectivo = 0;

        const regularPagos = (saleData.payments || []).map(p => ({
            date: p.date,
            name: p.name,
            amount: p.amount,
            pagoId: p.pagoId,
            isCheque: false
        }));

        const chequePagos = (saleData.cheques || []).map(c => ({
            date: saleData.fechaCreacion || saleData.date,
            name: c.metodoPagoNombre || 'Cheque',
            amount: c.monto,
            pagoId: c.pagoVentaId,
            isCheque: true,
            chequeInfo: c
        }));

        const uncashedCheques = chequePagos.filter(c => {
            return !c.pagoId || !regularPagos.some(p => p.pagoId === c.pagoId);
        });

        const allPayments = [...regularPagos, ...uncashedCheques].sort((a, b) => {
            return new Date(a.date || 0) - new Date(b.date || 0);
        });

        if (saleData.isCheque) {
            doc.setFontSize(10);
            doc.setTextColor(0, 80, 160);
            doc.text("Pagos: A cobrarse vía cheques", 14, payY);
            doc.setTextColor(0);
            doc.lastAutoTable = { finalY: payY + 5 };
        } else if (allPayments.length > 0) {
            const hasCheques = saleData.cheques && saleData.cheques.length > 0;
            const hasDetalle = hasCheques || allPayments.some(p => p.name.toLowerCase().includes('cheque') || p.isCheque);

            let availableCheques = saleData.cheques ? [...saleData.cheques] : [];
            const paymentsBody = allPayments.map(p => {
                let methodStr = p.name;
                if (p.amount < 0) {
                    negativePaymentsExist = true;
                    totalReembolsoEfectivo += Math.abs(p.amount);
                    methodStr = `Reembolso (${p.name})`;
                }

                let detalle = '';
                if (hasDetalle) {
                    let matchedCheque = null;
                    if (p.isCheque) {
                        matchedCheque = p.chequeInfo;
                        const idx = availableCheques.findIndex(c => c.id === p.chequeInfo.id);
                        if (idx !== -1) availableCheques.splice(idx, 1);
                    } else {
                        let matchedIdx = availableCheques.findIndex(c => c.pagoVentaId && c.pagoVentaId === p.pagoId);
                        if (matchedIdx === -1 && p.name.toLowerCase().includes('cheque')) {
                            matchedIdx = availableCheques.findIndex(c => c.monto === p.amount);
                        }
                        if (matchedIdx !== -1) {
                            matchedCheque = availableCheques.splice(matchedIdx, 1)[0];
                        }
                    }

                    if (matchedCheque) {
                        const isCobrado = matchedCheque.estado === 'COBRADO';
                        const dateLabel = isCobrado ? `Cobrado el ${formatDateDDMMYYYY(matchedCheque.fechaCobro)}` : `A cobrar el ${formatDateDDMMYYYY(matchedCheque.fechaCobro)}`;
                        detalle = `${matchedCheque.estado} (${dateLabel})`;
                    }
                }

                // FIX: Added Fecha to the array to match the new header
                if (hasDetalle) {
                    return [p.date ? formatDateDDMMYYYY(p.date) : formatDateDDMMYYYY(saleData.date), methodStr, detalle, formatMoney(p.amount)];
                } else {
                    return [p.date ? formatDateDDMMYYYY(p.date) : formatDateDDMMYYYY(saleData.date), methodStr, formatMoney(p.amount)];
                }
            });

            autoTable(doc, {
                startY: payY,
                head: hasDetalle ? [['Fecha', 'Método de Pago', 'Detalle / Estado', 'Monto']] : [['Fecha', 'Método de Pago', 'Monto']],
                body: paymentsBody,
                theme: 'grid',
                headStyles: { fillColor: [0, 80, 160], textColor: 255 },
                columnStyles: hasDetalle ? {
                    0: { cellWidth: 20 },
                    1: { cellWidth: 'auto' },
                    2: { cellWidth: 'auto', fontStyle: 'italic' },
                    3: { cellWidth: 30, halign: 'right' }
                } : {
                    0: { cellWidth: 20 },
                    1: { cellWidth: 'auto' },
                    2: { cellWidth: 30, halign: 'right' }
                },
                styles: { fontSize: 8, cellPadding: 1 },
            });
        }

        // --- TOTAL ---
        let finalY = doc.lastAutoTable.finalY + 8;
        doc.setFontSize(12);

        if (hasReturns) {
            // Apply proportional global discount to return if applicable to get exact totals,
            // or just use backend's calculation. If we don't have the backend's exact totalReembolso,
            // we can approximate it or just trust `totalDevueltoFromItems` as base, but we actually
            // should just calculate the total final as sum of effective subtotals minus global discount.

            // Re-calculate the effective final total from the ground up:
            let totalFinal = saleData.items.reduce((acc, item) => {
                const effectiveQty = item.quantity - (item.returnedQuantity || 0);
                const finalUnit = Math.max(0, (item.unitPrice || 0) - (item.discount || 0));
                return acc + (finalUnit * effectiveQty);
            }, 0);

            // Deduct global discount fully or proportionately? If sale was partially returned, global discount was proportional.
            // A simpler way to show it is Total Original vs Total Final

            // To perfectly reflect backend, let's just use what we have
            const totalOriginal = saleData.total;
            let refundCashDeduction = totalReembolsoEfectivo;

            // If they refunded via store credit, it won't show in negative payments. We'll just present the delta.
            let totalDevueltoCalculated = totalDevueltoFromItems;

            // Let's just do a clean summary:
            doc.setFont(undefined, 'normal');
            doc.text(`TOTAL ORIGINAL:`, pageWidth - 45, finalY, { align: 'right' });
            doc.text(`${formatMoney(totalOriginal)}`, pageWidth - 14, finalY, { align: 'right' });

            finalY += 6;
            doc.setTextColor(200, 0, 0); // Red for devoluciones
            doc.text(`DEVOLUCIONES:`, pageWidth - 45, finalY, { align: 'right' });
            doc.text(`-${formatMoney(totalDevueltoCalculated)}`, pageWidth - 14, finalY, { align: 'right' });
            doc.setTextColor(0);

            finalY += 4;
            doc.line(pageWidth - 60, finalY, pageWidth - 14, finalY);

            finalY += 6;
            const absoluteTotalFinal = totalOriginal - totalDevueltoCalculated;
            doc.setFont(undefined, 'bold');
            doc.text(`TOTAL FINAL: ${formatMoney(absoluteTotalFinal)}`, pageWidth - 14, finalY, { align: 'right' });

            // Note for Store Credit
            const totalSaldoAcred = totalDevueltoCalculated - refundCashDeduction;
            if (totalSaldoAcred > 0.01) {
                finalY += 10;
                doc.setFontSize(8);
                doc.setFont(undefined, 'normal');
                doc.setTextColor(80);
                doc.text(`* El saldo devuelto restante (${formatMoney(totalSaldoAcred)}) fue acreditado a Saldo a Favor.`, 14, finalY);
            }
        } else {
            doc.setFont(undefined, 'bold');
            doc.text(`TOTAL VENTA: ${formatMoney(saleData.total)}`, pageWidth - 14, finalY, { align: 'right' });
            if (saleData.globalDiscount > 0) {
                doc.setFontSize(10);
                doc.setFont(undefined, 'normal');
                finalY += 5;
                doc.text(`(Incluye descuento global de ${formatMoney(saleData.globalDiscount)})`, pageWidth - 14, finalY, { align: 'right' });
            }
            if (saleData.globalSurcharge > 0) {
                doc.setFontSize(10);
                doc.setFont(undefined, 'normal');
                finalY += 5;
                doc.text(`(Incluye recargo global de ${formatMoney(saleData.globalSurcharge)})`, pageWidth - 14, finalY, { align: 'right' });
            }
        }

        // Dynamic filename: Venta- [Client] - dd-mm-YYYY.pdf
        const clientName = saleData.client || 'Consumidor Final';
        const dateStr = formatDateDDMMYYYY(saleData.date).replace(/\//g, '-');
        doc.save(`Venta- ${clientName} - ${dateStr}.pdf`);
    } catch (error) {
        console.error("Error generating PDF:", error);
        alert("Error al generar el PDF. Revise la consola.");
    }
};


// --- DEBTOR RECEIPT PDF ---

/**
 * Generates a debtor receipt PDF (Comprobante de Deuda).
 *
 * @param {Object} debtorData
 * @param {number} debtorData.ventaId
 * @param {string} debtorData.clienteNombre
 * @param {string} debtorData.fechaDeuda
 * @param {string} debtorData.estado - PENDIENTE | PARCIAL | PAGADO
 * @param {number} debtorData.montoOriginal
 * @param {number} debtorData.montoDeuda - Current remaining debt
 * @param {string} debtorData.saleDate
 * @param {string} debtorData.user - Vendedor
 * @param {string} debtorData.saleType - Tipo de Venta
 * @param {Array}  debtorData.items
 * @param {Array}  debtorData.pagosDeuda - [{fechaPago, metodoPagoNombre, monto}]
 * @param {Array}  debtorData.salePayments - [{name, amount}]
 * @param {number} debtorData.globalDiscount
 */
export const generateDebtorReceipt = (debtorData, options = { printItems: true }) => {
    try {
        const doc = new jsPDF();
        const pageWidth = doc.internal.pageSize.width;

        // --- LOGO (drawn first so it renders as background layer) ---
        addLogoToDoc(doc);

        // --- HEADER ---
        doc.setFontSize(18);
        doc.setFont(undefined, 'bold');
        doc.text("PRESUPUESTO", pageWidth / 2, 15, { align: 'center' });

        doc.setFont(undefined, 'normal');
        doc.setFontSize(10);
        doc.text(`ID Venta: #${debtorData.ventaId}`, 14, 25);
        doc.text(`Fecha de Venta: ${formatDateDDMMYYYY(debtorData.saleDate)}`, 14, 30);

        // Print date (smaller, gray)
        doc.setFontSize(8);
        doc.setTextColor(120);
        doc.text(`Fecha de Impresión: ${formatDateDDMMYYYY(new Date().toISOString())}`, 14, 35);
        doc.setTextColor(0);
        doc.setFontSize(10);

        doc.text(`Cliente: ${debtorData.clienteNombre}`, 14, 42);
        doc.text(`Vendedor: ${debtorData.vendedor || debtorData.user || 'Sistema'}`, 14, 47);
        doc.text(`Tipo Venta: ${debtorData.saleType || 'ESTÁNDAR'}`, 14, 52);

        // Estado with color coding
        const estadoText = `Estado: ${debtorData.estado}`;
        if (debtorData.estado === 'PAGADO') {
            doc.setTextColor(0, 128, 0);
        } else if (debtorData.estado === 'PARCIAL') {
            doc.setTextColor(200, 120, 0);
        } else {
            doc.setTextColor(200, 0, 0);
        }
        doc.setFont(undefined, 'bold');
        doc.text(estadoText, 14, 57);
        doc.setFont(undefined, 'normal');
        doc.setTextColor(0);

        if (options.printItems) {
            const totalUnidades = debtorData.items.reduce((acc, item) => acc + (Number(item.quantity) || 0), 0);
            doc.setFont(undefined, 'bold');
            doc.text(`Total de Artículos: ${totalUnidades}`, 14, 62);
            doc.setFont(undefined, 'normal');
        } else {
            doc.setFontSize(12);
            doc.setFont(undefined, 'bold');
            doc.text("RESUMEN DE PAGOS", 14, 62);
            doc.setFont(undefined, 'normal');
            doc.lastAutoTable = { finalY: 62 };
        }

        if (options.printItems) {
            // --- TABLE 1: ITEMS ---
            const itemsBody = debtorData.items.map(item => {
                const unitPrice = item.unitPrice || 0;
                const discount = item.discount || 0;
                const finalUnit = Math.max(0, unitPrice - discount);
                const subtotal = finalUnit * item.quantity;

                return [
                    item.codigo || 'N/A',
                    item.descripcion,
                    item.quantity,
                    formatMoney(unitPrice),
                    discount > 0 ? `-${formatMoney(discount)}` : '-',
                    formatMoney(subtotal)
                ];
            });

            autoTable(doc, {
                startY: 67,
                head: [['Código', 'Descripción', 'Cant.', 'Precio', 'Desc.', 'Subtotal']],
                body: itemsBody,
                theme: 'grid',
                headStyles: { fillColor: [0, 0, 0], textColor: 255 },
                columnStyles: {
                    0: { cellWidth: 20 },
                    1: { cellWidth: 'auto' },
                    2: { cellWidth: 10, halign: 'center' },
                    3: { cellWidth: 22, halign: 'right' },
                    4: { cellWidth: 22, halign: 'right' },
                    5: { cellWidth: 28, halign: 'right' }
                },
                styles: { fontSize: 8, cellPadding: 1 },
            });

            // --- TABLE 2: DISCOUNTS SUMMARY (black header, black font) ---
            const discountRows = [];
            const hasReason = debtorData.items.some(item => item.discount > 0 && item.reason);

            debtorData.items.forEach(item => {
                const discount = item.discount || 0;
                if (discount > 0) {
                    if (hasReason) {
                        discountRows.push([
                            `${item.descripcion} (x${item.quantity})`,
                            item.reason || '-',
                            `-${formatMoney(discount * item.quantity)}`
                        ]);
                    } else {
                        discountRows.push([
                            `${item.descripcion} (x${item.quantity})`,
                            `-${formatMoney(discount * item.quantity)}`
                        ]);
                    }
                }
            });

            if (debtorData.globalDiscount > 0) {
                if (hasReason) {
                    discountRows.push([
                        'Descuento Global',
                        '-',
                        `-${formatMoney(debtorData.globalDiscount)}`
                    ]);
                } else {
                    discountRows.push([
                        'Descuento Global',
                        `-${formatMoney(debtorData.globalDiscount)}`
                    ]);
                }
            }

            if (debtorData.globalSurcharge > 0) {
                if (hasReason) {
                    discountRows.push([
                        'Recargo Global',
                        '-',
                        `${formatMoney(debtorData.globalSurcharge)}`
                    ]);
                } else {
                    discountRows.push([
                        'Recargo Global',
                        `${formatMoney(debtorData.globalSurcharge)}`
                    ]);
                }
            }

            if (discountRows.length > 0) {
                let discountY = doc.lastAutoTable.finalY + 10;
                const title = (debtorData.globalSurcharge > 0) ? 'Descuento / Recargo' : 'Descuento';

                autoTable(doc, {
                    startY: discountY,
                    head: hasReason ? [[title, 'Motivo', 'Monto']] : [[title, 'Monto']],
                    body: discountRows,
                    theme: 'grid',
                    headStyles: { fillColor: [0, 0, 0], textColor: 255 },
                    columnStyles: hasReason
                        ? { 0: { cellWidth: 'auto' }, 1: { cellWidth: 'auto' }, 2: { cellWidth: 40, halign: 'right' } }
                        : { 0: { cellWidth: 'auto' }, 1: { cellWidth: 40, halign: 'right' } },
                    styles: { fontSize: 8, cellPadding: 1 },
                });
            }
        } // End of options.printItems

        // --- TABLE 3: HISTORIAL DE PAGOS (Sale Payments + Debt Payments unified) ---
        let pagosY = doc.lastAutoTable.finalY + 10;

        // Merge original sale payments (using sale date) with debt payments (using fechaPago)
        const salePayments = (debtorData.salePayments || []).map(p => ({
            date: debtorData.saleDate || debtorData.fechaDeuda,
            method: p.name,
            amount: p.amount || 0,
            type: 'Venta',
            pagoId: p.pagoId
        }));

        const debtPayments = (debtorData.pagosDeuda || []).map(p => ({
            date: p.fechaPago,
            method: p.metodoPagoNombre || 'Desconocido',
            amount: p.monto || 0,
            type: 'Pago Deuda',
            pagoId: p.id // Note: For debt payments, the cheque matching might use a different ID, but we will check it.
        }));

        const allPayments = [...salePayments, ...debtPayments].sort((a, b) => {
            return new Date(a.date || 0) - new Date(b.date || 0);
        });

        const totalPagado = allPayments.reduce((sum, p) => sum + p.amount, 0);

        const hasCheques = debtorData.cheques && debtorData.cheques.length > 0;
        const hasDetalle = hasCheques || allPayments.some(p => p.method.toLowerCase().includes('cheque'));

        let availableCheques = debtorData.cheques ? [...debtorData.cheques] : [];

        if (allPayments.length > 0) {
            const pagosBody = allPayments.map(p => {
                let detalle = '-';
                if (availableCheques.length > 0) {
                    let matchedIdx = availableCheques.findIndex(c => c.pagoVentaId && c.pagoVentaId === p.pagoId);
                    if (matchedIdx === -1 && p.method.toLowerCase().includes('cheque')) {
                        matchedIdx = availableCheques.findIndex(c => c.monto === p.amount);
                    }
                    if (matchedIdx !== -1) {
                        const matchedCheque = availableCheques.splice(matchedIdx, 1)[0];
                        const isCobrado = matchedCheque.estado === 'COBRADO';
                        const dateLabel = isCobrado ? `Cobrado el ${formatDateDDMMYYYY(matchedCheque.fechaCobro)}` : `A cobrar el ${formatDateDDMMYYYY(matchedCheque.fechaCobro)}`;
                        detalle = `${matchedCheque.estado} (${dateLabel})`;
                    }
                }

                if (hasDetalle) {
                    return [
                        formatDateDDMMYYYY(p.date),
                        p.method,
                        detalle,
                        p.type,
                        formatMoney(p.amount)
                    ];
                } else {
                    return [
                        formatDateDDMMYYYY(p.date),
                        p.method,
                        p.type,
                        formatMoney(p.amount)
                    ];
                }
            });

            autoTable(doc, {
                startY: pagosY,
                head: hasDetalle
                    ? [['Fecha', 'Método de Pago', 'Detalle / Estado', 'Concepto', 'Monto']]
                    : [['Fecha', 'Método de Pago', 'Concepto', 'Monto']],
                body: pagosBody,
                theme: 'grid',
                headStyles: { fillColor: [0, 80, 160], textColor: 255 },
                columnStyles: hasDetalle ? {
                    0: { cellWidth: 20 },
                    1: { cellWidth: 'auto' },
                    2: { cellWidth: 'auto', fontStyle: 'italic' },
                    3: { cellWidth: 25, fontStyle: 'italic' },
                    4: { cellWidth: 25, halign: 'right' }
                } : {
                    0: { cellWidth: 30 },
                    1: { cellWidth: 'auto' },
                    2: { cellWidth: 30, fontStyle: 'italic' },
                    3: { cellWidth: 30, halign: 'right' }
                },
                styles: { fontSize: 8, cellPadding: 1 },
                foot: hasDetalle
                    ? [['', '', '', 'TOTAL PAGADO', formatMoney(totalPagado)]]
                    : [['', '', 'TOTAL PAGADO', formatMoney(totalPagado)]],
                footStyles: { fillColor: [220, 235, 250], textColor: [0, 0, 0], fontStyle: 'bold' },
            });
        } else {
            doc.setFontSize(9);
            doc.setTextColor(120);
            doc.text("No se han registrado pagos.", 14, pagosY);
            doc.setTextColor(0);
        }

        // --- DEBT SUMMARY BOX ---
        const summaryY = (allPayments.length > 0 ? doc.lastAutoTable.finalY : pagosY) + 12;
        const montoOriginal = debtorData.montoOriginal || 0;
        const montoDeuda = debtorData.montoDeuda || 0;

        // Draw a bordered box
        const boxX = 14;
        const boxW = pageWidth - 28;
        const boxH = 32;

        doc.setDrawColor(0);
        doc.setLineWidth(0.5);
        doc.rect(boxX, summaryY, boxW, boxH);

        // Box title
        doc.setFontSize(11);
        doc.setFont(undefined, 'bold');
        doc.text("RESUMEN DE DEUDA", boxX + 4, summaryY + 7);

        doc.setFontSize(10);
        doc.setFont(undefined, 'normal');
        doc.text(`Monto Original de Venta:`, boxX + 4, summaryY + 14);
        doc.text(formatMoney(montoOriginal), boxX + boxW - 4, summaryY + 14, { align: 'right' });

        let currentYOffset = 14;
        if (debtorData.globalDiscount > 0) {
            currentYOffset += 4;
            doc.setFontSize(8);
            doc.setTextColor(100);
            doc.text(`(Incluye desc. global de ${formatMoney(debtorData.globalDiscount)})`, boxX + 4, summaryY + currentYOffset);
            doc.setTextColor(0);
            doc.setFontSize(10);
        }
        if (debtorData.globalSurcharge > 0) {
            currentYOffset += 4;
            doc.setFontSize(8);
            doc.setTextColor(100);
            doc.text(`(Incluye recargo global de ${formatMoney(debtorData.globalSurcharge)})`, boxX + 4, summaryY + currentYOffset);
            doc.setTextColor(0);
            doc.setFontSize(10);
        }

        doc.setTextColor(0, 128, 0);
        doc.text(`Total Pagado:`, boxX + 4, summaryY + 21);
        doc.text(formatMoney(totalPagado), boxX + boxW - 4, summaryY + 21, { align: 'right' });

        doc.setFont(undefined, 'bold');
        doc.setTextColor(200, 0, 0);
        doc.setFontSize(12);
        doc.text(`DEUDA PENDIENTE:`, boxX + 4, summaryY + 28);
        doc.text(formatMoney(montoDeuda), boxX + boxW - 4, summaryY + 28, { align: 'right' });
        doc.setTextColor(0);

        // Save
        const clientName = debtorData.clienteNombre || 'Consumidor Final';
        const dateStr = formatDateDDMMYYYY(debtorData.fechaDeuda || debtorData.saleDate || new Date().toISOString()).replace(/\//g, '-');
        doc.save(`Presupuesto- ${clientName} - ${dateStr}.pdf`);
    } catch (error) {
        console.error("Error generating debtor PDF:", error);
        alert("Error al generar el PDF de deuda. Revise la consola.");
    }
};

// --- PENDING SALE (PEDIDO) RECEIPT PDF ---

export const generatePendingSaleReceipt = (pedidoData, options = { printItems: true }) => {
    try {
        const doc = new jsPDF();
        const pageWidth = doc.internal.pageSize.width;

        // --- LOGO (drawn first so it renders as background layer) ---
        addLogoToDoc(doc);

        // --- HEADER ---
        doc.setFontSize(18);
        doc.setFont(undefined, 'bold');
        doc.text("PRESUPUESTO", pageWidth / 2, 15, { align: 'center' });

        doc.setFont(undefined, 'normal');
        doc.setFontSize(10);
        doc.text(`ID Pedido: #${pedidoData.id}`, 14, 25);
        doc.text(`Fecha: ${formatDateDDMMYYYY(pedidoData.fechaCreacion)}`, 14, 30);

        // Print date
        doc.setFontSize(8);
        doc.setTextColor(120);
        doc.text(`Fecha de Impresión: ${formatDateDDMMYYYY(new Date().toISOString())}`, 14, 35);
        doc.setTextColor(0);
        doc.setFontSize(10);

        doc.text(`Cliente: ${pedidoData.clienteNombre}`, 14, 42);
        doc.text(`Vendedor: ${pedidoData.vendedor || pedidoData.user || 'Sistema'}`, 14, 47);

        // Estado
        doc.setFont(undefined, 'bold');
        doc.setTextColor(200, 120, 0); // Orange for PENDIENTE
        doc.text(`Estado: ${pedidoData.estado}`, 14, 52);
        doc.setFont(undefined, 'normal');
        doc.setTextColor(0);

        if (options.printItems) {
            const totalUnidades = pedidoData.items.reduce((acc, item) => acc + (Number(item.quantity) || 0), 0);
            doc.setFont(undefined, 'bold');
            doc.text(`Total de Artículos: ${totalUnidades}`, 14, 57);
            doc.setFont(undefined, 'normal');

            // --- TABLE 1: ITEMS ---
            const itemsBody = pedidoData.items.map(item => {
                const unitPrice = item.unitPrice || 0;
                const discount = item.discount || 0;
                const finalUnit = Math.max(0, unitPrice - discount);
                const subtotal = finalUnit * item.quantity;

                return [
                    item.codigo || 'N/A',
                    item.descripcion,
                    item.quantity,
                    formatMoney(unitPrice),
                    discount > 0 ? `-${formatMoney(discount)}` : '-',
                    formatMoney(subtotal)
                ];
            });

            autoTable(doc, {
                startY: 62,
                head: [['Código', 'Descripción', 'Cant.', 'Precio', 'Desc.', 'Subtotal']],
                body: itemsBody,
                theme: 'grid',
                headStyles: { fillColor: [0, 0, 0], textColor: 255 },
                columnStyles: {
                    0: { cellWidth: 20 },
                    1: { cellWidth: 'auto' },
                    2: { cellWidth: 10, halign: 'center' },
                    3: { cellWidth: 22, halign: 'right' },
                    4: { cellWidth: 22, halign: 'right' },
                    5: { cellWidth: 28, halign: 'right' }
                },
                styles: { fontSize: 8, cellPadding: 1 },
            });
        } else {
            doc.setFontSize(12);
            doc.setFont(undefined, 'bold');
            doc.text("RESUMEN DE PAGOS DEL PEDIDO", 14, 57);
            doc.setFont(undefined, 'normal');
            doc.lastAutoTable = { finalY: 57 };
        }

        // --- TABLE 2: DISCOUNTS SUMMARY ---
        const discountRows = [];
        const hasReason = pedidoData.items.some(item => item.discount > 0 && item.reason);

        pedidoData.items.forEach(item => {
            const discount = item.discount || 0;
            if (discount > 0) {
                if (hasReason) {
                    discountRows.push([
                        `${item.descripcion} (x${item.quantity})`,
                        item.reason || '-',
                        `-${formatMoney(discount * item.quantity)}`
                    ]);
                } else {
                    discountRows.push([
                        `${item.descripcion} (x${item.quantity})`,
                        `-${formatMoney(discount * item.quantity)}`
                    ]);
                }
            }
        });

        if (pedidoData.globalDiscount > 0) {
            if (hasReason) {
                discountRows.push([
                    'Descuento Global',
                    '-',
                    `-${formatMoney(pedidoData.globalDiscount)}`
                ]);
            } else {
                discountRows.push([
                    'Descuento Global',
                    `-${formatMoney(pedidoData.globalDiscount)}`
                ]);
            }
        }

        if (pedidoData.globalSurcharge > 0) {
            if (hasReason) {
                discountRows.push([
                    'Recargo Global',
                    '-',
                    `${formatMoney(pedidoData.globalSurcharge)}`
                ]);
            } else {
                discountRows.push([
                    'Recargo Global',
                    `${formatMoney(pedidoData.globalSurcharge)}`
                ]);
            }
        }

        if (discountRows.length > 0) {
            let discountY = doc.lastAutoTable.finalY + 10;
            const title = (pedidoData.globalSurcharge > 0) ? 'Descuento / Recargo' : 'Descuento';

            autoTable(doc, {
                startY: discountY,
                head: hasReason ? [[title, 'Motivo', 'Monto']] : [[title, 'Monto']],
                body: discountRows,
                theme: 'grid',
                headStyles: { fillColor: [0, 0, 0], textColor: 255 },
                columnStyles: hasReason
                    ? { 0: { cellWidth: 'auto' }, 1: { cellWidth: 'auto' }, 2: { cellWidth: 40, halign: 'right' } }
                    : { 0: { cellWidth: 'auto' }, 1: { cellWidth: 40, halign: 'right' } },
                styles: { fontSize: 8, cellPadding: 1 },
            });
        }

        // --- TABLE 3: HISTORIAL DE PAGOS (Señas) ---
        let pagosY = doc.lastAutoTable.finalY + 10;

        const regularPagos = (pedidoData.pagos || []).map(p => ({
            date: p.date,
            name: p.name,
            amount: p.amount,
            pagoId: p.pagoId,
            isCheque: false
        }));

        const chequePagos = (pedidoData.cheques || []).map(c => ({
            date: pedidoData.fechaCreacion || pedidoData.date, // Registration date (fallback to sale creation)
            name: c.metodoPagoNombre || 'Cheque',
            amount: c.monto,
            pagoId: c.pagoVentaId,
            isCheque: true,
            chequeInfo: c
        }));

        // Filter out cheques that are already linked to a regular pago
        const uncashedCheques = chequePagos.filter(c => {
            return !c.pagoId || !regularPagos.some(p => p.pagoId === c.pagoId);
        });

        const allPayments = [...regularPagos, ...uncashedCheques].sort((a, b) => {
            return new Date(a.date || 0) - new Date(b.date || 0);
        });

        const totalPagado = pedidoData.montoPagado || 0;

        const hasCheques = pedidoData.cheques && pedidoData.cheques.length > 0;
        const hasDetalle = hasCheques || allPayments.some(p => p.name.toLowerCase().includes('cheque') || p.isCheque);

        if (allPayments.length > 0) {
            let availableCheques = pedidoData.cheques ? [...pedidoData.cheques] : [];
            const pagosBody = allPayments.map(p => {
                let detalle = '-';
                if (availableCheques.length > 0) {
                    let matchedCheque = null;
                    if (p.isCheque) {
                        matchedCheque = p.chequeInfo;
                        // Remove it from available to keep things consistent, if it's there
                        const idx = availableCheques.findIndex(c => c.id === p.chequeInfo.id);
                        if (idx !== -1) availableCheques.splice(idx, 1);
                    } else {
                        let matchedIdx = availableCheques.findIndex(c => c.pagoVentaId && c.pagoVentaId === p.pagoId);
                        if (matchedIdx === -1 && p.name.toLowerCase().includes('cheque')) {
                            matchedIdx = availableCheques.findIndex(c => c.monto === p.amount);
                        }
                        if (matchedIdx !== -1) {
                            matchedCheque = availableCheques.splice(matchedIdx, 1)[0];
                        }
                    }

                    if (matchedCheque) {
                        const isCobrado = matchedCheque.estado === 'COBRADO';
                        const dateLabel = isCobrado ? `Cobrado el ${formatDateDDMMYYYY(matchedCheque.fechaCobro)}` : `A cobrar el ${formatDateDDMMYYYY(matchedCheque.fechaCobro)}`;
                        detalle = `${matchedCheque.estado} (${dateLabel})`;
                    }
                }

                if (hasDetalle) {
                    return [
                        formatDateDDMMYYYY(p.date),
                        p.name,
                        detalle,
                        'Seña / Anticipo',
                        formatMoney(p.amount)
                    ];
                } else {
                    return [
                        formatDateDDMMYYYY(p.date),
                        p.name,
                        'Seña / Anticipo',
                        formatMoney(p.amount)
                    ];
                }
            });

            autoTable(doc, {
                startY: pagosY,
                head: hasDetalle
                    ? [['Fecha', 'Método de Pago', 'Detalle / Estado', 'Concepto', 'Monto']]
                    : [['Fecha', 'Método de Pago', 'Concepto', 'Monto']],
                body: pagosBody,
                theme: 'grid',
                headStyles: { fillColor: [0, 80, 160], textColor: 255 },
                columnStyles: hasDetalle ? {
                    0: { cellWidth: 20 },
                    1: { cellWidth: 'auto' },
                    2: { cellWidth: 'auto', fontStyle: 'italic' },
                    3: { cellWidth: 25, fontStyle: 'italic' },
                    4: { cellWidth: 30, halign: 'right' }
                } : {
                    0: { cellWidth: 20 },
                    1: { cellWidth: 'auto' },
                    2: { cellWidth: 30, fontStyle: 'italic' },
                    3: { cellWidth: 30, halign: 'right' }
                },
                styles: { fontSize: 8, cellPadding: 1 },
                foot: hasDetalle
                    ? [['', '', '', 'TOTAL PAGADO', formatMoney(totalPagado)]]
                    : [['', '', 'TOTAL PAGADO', formatMoney(totalPagado)]],
                footStyles: { fillColor: [220, 235, 250], textColor: [0, 0, 0], fontStyle: 'bold' },
            });
        } else {
            doc.setFontSize(9);
            doc.setTextColor(120);
            doc.text("No se han registrado pagos anticipados.", 14, pagosY);
            doc.setTextColor(0);
        }

        // --- DEBT SUMMARY BOX ---
        const summaryY = (allPayments.length > 0 ? doc.lastAutoTable.finalY : pagosY) + 12;
        const montoTotal = pedidoData.montoTotal || 0;
        const saldoRestante = pedidoData.saldoRestante || 0;

        const boxX = 14;
        const boxW = pageWidth - 28;
        const boxH = 32;

        doc.setDrawColor(0);
        doc.setLineWidth(0.5);
        doc.rect(boxX, summaryY, boxW, boxH);

        doc.setFontSize(11);
        doc.setFont(undefined, 'bold');
        doc.text("RESUMEN DEL PEDIDO", boxX + 4, summaryY + 7);

        doc.setFontSize(10);
        doc.setFont(undefined, 'normal');
        doc.text(`Valor Total del Pedido:`, boxX + 4, summaryY + 14);
        doc.text(formatMoney(montoTotal), boxX + boxW - 4, summaryY + 14, { align: 'right' });

        let currentYOffsetPedido = 14;
        if (pedidoData.descuentoGlobal > 0) {
            currentYOffsetPedido += 4;
            doc.setFontSize(8);
            doc.setTextColor(100);
            doc.text(`(Incluye desc. global de ${formatMoney(pedidoData.descuentoGlobal)})`, boxX + 4, summaryY + currentYOffsetPedido);
            doc.setTextColor(0);
            doc.setFontSize(10);
        }
        if (pedidoData.recargoGlobal > 0) {
            currentYOffsetPedido += 4;
            doc.setFontSize(8);
            doc.setTextColor(100);
            doc.text(`(Incluye recargo global de ${formatMoney(pedidoData.recargoGlobal)})`, boxX + 4, summaryY + currentYOffsetPedido);
            doc.setTextColor(0);
            doc.setFontSize(10);
        }

        doc.setTextColor(0, 128, 0);
        doc.text(`Total Anticipado (Seña):`, boxX + 4, summaryY + 21);
        doc.text(formatMoney(totalPagado), boxX + boxW - 4, summaryY + 21, { align: 'right' });

        doc.setFont(undefined, 'bold');
        doc.setTextColor(200, 0, 0);
        doc.setFontSize(12);
        doc.text(`SALDO A PAGAR:`, boxX + 4, summaryY + 28);
        doc.text(formatMoney(saldoRestante), boxX + boxW - 4, summaryY + 28, { align: 'right' });
        doc.setTextColor(0);

        const clientName = pedidoData.clienteNombre || 'Consumidor Final';
        const dateStr = formatDateDDMMYYYY(pedidoData.fechaCreacion || new Date().toISOString()).replace(/\//g, '-');
        doc.save(`Presupuesto - ${clientName} - ${dateStr}.pdf`);
    } catch (error) {
        console.error("Error generating pedido PDF:", error);
        alert("Error al generar el PDF de pedido. Revise la consola.");
    }
};
