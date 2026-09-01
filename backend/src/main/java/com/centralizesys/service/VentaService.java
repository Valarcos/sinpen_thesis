package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.dto.PageResponse;
import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.product.StockLocation;
import com.centralizesys.model.returns.DevolucionRequest;
import com.centralizesys.model.sales.*;
import com.centralizesys.repository.ClienteRepository;
import com.centralizesys.repository.DevolucionesRepository;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.StockRepository;
import com.centralizesys.repository.VentaRepository;
import com.centralizesys.repository.AlertaChequeRepository;
import com.centralizesys.model.cheque.AlertaCheque;
import com.centralizesys.model.cheque.AlertaChequeRequest;
import com.centralizesys.util.Constants;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final DeudoresRepository deudoresRepository;
    private final AuditoriaService auditoriaService;
    private final AlertaChequeRepository alertaChequeRepository;
    private final com.centralizesys.repository.MetodoPagoRepository metodoPagoRepository;
    private final ClienteRepository clienteRepository;
    private final DevolucionesRepository devolucionesRepository;

    private static final String ANULADA = "ANULADA";
    private static final String PENDIENTE = "PENDIENTE";
    private static final String ACTIVA = "ACTIVA";
    private static final String MINORISTA = "MINORISTA";
    private static final String VENTA_PENDIENTE = "Venta Pendiente";
    private static final double PAYMENT_COMPLETE_EPSILON = 0.01;
    private static final String COBRADO = "COBRADO";
    private static final String ALERTA_CHEQUE = "AlertaCheque";
    private static final String SALDO_ACRONIMO = "SALDO";

    public VentaService(VentaRepository ventaRepository,
                        ProductRepository productRepository,
                        StockRepository stockRepository,
                        DeudoresRepository deudoresRepository,
                        AuditoriaService auditoriaService,
                        AlertaChequeRepository alertaChequeRepository,
                        com.centralizesys.repository.MetodoPagoRepository metodoPagoRepository,
                        ClienteRepository clienteRepository,
                        DevolucionesRepository devolucionesRepository) {
        this.ventaRepository       = ventaRepository;
        this.productRepository     = productRepository;
        this.stockRepository       = stockRepository;
        this.deudoresRepository    = deudoresRepository;
        this.auditoriaService      = auditoriaService;
        this.alertaChequeRepository = alertaChequeRepository;
        this.metodoPagoRepository  = metodoPagoRepository;
        this.clienteRepository     = clienteRepository;
        this.devolucionesRepository = devolucionesRepository;
    }

    public PageResponse<Venta> getVentasPage(String startDate, String endDate, Long searchId, int page, int size) {
        LocalDateTime end = (endDate == null || endDate.isBlank()) ? LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")) : LocalDate.parse(endDate).atTime(23, 59, 59, 999999999);
        LocalDateTime start = (startDate == null || startDate.isBlank()) ? end.minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0) : LocalDate.parse(startDate).atStartOfDay();

        if (searchId == null) {
            long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(start.atZone(ZoneId.of("America/Argentina/Buenos_Aires")), end.atZone(ZoneId.of("America/Argentina/Buenos_Aires")));
            if (daysDiff < 0) throw new BusinessRuleException("La fecha de inicio no puede ser posterior a la fecha de fin.");
            if (daysDiff > 60) throw new BusinessRuleException("El rango de fechas no puede exceder los 60 días.");
        }

        size = Math.min(size, 100);
        int offset = page * size;
        List<Venta> ventas = ventaRepository.findVentasByFechaBetween(start, end, searchId, size, offset);
        long totalElements = ventaRepository.countVentasByFechaBetween(start, end, searchId);
        long totalPages = (long) Math.ceil((double) totalElements / size);

        return new PageResponse<>(ventas, (long) page, (long) size, totalElements, totalPages);
    }

    public PageResponse<Venta> getVentasPendientesPage(String startDate, String endDate, int page, int size) {
        size = Math.min(size, 100);
        LocalDateTime end = (endDate == null || endDate.isBlank()) ? LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")) : LocalDate.parse(endDate).atTime(23, 59, 59, 999999999);
        LocalDateTime start = (startDate == null || startDate.isBlank()) ? end.minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0) : LocalDate.parse(startDate).atStartOfDay();

        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(start.atZone(ZoneId.of("America/Argentina/Buenos_Aires")), end.atZone(ZoneId.of("America/Argentina/Buenos_Aires")));
        if (daysDiff < 0) throw new BusinessRuleException("La fecha de inicio no puede ser posterior a la fecha de fin.");
        if (daysDiff > 60) throw new BusinessRuleException("El rango de fechas no puede exceder los 60 días.");

        int offset = page * size;
        List<Venta> ventas = ventaRepository.findVentasPendientesByFechaBetween(start, end, size, offset);
        long totalElements = ventaRepository.countVentasPendientesByFechaBetween(start, end);
        long totalPages = (long) Math.ceil((double) totalElements / size);

        return new PageResponse<>(ventas, (long) page, (long) size, totalElements, totalPages);
    }

    public List<String> getClientes() {
        return ventaRepository.findDistinctClientNames();
    }

    public VentaResponse getVentaById(Long id) {
        Venta venta = ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Venta", id));
        List<DetalleVenta> detalles = ventaRepository.findDetallesByVentaId(id);

        // Populate devoluciones para que el UI pueda calcular netRemaining
        detalles.forEach(d -> {
            Long yaDevuelta = devolucionesRepository.sumCantidadDevueltaByDetalleId(d.getId());
            d.setCantidadDevuelta(yaDevuelta);
        });

        List<PagoVenta> pagos = ventaRepository.findPagosActivosByVentaId(id);
        String vendedorNombre = ventaRepository.findVendedorNombre(venta.getUsuarioId());

        return new VentaResponse(
                venta.getId(),
                venta.getFecha(),
                venta.getClienteNombre(),
                venta.getClienteId(),
                vendedorNombre,
                venta.getTotalVenta(),
                venta.getDescuentoGlobal(),
                venta.getRecargoGlobal(),
                venta.getTipoVenta(),
                detalles,
                pagos,
                null,
                venta.getEstado(),
                venta.getCostoTotal(),
                venta.getVersion()
        );
    }

    @Transactional
    public VentaResponse registrarVenta(VentaRequest request) {
        validateRequest(request);
        resolveClientForSale(request, request.getUsuarioId());
        ProcessedSaleResult processedData = processItems(request.getItems(), request.getTipoVenta());
        Double subtotal = processedData.getTotalVenta();
        Double descuentoGlobal = request.getDescuentoGlobal();
        // Defense-in-Depth: guard against NaN/Infinity injected via raw HTTP payloads
        if (Double.isNaN(descuentoGlobal) || Double.isInfinite(descuentoGlobal)) throw new BusinessRuleException("Valor de descuento inválido.");
        if (descuentoGlobal < 0) throw new BusinessRuleException("El descuento global no puede ser negativo.");
        if (descuentoGlobal > subtotal) throw new BusinessRuleException("El descuento global no puede ser mayor al subtotal.");

        Double recargoGlobal = request.getRecargoGlobal();
        // Defense-in-Depth: guard against NaN/Infinity injected via raw HTTP payloads
        if (Double.isNaN(recargoGlobal) || Double.isInfinite(recargoGlobal)) throw new BusinessRuleException("Valor de recargo inválido.");
        if (recargoGlobal < 0) throw new BusinessRuleException("El recargo global no puede ser negativo.");

        // Formula: Total = Subtotal - Descuento + Recargo
        Double finalTotal = Math.round((subtotal - descuentoGlobal + recargoGlobal) * 100.0) / 100.0;
        processedData.setTotalVenta(finalTotal);
        processedData.setDescuentoGlobal(descuentoGlobal);
        processedData.setRecargoGlobal(recargoGlobal);

        double pagosTotal = request.getPagos() != null ? request.getPagos().stream().mapToDouble(VentaRequest.PagoRequest::getMonto).sum() : 0.0;
        double chequesTotal = request.getCheques() != null ? request.getCheques().stream().mapToDouble(com.centralizesys.model.cheque.AlertaChequeRequest::getMonto).sum() : 0.0;
        double totalAbonadoRounded = Math.round((pagosTotal + chequesTotal) * 100.0) / 100.0;

        Double saldoGenerado = request.getSaldoGenerado();
        if (totalAbonadoRounded > finalTotal + saldoGenerado + 0.01) {
            throw new BusinessRuleException(String.format("La suma de los pagos y cheques ($%.2f) no puede superar el total de la venta más el saldo generado ($%.2f).", totalAbonadoRounded, finalTotal + saldoGenerado));
        }

        PersistedTransactionInfo txInfo = saveTransactionData(request, processedData, saldoGenerado);
        List<String> stockAlerts = updateStockFromDetails(processedData.getDetalles());

        // Atomically inject Saldo a Favor if client intended it
        if (saldoGenerado > 0 && request.getClienteId() != null) {
            clienteRepository.addSaldo(request.getClienteId(), saldoGenerado);
        }

        // Process any cheques submitted with the standard sale so they aren't lost
        processChequesPendientes(txInfo.getVentaId(), request);

        // Pass chequesTotal to handleDebt to prevent the cheque amount from being falsely flagged as FIADO
        handleDebt(txInfo.getVentaId(), request.getClienteNombre(), request.getClienteId(), processedData.getTotalVenta(), txInfo.getPagosPersistidos(), chequesTotal);

        auditoriaService.registrarAccion(request.getUsuarioId(), "VENTA", "Venta ID " + txInfo.getVentaId() + " a " + request.getClienteNombre() + ". Total: $" + processedData.getTotalVenta() + " (Desc: " + descuentoGlobal + ", Rec: " + recargoGlobal + ")");
        String vendedorNombre = ventaRepository.findVendedorNombre(request.getUsuarioId());

        return new VentaResponse(
                txInfo.getVentaId(),
                LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")),
                request.getClienteNombre(),
                request.getClienteId(),
                vendedorNombre,
                processedData.getTotalVenta(),
                descuentoGlobal,
                recargoGlobal,
                request.getTipoVenta() != null ? request.getTipoVenta().name() : MINORISTA,
                processedData.getDetalles(),
                txInfo.getPagosPersistidos(),
                stockAlerts,
                ACTIVA,
                processedData.getDetalles().stream().mapToDouble(d -> d.getCostoSnapshot() * d.getCantidad()).sum(),
                0
        );
    }

    @Transactional
    public void cobrarCheque(Long chequeId, Long metodoPagoId, Long authenticatedUserId) {
        AlertaCheque cheque = alertaChequeRepository.findById(chequeId)
                .orElseThrow(() -> new ResourceNotFoundException(ALERTA_CHEQUE, chequeId));

        if (!PENDIENTE.equals(cheque.getEstado())) {
            throw new BusinessRuleException("El cheque ya fue cobrado o anulado.");
        }

        com.centralizesys.model.sales.MetodoPago metodo = metodoPagoRepository.findById(metodoPagoId)
                .orElseThrow(() -> new ResourceNotFoundException("MetodoPago", metodoPagoId));
        if (!metodo.isActivo()) {
            throw new BusinessRuleException("El método de pago seleccionado no está activo.");
        }

        if ("DEUDA_FIADO".equals(cheque.getTipoOrigen())) {
            com.centralizesys.model.debt.DeudaResponse deuda = deudoresRepository.findByVentaId(cheque.getVentaId())
                    .orElseThrow(() -> new BusinessRuleException("No se encontró la deuda asociada al cheque."));

            if (cheque.getMonto() > deuda.getMontoDeuda() + PAYMENT_COMPLETE_EPSILON) {
                throw new BusinessRuleException(String.format("El monto del cheque ($%.2f) supera la deuda pendiente ($%.2f).", cheque.getMonto(), deuda.getMontoDeuda()));
            }

            Long pagoDeudaId = deudoresRepository.insertarPagoDeudaReturningId(
                    deuda.getId(), metodoPagoId, cheque.getMonto(),
                    "Cobro de cheque ID " + chequeId, authenticatedUserId);

            deudoresRepository.deductDeudaAtomic(deuda.getId(), cheque.getMonto(), deuda.getMontoOriginal());
            int rows = alertaChequeRepository.updateEstadoAndPagoDeudaIdAtomic(chequeId, COBRADO, pagoDeudaId, PENDIENTE);
            if (rows == 0) throw new BusinessRuleException("El cheque ya fue cobrado o anulado concurrentemente.");

            auditoriaService.registrarAccion(authenticatedUserId, "COBRO_CHEQUE_DEUDA",
                    "Cheque ID " + chequeId + " cobrado por $" + cheque.getMonto() + " para deuda ID " + deuda.getId());
        } else {
            Long pagoVentaId = ventaRepository.savePagoUnicoReturningId(cheque.getVentaId(), metodoPagoId, cheque.getMonto(), authenticatedUserId);
            int rows = alertaChequeRepository.updateEstadoAndPagoVentaIdAtomic(chequeId, COBRADO, pagoVentaId, PENDIENTE);
            if (rows == 0) throw new BusinessRuleException("El cheque ya fue cobrado o anulado concurrentemente.");

            auditoriaService.registrarAccion(authenticatedUserId, "COBRO_CHEQUE",
                    "Cheque ID " + chequeId + " cobrado por $" + cheque.getMonto() + " (Pago ID: " + pagoVentaId + ")");
        }
    }

    @Transactional
    public void cancelarCobroCheque(Long chequeId, Long authenticatedUserId) {
        internalCancelarCobroCheque(chequeId, authenticatedUserId);
    }

    private void internalCancelarCobroCheque(Long chequeId, Long authenticatedUserId) {
        AlertaCheque cheque = alertaChequeRepository.findById(chequeId)
                .orElseThrow(() -> new ResourceNotFoundException(ALERTA_CHEQUE, chequeId));

        if (!COBRADO.equals(cheque.getEstado())) {
            throw new BusinessRuleException("El cheque no está cobrado.");
        }

        if ("DEUDA_FIADO".equals(cheque.getTipoOrigen())) {
            if (cheque.getPagoDeudaId() == null) {
                throw new BusinessRuleException("El cheque de deuda no tiene un pago asociado.");
            }
            com.centralizesys.model.debt.DeudaResponse deuda = deudoresRepository.findByVentaId(cheque.getVentaId())
                    .orElseThrow(() -> new BusinessRuleException("No se encontró la deuda asociada al cheque."));

            deudoresRepository.updatePagoAnulado(cheque.getPagoDeudaId());

            deudoresRepository.addDeudaAtomic(deuda.getId(), cheque.getMonto(), deuda.getMontoOriginal());

            int rows = alertaChequeRepository.updateEstadoAndPagoDeudaIdAtomic(chequeId, PENDIENTE, null, COBRADO);
            if (rows == 0) throw new BusinessRuleException("El cheque fue modificado concurrentemente.");

            auditoriaService.registrarAccion(authenticatedUserId, "CANCELACION_COBRO_CHEQUE_DEUDA", "Cheque ID: " + chequeId);
        } else {
            if (cheque.getPagoVentaId() == null) {
                throw new BusinessRuleException("El cheque no tiene un pago asociado.");
            }
            ventaRepository.anularPagoVentaById(cheque.getPagoVentaId());

            int rows = alertaChequeRepository.updateEstadoAndPagoVentaIdAtomic(chequeId, PENDIENTE, null, COBRADO);
            if (rows == 0) throw new BusinessRuleException("El cheque fue modificado concurrentemente.");

            auditoriaService.registrarAccion(authenticatedUserId, "CANCELACION_COBRO_CHEQUE", "Cheque ID: " + chequeId);
        }
    }

    // TODO: Add backend tests for this logical deletion logic (anularCheque)
    @Transactional
    public void anularCheque(Long chequeId, Long authenticatedUserId) {
        AlertaCheque cheque = alertaChequeRepository.findById(chequeId)
                .orElseThrow(() -> new ResourceNotFoundException(ALERTA_CHEQUE, chequeId));

        if (ANULADA.equals(cheque.getEstado())) {
            throw new BusinessRuleException("El cheque ya se encuentra anulado.");
        }

        // Si el cheque ya fue cobrado, deshacemos el pago antes de anularlo completamente
        if (COBRADO.equals(cheque.getEstado())) {
            internalCancelarCobroCheque(chequeId, authenticatedUserId);
        }

        // Logical deletion: marcar como ANULADA
        int rows = alertaChequeRepository.updateEstadoAndPagoVentaIdAtomic(chequeId, ANULADA, null, PENDIENTE);
        if (rows == 0) throw new BusinessRuleException("El cheque fue modificado concurrentemente o no estaba pendiente.");

        auditoriaService.registrarAccion(authenticatedUserId, "ANULAR_CHEQUE",
                "Cheque ID " + chequeId + " anulado de la venta (eliminación lógica).");
    }

    @Transactional
    public Long crearPendiente(VentaRequest request, Long authenticatedUserId) {
        validateRequest(request);
        resolveClientForSale(request, authenticatedUserId);

        if ((request.getClienteId() == null || request.getClienteId().equals(999L)) &&
                (request.getClienteNombre() == null || request.getClienteNombre().trim().isEmpty() || request.getClienteNombre().trim().equalsIgnoreCase("Consumidor Final"))) {
            throw new BusinessRuleException("Regla de Negocio: Un pedido pendiente no puede ser anónimo. Debe asignarse a un cliente registrado.");
        }
        ProcessedSaleResult processedData = processItems(request.getItems(), request.getTipoVenta());

        Double subtotal = processedData.getTotalVenta();
        Double descuentoGlobal = request.getDescuentoGlobal();
        // Defense-in-Depth: guard against NaN/Infinity injected via raw HTTP payloads
        if (Double.isNaN(descuentoGlobal) || Double.isInfinite(descuentoGlobal)) throw new BusinessRuleException("Valor de descuento inválido.");
        if (descuentoGlobal < 0 || descuentoGlobal > subtotal) throw new BusinessRuleException("Descuento global inválido.");

        Double recargoGlobal = request.getRecargoGlobal();
        // Defense-in-Depth: guard against NaN/Infinity injected via raw HTTP payloads
        if (Double.isNaN(recargoGlobal) || Double.isInfinite(recargoGlobal)) throw new BusinessRuleException("Valor de recargo inválido.");
        if (recargoGlobal < 0) throw new BusinessRuleException("El recargo global no puede ser negativo.");

        // Formula: Total = Subtotal - Descuento + Recargo
        Double finalTotal = Math.round((subtotal - descuentoGlobal + recargoGlobal) * 100.0) / 100.0;

        Long pendingId = persistVentaPendienteBase(request, finalTotal, descuentoGlobal, recargoGlobal, authenticatedUserId);

        List<DetalleVenta> detalles = processedData.getDetalles();
        persistDetalles(pendingId, detalles);
        updateStockFromDetails(detalles);

        processPagosPendientes(pendingId, request, authenticatedUserId);
        processChequesPendientes(pendingId, request);

        auditoriaService.registrarAccion(authenticatedUserId, "CREAR_PENDIENTE", "Pedido creado con ID: " + pendingId);
        return pendingId;
    }

    private Long persistVentaPendienteBase(VentaRequest request, Double finalTotal, Double descuentoGlobal, Double recargoGlobal, Long authenticatedUserId) {
        Venta pendingSale = new Venta();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        pendingSale.setFecha(now);
        pendingSale.setFechaCreacion(now);
        pendingSale.setClienteNombre(request.getClienteNombre());
        pendingSale.setClienteId(request.getClienteId());
        pendingSale.setTotalVenta(finalTotal);
        pendingSale.setDescuentoGlobal(descuentoGlobal);
        pendingSale.setRecargoGlobal(recargoGlobal);
        pendingSale.setTipoVenta(request.getTipoVenta() != null ? request.getTipoVenta().name() : MINORISTA);
        pendingSale.setUsuarioId(authenticatedUserId);
        pendingSale.setEstado(PENDIENTE);
        return ventaRepository.saveVenta(pendingSale);
    }

    private void processPagosPendientes(Long pendingId, VentaRequest request, Long authenticatedUserId) {
        if (request.getPagos() == null || request.getPagos().isEmpty()) return;
        MetodoPago saldoMethod = metodoPagoRepository.findByAcronimo(SALDO_ACRONIMO)
                .orElseThrow(() -> new BusinessRuleException("Método SALDO no configurado."));
        Long saldoId = saldoMethod.getId();
        for (VentaRequest.PagoRequest pvr : request.getPagos()) {
            MetodoPago metodo = metodoPagoRepository.findById(pvr.getMetodoPagoId())
                    .orElseThrow(() -> new BusinessRuleException("Método de pago no encontrado."));
            if (!metodo.isActivo()) throw new BusinessRuleException("El método de pago seleccionado se encuentra inactivo.");

            if (saldoId != null && saldoId.equals(pvr.getMetodoPagoId())) {
                if (request.getClienteId() == null) throw new BusinessRuleException("El pago con Saldo a Favor requiere un cliente seleccionado.");
                int rows = clienteRepository.deductSaldo(request.getClienteId(), pvr.getMonto());
                if (rows == 0) throw new BusinessRuleException("Saldo a favor insuficiente para el cliente.");
            }
            ventaRepository.savePagoUnico(pendingId, pvr.getMetodoPagoId(), pvr.getMonto(), authenticatedUserId);
        }
    }

    private void processChequesPendientes(Long pendingId, VentaRequest request) {
        if (request.getCheques() == null || request.getCheques().isEmpty()) return;
        for (AlertaChequeRequest chequeReq : request.getCheques()) {
            AlertaCheque cheque = new AlertaCheque(null, pendingId, chequeReq.getMonto(), chequeReq.getFechaCobro(), PENDIENTE, null, null);
            alertaChequeRepository.save(cheque);
        }
    }

    @Transactional
    public void registrarPago(Long id, List<PagoDeudaRequest> pagos, Long usuarioId) {
        double totalNuevoPago = validatePagosPendientes(pagos);
        Venta pendingSale = validateEstadoPendiente(id);

        // PESSIMISTIC LOCK: Lock the sale row to serialize concurrent payment registrations.
        // This forces other threads to wait until this transaction commits,
        // ensuring they read the newly inserted payments in READ COMMITTED isolation.
        // Replaced MVCC bloat Dummy Update with explicit SELECT FOR UPDATE.
        boolean isLocked = ventaRepository.lockVentaForUpdate(id, PENDIENTE);
        if (!isLocked) throw new BusinessRuleException("El pedido no se encuentra pendiente o fue modificado concurrentemente.");

        processPagoPendienteRecords(id, pagos, pendingSale, usuarioId);

        auditoriaService.registrarAccion(usuarioId, "PAGO_PENDIENTE", String.format("Registrado pago/cheque de $%.2f en Pedido ID %d.", totalNuevoPago, id));
    }

    private double validatePagosPendientes(List<PagoDeudaRequest> pagos) {
        if (pagos == null || pagos.isEmpty()) throw new BusinessRuleException(Constants.ERR_PAYMENT_NEGATIVE);
        double totalNuevoPago = pagos.stream().mapToDouble(PagoDeudaRequest::getMontoPago).sum();
        if (totalNuevoPago <= 0) throw new BusinessRuleException(Constants.ERR_PAYMENT_NEGATIVE);
        return totalNuevoPago;
    }

    private Venta validateEstadoPendiente(Long id) {
        Venta pendingSale = ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(VENTA_PENDIENTE, id));
        if (!PENDIENTE.equals(pendingSale.getEstado())) throw new BusinessRuleException("Solo se pueden registrar pagos en pedidos con estado PENDIENTE.");
        return pendingSale;
    }

    private void processPagoPendienteRecords(Long id, List<PagoDeudaRequest> pagos, Venta pendingSale, Long usuarioId) {
        MetodoPago saldoMethod = metodoPagoRepository.findByAcronimo(SALDO_ACRONIMO)
                .orElseThrow(() -> new BusinessRuleException("Método SALDO no configurado."));
        Long saldoId = saldoMethod.getId();
        for (PagoDeudaRequest pago : pagos) {
            if (pago.getMontoPago() != null && pago.getMontoPago() > 0) {
                processSinglePagoPendienteRecord(id, pago, pendingSale, saldoId, usuarioId);
            }
        }
    }

    private void processSinglePagoPendienteRecord(Long id, PagoDeudaRequest pago, Venta pendingSale, Long saldoId, Long usuarioId) {
        MetodoPago metodo = metodoPagoRepository.findById(pago.getMetodoPagoId())
                .orElseThrow(() -> new BusinessRuleException("Método de pago no encontrado."));
        if (!metodo.isActivo()) {
            throw new BusinessRuleException("El método de pago '" + metodo.getDescripcion() + "' se encuentra desactivado.");
        }

        if (pago.getFechaCobro() != null) {
            AlertaCheque cheque = new AlertaCheque(null, id, pago.getMontoPago(), pago.getFechaCobro(), PENDIENTE, null, null);
            alertaChequeRepository.save(cheque);
        } else {
            if (saldoId != null && saldoId.equals(pago.getMetodoPagoId())) {
                if (pendingSale.getClienteId() == null) throw new BusinessRuleException("El pago con Saldo a Favor requiere un cliente seleccionado.");
                int rows = clienteRepository.deductSaldo(pendingSale.getClienteId(), pago.getMontoPago());
                if (rows == 0) throw new BusinessRuleException("Saldo a favor insuficiente para el cliente.");
            }
            ventaRepository.savePagoUnico(id, pago.getMetodoPagoId(), pago.getMontoPago(), usuarioId);
        }
    }

    @Transactional
    public VentaResponse modificarCarrito(Long id, VentaRequest request, Long usuarioId) {
        validateRequest(request);
        resolveClientForSale(request, usuarioId);
        // PESSIMISTIC LOCK: Serialize modifications cleanly without MVCC bloat.
        boolean isLocked = ventaRepository.lockVentaForUpdate(id, PENDIENTE);
        if (!isLocked) throw new BusinessRuleException("El pedido está siendo modificado o ya no está pendiente.");

        // READ LATEST COMMITTED STATE (after acquiring the lock)
        Venta pendingSale = ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(VENTA_PENDIENTE, id));
        if (!PENDIENTE.equals(pendingSale.getEstado())) throw new BusinessRuleException("Solo se puede modificar un pedido en estado PENDIENTE.");

        // OPTIMISTIC LOCK: Prevent Last-Writer-Wins data loss
        if (request.getVersion() != null && !request.getVersion().equals(pendingSale.getVersion())) {
            throw new BusinessRuleException("El carrito fue modificado por otro usuario mientras lo editabas. Por favor, recarga la página.");
        }

        // Resolve Client Identity properly for partial updates vs explicit clearing
        if (request.getClienteId() == null && request.getClienteNombre() == null) {
            // Partial update: preserve existing client
            request.setClienteId(pendingSale.getClienteId());
            request.setClienteNombre(pendingSale.getClienteNombre());
        } else {
            // Explicit client change
            resolveClientForSale(request, usuarioId);

            if ((request.getClienteId() == null || request.getClienteId().equals(999L)) &&
                    (request.getClienteNombre() == null || request.getClienteNombre().trim().isEmpty() || request.getClienteNombre().trim().equalsIgnoreCase("Consumidor Final"))) {
                throw new BusinessRuleException("Regla de Negocio: Un pedido pendiente no puede ser anónimo. Debe asignarse a un cliente registrado.");
            }
        }

        Long resolvedClienteId = request.getClienteId();
        String resolvedClienteNombre = request.getClienteNombre();

        // ANTI-THEFT LEDGER GUARD: Prevent stealing Saldo a Favor via identity swap
        if (!java.util.Objects.equals(pendingSale.getClienteId(), resolvedClienteId)) {
            MetodoPago saldoMethod = metodoPagoRepository.findByAcronimo(SALDO_ACRONIMO).orElse(null);
            if (saldoMethod != null) {
                boolean hasSaldoPayments = ventaRepository.findPagosActivosByVentaId(id).stream()
                        .anyMatch(p -> p.getMetodoPagoId().equals(saldoMethod.getId()));
                if (hasSaldoPayments) {
                    throw new BusinessRuleException("Integridad de Datos: No se puede cambiar el cliente de un pedido pendiente que tiene señas abonadas con 'Saldo a Favor'. Anule el pago primero para reintegrar los fondos al cliente original.");
                }
            }
        }

        TipoVenta tipoVenta = request.getTipoVenta() != null ? request.getTipoVenta() : TipoVenta.valueOf(pendingSale.getTipoVenta());

        // Return old stock (and determine previously included products to bypass inactive check)
        List<DetalleVenta> oldDetails = ventaRepository.findDetallesByVentaId(id);
        java.util.Map<Long, Long> oldProductQuantities = oldDetails.stream()
                .collect(java.util.stream.Collectors.toMap(DetalleVenta::getProductoId, DetalleVenta::getCantidad, Long::sum));

        ProcessedSaleResult processedData = processItems(request.getItems(), tipoVenta, oldProductQuantities);

        Double subtotal = processedData.getTotalVenta();
        Double descuentoGlobal = request.getDescuentoGlobal();
        // Defense-in-Depth: guard against NaN/Infinity injected via raw HTTP payloads
        if (Double.isNaN(descuentoGlobal) || Double.isInfinite(descuentoGlobal)) throw new BusinessRuleException("Valor de descuento inválido.");
        if (descuentoGlobal < 0 || descuentoGlobal > subtotal) throw new BusinessRuleException("Descuento global inválido.");

        Double recargoGlobal = request.getRecargoGlobal();
        // Defense-in-Depth: guard against NaN/Infinity injected via raw HTTP payloads
        if (Double.isNaN(recargoGlobal) || Double.isInfinite(recargoGlobal)) throw new BusinessRuleException("Valor de recargo inválido.");
        if (recargoGlobal < 0) throw new BusinessRuleException("El recargo global no puede ser negativo.");

        // Formula: Total = Subtotal - Descuento + Recargo
        Double finalTotal = Math.round((subtotal - descuentoGlobal + recargoGlobal) * 100.0) / 100.0;

        Double totalPagado = ventaRepository.sumPagosActivosByVentaId(id);
        Double chequesPendientes = alertaChequeRepository.sumMontoPendienteByVentaId(id);
        double totalAbonado = Math.round((totalPagado + chequesPendientes) * 100.0) / 100.0;
        Double saldoGenerado = request.getSaldoGenerado();
        if (finalTotal + saldoGenerado < totalAbonado) throw new BusinessRuleException(String.format("El nuevo total más el saldo generado ($%.2f) no puede ser menor al monto ya abonado ($%.2f).", finalTotal + saldoGenerado, totalAbonado));

        if (!oldDetails.isEmpty()) {
            for (DetalleVenta d : oldDetails) {
                List<StockLocation> allLocations = stockRepository.findByProductId(d.getProductoId());
                Long primaryLocId = allLocations.isEmpty() ? 1L : allLocations.getFirst().getUbicacionId();
                stockRepository.addStock(d.getProductoId(), primaryLocId, d.getCantidad());
            }
        }

        ventaRepository.marcarDetallesComoAnulados(id);

        List<DetalleVenta> detalles = processedData.getDetalles();
        detalles.forEach(d -> { d.setVentaId(id); d.setAnulado(false); });
        ventaRepository.saveDetalles(detalles);

        List<String> stockAlerts = updateStockFromDetails(detalles);
        ventaRepository.updatePendingSaleHeader(id, finalTotal, descuentoGlobal, recargoGlobal, saldoGenerado, resolvedClienteId, resolvedClienteNombre, tipoVenta.name());

        auditoriaService.registrarAccion(usuarioId, "MODIFICAR_CARRITO_PENDIENTE", "Pedido ID: " + id + ". Nuevo Total: $" + finalTotal);
        List<PagoVenta> pagosActivos = ventaRepository.findPagosActivosByVentaId(id);

        return new VentaResponse(
                id,
                pendingSale.getFecha(),
                resolvedClienteNombre,
                resolvedClienteId,
                ventaRepository.findVendedorNombre(pendingSale.getUsuarioId()),
                finalTotal,
                descuentoGlobal,
                recargoGlobal,
                tipoVenta.name(),
                detalles,
                pagosActivos,
                stockAlerts,
                PENDIENTE,
                pendingSale.getCostoTotal(),
                (pendingSale.getVersion() != null ? pendingSale.getVersion() + 1 : 1)
        );
    }

    @Transactional
    public void anularPago(Long pendingId, Long pagoId, Long usuarioId) {
        boolean isLocked = ventaRepository.lockVentaForUpdate(pendingId, PENDIENTE);
        if (!isLocked) throw new BusinessRuleException("El pedido no se encuentra pendiente o fue modificado concurrentemente.");

        Venta pendingSale = ventaRepository.findById(pendingId).orElseThrow(() -> new ResourceNotFoundException(VENTA_PENDIENTE, pendingId));
        if (!PENDIENTE.equals(pendingSale.getEstado())) throw new BusinessRuleException("Solo se pueden anular pagos de pedidos en estado PENDIENTE.");

        Double monto = ventaRepository.getMontoPagoActivo(pagoId, pendingId);
        PagoVenta pago = ventaRepository.findPagosByVentaId(pendingId).stream()
                .filter(p -> p.getId().equals(pagoId) && !Boolean.TRUE.equals(p.getAnulado()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("El pago no existe, no pertenece a este pedido o ya está anulado."));

        // SALDO annulment safety: restore the client's credit balance if the payment
        // was made using the 'Saldo a Favor' method, to prevent permanent fund loss.
        metodoPagoRepository.findById(pago.getMetodoPagoId())
                .filter(mp -> SALDO_ACRONIMO.equals(mp.getAcronimo()))
                .ifPresent(mp -> {
                    // The venta must have a linked client to restore the saldo.
                    // If clienteId is null (legacy sale), we log and skip rather than crash.
                    if (pendingSale.getClienteId() != null) {
                        clienteRepository.addSaldo(pendingSale.getClienteId(), Math.abs(monto));
                    }
                });

        ventaRepository.updatePagoAnulado(pagoId);
        auditoriaService.registrarAccion(usuarioId, "ANULAR_PAGO_PENDIENTE", "Pago ID: " + pagoId + " del Pedido ID: " + pendingId + " por $" + monto + " anulado.");
    }

    @Transactional
    public void cancelarPendiente(Long id, Long authenticatedUserId) {
        boolean isLocked = ventaRepository.lockVentaForUpdate(id, PENDIENTE);
        if (!isLocked) throw new BusinessRuleException("El pedido no se encuentra pendiente o fue modificado concurrentemente.");

        Venta pendingSale = ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(VENTA_PENDIENTE, id));
        if (!PENDIENTE.equals(pendingSale.getEstado())) throw new BusinessRuleException("Solo se pueden cancelar pedidos en estado PENDIENTE.");

        // Restore Saldo a Favor for any active payments (Vector 10)
        List<PagoVenta> pagosActivos = ventaRepository.findPagosActivosByVentaId(id);
        for (PagoVenta pago : pagosActivos) {
            metodoPagoRepository.findById(pago.getMetodoPagoId())
                    .filter(mp -> SALDO_ACRONIMO.equals(mp.getAcronimo()))
                    .ifPresent(mp -> {
                        if (pendingSale.getClienteId() != null) {
                            clienteRepository.addSaldo(pendingSale.getClienteId(), pago.getMonto());
                        }
                    });
            ventaRepository.updatePagoAnulado(pago.getId());
        }

        // Cancel Zombie Cheques (Vector 16)
        alertaChequeRepository.cancelarChequesPendientesByVentaId(id);

        int rowsAffected = ventaRepository.updateEstadoAtomic(id, "CANCELADA_PENDIENTE", PENDIENTE);
        if (rowsAffected == 0) throw new BusinessRuleException("El pedido fue modificado concurrentemente.");
        List<DetalleVenta> detalles = ventaRepository.findDetallesByVentaId(id);
        if (!detalles.isEmpty()) {
            List<com.centralizesys.model.product.Location> allLocations = stockRepository.findAllLocations();
            if (allLocations.isEmpty()) throw new BusinessRuleException("No hay ubicaciones para retornar el stock reservado.");
            Long primaryLocId = allLocations.getFirst().getId();
            for (DetalleVenta d : detalles) {
                stockRepository.addStock(d.getProductoId(), primaryLocId, d.getCantidad());
            }
        }
        auditoriaService.registrarAccion(authenticatedUserId, "CANCELAR_PENDIENTE", "Pedido ID " + id + " cancelado.");
    }

    @Transactional
    public VentaResponse finalizarVenta(Long id, Long authenticatedUserId) {
        Venta pendingSale = ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(VENTA_PENDIENTE, id));
        if (!PENDIENTE.equals(pendingSale.getEstado())) throw new BusinessRuleException("Solo se pueden finalizar pedidos en estado PENDIENTE.");

        List<PagoVenta> pagosActivos = ventaRepository.findPagosActivosByVentaId(id);
        double totalPagosEfectivos = pagosActivos.stream().mapToDouble(PagoVenta::getMonto).sum();
        Double chequesPendientes = alertaChequeRepository.sumMontoPendienteByVentaId(id);
        double totalPagado = Math.round((totalPagosEfectivos + chequesPendientes) * 100.0) / 100.0;

        if (totalPagado <= PAYMENT_COMPLETE_EPSILON) {
            throw new BusinessRuleException("El pedido debe tener al menos una seña para ser finalizado.");
        }
        Double saldoGenerado = pendingSale.getSaldoGenerado();
        if (totalPagado > pendingSale.getTotalVenta() + saldoGenerado + PAYMENT_COMPLETE_EPSILON) {
            throw new BusinessRuleException("El monto total abonado supera el total de la venta más el saldo generado.");
        }

        if (saldoGenerado > 0 && pendingSale.getClienteId() != null) {
            clienteRepository.addSaldo(pendingSale.getClienteId(), saldoGenerado);
        }

        LocalDateTime nuevaFecha = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        int rowsAffected = ventaRepository.updateFechaAndEstadoAtomic(id, nuevaFecha, ACTIVA, PENDIENTE);
        if (rowsAffected == 0) {
            throw new BusinessRuleException("El pedido ya fue finalizado o cancelado concurrentemente.");
        }

        double totalEstimado = pendingSale.getTotalVenta();
        double saldoPendiente = Math.round((totalEstimado - totalPagado) * 100.0) / 100.0;

        if (saldoPendiente > PAYMENT_COMPLETE_EPSILON) {
            if (pendingSale.getClienteId() == null) throw new BusinessRuleException("No se puede finalizar un pedido con saldo deudor (FIADO) sin un cliente seleccionado.");
            deudoresRepository.save(id, pendingSale.getClienteNombre(), pendingSale.getClienteId(), saldoPendiente);
        }

        auditoriaService.registrarAccion(authenticatedUserId, "FINALIZAR_PENDIENTE", String.format("Pedido %d finalizado. Pagado: $%.2f. Deuda: $%.2f.", id, totalPagado, saldoPendiente));
        String vendedorNombre = ventaRepository.findVendedorNombre(pendingSale.getUsuarioId());

        return new VentaResponse(
                id,
                nuevaFecha,
                pendingSale.getClienteNombre(),
                pendingSale.getClienteId(),
                vendedorNombre,
                pendingSale.getTotalVenta(),
                pendingSale.getDescuentoGlobal(),
                pendingSale.getRecargoGlobal(),
                pendingSale.getTipoVenta(),
                ventaRepository.findDetallesByVentaId(id),
                pagosActivos,
                Collections.emptyList(),
                ACTIVA,
                pendingSale.getCostoTotal(),
                pendingSale.getVersion()
        );
    }

    // --- HELPER CLASSES (Internal DTOs) ---
    @Data
    static class ProcessedSaleResult {
        private Double totalVenta;
        private List<DetalleVenta> detalles;
        private Double descuentoGlobal;
        private Double recargoGlobal;
    }

    @Data
    static class PersistedTransactionInfo {
        private Long ventaId;
        private List<PagoVenta> pagosPersistidos;
    }

    // --- READ OPERATIONS ---

    public List<VentaResponse> getVentasByClienteId(Long clienteId) {
        List<Venta> ventas = ventaRepository.findVentasByClienteId(clienteId);
        List<VentaResponse> responses = new java.util.ArrayList<>();
        for (Venta venta : ventas) {
            String vendedorNombre = ventaRepository.findVendedorNombre(venta.getUsuarioId());
            responses.add(new VentaResponse(
                    venta.getId(), venta.getFecha(), venta.getClienteNombre(),
                    venta.getClienteId(), vendedorNombre, venta.getTotalVenta(), venta.getDescuentoGlobal(),
                    venta.getRecargoGlobal(),
                    venta.getTipoVenta(), null, null, null, venta.getEstado(),
                    venta.getCostoTotal(),
                    venta.getVersion()
            ));
        }
        return responses;
    }

    // --- HELPER METHODS ---
    private void validateRequest(VentaRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("La venta debe tener al menos un producto.");
        }
        if (request.getClienteNombre() != null) {
            request.setClienteNombre(request.getClienteNombre().trim());
        }
    }

    private void resolveClientForSale(VentaRequest request, Long usuarioId) {
        if (request.getClienteId() != null && request.getClienteId() != 999L) {
            com.centralizesys.model.client.Cliente client = clienteRepository.findById(request.getClienteId())
                    .orElseThrow(() -> new BusinessRuleException("El cliente proporcionado no existe."));
            if (!Boolean.TRUE.equals(client.getActivo())) {
                throw new BusinessRuleException("El cliente seleccionado está inactivo o ha sido eliminado.");
            }
        } else if (request.getClienteId() == null && request.getClienteNombre() != null && !request.getClienteNombre().trim().isEmpty() && !request.getClienteNombre().equalsIgnoreCase("Consumidor Final")) {
            String nombre = request.getClienteNombre().trim();
            java.util.Optional<com.centralizesys.model.client.ClienteResponse> optCliente = clienteRepository.findByNombre(nombre);
            if (optCliente.isPresent()) {
                request.setClienteId(optCliente.get().getId());
            } else {
                com.centralizesys.model.client.Cliente nuevoCliente = new com.centralizesys.model.client.Cliente();
                nuevoCliente.setNombre(com.centralizesys.util.StringUtil.safeTruncate(nombre, 255));
                nuevoCliente.setActivo(true);
                nuevoCliente.setSaldoAFavor(0.0);
                com.centralizesys.model.client.Cliente guardado = clienteRepository.save(nuevoCliente, usuarioId);
                request.setClienteId(guardado != null ? guardado.getId() : 999L);
            }
        }
    }

    ProcessedSaleResult processItems(List<VentaRequest.ItemRequest> itemsReq, TipoVenta tipoVenta) {
        return processItems(itemsReq, tipoVenta, java.util.Collections.emptyMap());
    }

    ProcessedSaleResult processItems(List<VentaRequest.ItemRequest> itemsReq, TipoVenta tipoVenta, java.util.Map<Long, Long> oldProductQuantities) {
        ProcessedSaleResult result = new ProcessedSaleResult();
        result.setDetalles(new ArrayList<>());
        Double totalAcumulado = 0.0;

        for (VentaRequest.ItemRequest itemReq : itemsReq) {
            if (itemReq.getCantidad() == null || itemReq.getCantidad() <= 0) {
                throw new BusinessRuleException("La cantidad del producto debe ser mayor a cero.");
            }

            Product producto = productRepository.findByIdIncludingInactive(itemReq.getProductoId()).orElseThrow(() -> new ResourceNotFoundException("Producto", itemReq.getProductoId()));

            if (!producto.isActivo()) {
                Long oldQty = oldProductQuantities != null ? oldProductQuantities.get(producto.getId()) : null;
                if (oldQty == null) {
                    throw new BusinessRuleException("El producto '" + producto.getDescripcion() + "' está eliminado y no puede ser incluido.");
                } else if (itemReq.getCantidad() > oldQty) {
                    throw new BusinessRuleException("El producto '" + producto.getDescripcion() + "' está eliminado. No puede incrementar su cantidad (máximo permitido: " + oldQty + ").");
                }
            }

            DetalleVenta detalle = createDetalleVenta(producto, itemReq, tipoVenta);
            result.getDetalles().add(detalle);
            totalAcumulado += detalle.getSubtotal();
        }
        result.setTotalVenta(Math.round(totalAcumulado * 100.0) / 100.0);
        return result;
    }

    private String resolveFamilyDescripcion(Product producto) {
        return "1".equals(producto.getCodigo()) ? producto.getDescripcion() : null;
    }

    private DetalleVenta createDetalleVenta(Product producto, VentaRequest.ItemRequest itemReq, TipoVenta tipoVenta) {
        DetalleVenta detalle = new DetalleVenta();
        detalle.setProductoId(producto.getId());
        detalle.setCodigoSnapshot(producto.getCodigo());
        detalle.setDescripcionSnapshot(producto.getDescripcion());

        String familyDescripcion = resolveFamilyDescripcion(producto);
        java.util.Optional<Double> wacOptional = productRepository.findWAC(producto.getCodigo(), familyDescripcion);
        double wac = wacOptional.orElse(producto.getPrecioCosto());

        detalle.setCostoSnapshot(Math.round(wac * 100.0) / 100.0);
        detalle.setCantidad(itemReq.getCantidad());

        Double precioBase;
        if (tipoVenta == TipoVenta.MAYORISTA) {
            precioBase = producto.getPrecioMayorista();
            if (precioBase == null) precioBase = 0.0;
        } else {
            precioBase = producto.getPrecioMinorista();
        }
        detalle.setPrecioLista(precioBase);

        Double valorDescuento = itemReq.getValorDescuento();
        Double precioFinal = calculateFinalPrice(precioBase, valorDescuento, producto.getDescripcion());

        detalle.setDescuentoValor(valorDescuento);
        String rz = itemReq.getRazonDescuento();
        if (rz != null && rz.length() > 255) rz = rz.substring(0, 255);
        detalle.setRazonDescuento(rz);
        detalle.setPrecioUnitario(precioFinal);
        detalle.setSubtotal(itemReq.getCantidad() * precioFinal);

        return detalle;
    }

    private Double calculateFinalPrice(Double basePrice, Double value, String productName) {
        if (value < 0) throw new BusinessRuleException("El descuento no puede ser negativo para: " + productName);
        if (value > basePrice) throw new BusinessRuleException("El descuento no puede ser mayor al precio para: " + productName);
        return Math.round((basePrice - value) * 100.0) / 100.0;
    }

    private PersistedTransactionInfo saveTransactionData(VentaRequest request, ProcessedSaleResult processedData, Double saldoGenerado) {
        PersistedTransactionInfo info = new PersistedTransactionInfo();
        Long ventaId = persistVentaBase(request, processedData, saldoGenerado);
        info.setVentaId(ventaId);

        persistDetalles(ventaId, processedData.getDetalles());
        List<PagoVenta> pagosPersistidos = processPagosVenta(ventaId, request.getPagos(), request.getClienteId(), request.getUsuarioId());
        info.setPagosPersistidos(pagosPersistidos);

        return info;
    }

    private Long persistVentaBase(VentaRequest request, ProcessedSaleResult processedData, Double saldoGenerado) {
        Venta venta = new Venta();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        venta.setFecha(now);
        venta.setFechaCreacion(now);
        venta.setClienteNombre(request.getClienteNombre());
        venta.setClienteId(request.getClienteId());
        venta.setTotalVenta(processedData.getTotalVenta());
        venta.setDescuentoGlobal(processedData.getDescuentoGlobal());
        venta.setRecargoGlobal(processedData.getRecargoGlobal());
        venta.setSaldoGenerado(saldoGenerado);
        venta.setTipoVenta(request.getTipoVenta() != null ? request.getTipoVenta().name() : MINORISTA);
        venta.setUsuarioId(request.getUsuarioId());
        venta.setEstado(ACTIVA);
        return ventaRepository.saveVenta(venta);
    }

    private void persistDetalles(Long ventaId, List<DetalleVenta> detalles) {
        detalles.forEach(d -> {
            d.setVentaId(ventaId);
            d.setAnulado(false);
        });
        ventaRepository.saveDetalles(detalles);
    }

    private List<PagoVenta> processPagosVenta(Long ventaId, List<VentaRequest.PagoRequest> pagos, Long clienteId, Long usuarioId) {
        if (pagos == null || pagos.isEmpty()) {
            return Collections.emptyList();
        }
        MetodoPago saldoMethod = metodoPagoRepository.findByAcronimo(SALDO_ACRONIMO)
                .orElseThrow(() -> new BusinessRuleException("Método SALDO no configurado."));
        Long saldoId = saldoMethod.getId();
        List<PagoVenta> pagosEntities = new ArrayList<>();
        for (VentaRequest.PagoRequest p : pagos) {
            MetodoPago metodo = metodoPagoRepository.findById(p.getMetodoPagoId())
                    .orElseThrow(() -> new BusinessRuleException("Método de pago no encontrado."));
            if (!metodo.isActivo()) throw new BusinessRuleException("El método de pago seleccionado se encuentra inactivo.");

            if (saldoId != null && saldoId.equals(p.getMetodoPagoId())) {
                if (clienteId == null) throw new BusinessRuleException("El pago con Saldo a Favor requiere un cliente seleccionado.");
                int rows = clienteRepository.deductSaldo(clienteId, p.getMonto());
                if (rows == 0) throw new BusinessRuleException("Saldo a favor insuficiente para el cliente.");
            }
            pagosEntities.add(new PagoVenta(null, ventaId, p.getMetodoPagoId(), p.getMonto(), null, false, usuarioId));
        }
        ventaRepository.savePagos(pagosEntities);
        return pagosEntities;
    }

    List<String> updateStockFromDetails(List<DetalleVenta> detalles) {
        List<String> alerts = new ArrayList<>();
        for (DetalleVenta detalle : detalles) {
            String alerta = deductStockFromInventory(detalle.getProductoId(), detalle.getDescripcionSnapshot(), detalle.getCantidad());
            if (alerta != null) alerts.add(alerta);
        }
        return alerts;
    }

    String deductStockFromInventory(Long productId, String productName, Long quantityNeeded) {
        List<StockLocation> locations = stockRepository.findByProductId(productId);
        Long remainingToDeduct = quantityNeeded;

        for (StockLocation loc : locations) {
            if (remainingToDeduct <= 0) break;
            Long available = loc.getCantidad();
            if (available > 0) {
                Long toTake = Math.min(available, remainingToDeduct);
                stockRepository.subtractStock(loc.getUbicacionId(), productId, toTake);
                remainingToDeduct -= toTake;
            }
        }

        if (remainingToDeduct > 0) {
            if (locations.isEmpty()) {
                List<com.centralizesys.model.product.Location> allLocations = stockRepository.findAllLocations();
                if (allLocations.isEmpty()) {
                    return "CRÍTICO: El producto '" + productName + "' se vendió pero el sistema no tiene NINGUNA ubicación de stock configurada.";
                }
                Long defaultLocId = allLocations.getFirst().getId();
                stockRepository.addStock(productId, defaultLocId, -remainingToDeduct);
                return "ATENCIÓN: El producto '" + productName + "' se vendió pero no tenía ubicación asignada. Stock negativo en principal.";
            } else {
                Long defaultLocId = locations.getFirst().getUbicacionId();
                stockRepository.subtractStock(defaultLocId, productId, remainingToDeduct);
                return "ATENCIÓN: Stock insuficiente para '" + productName + "'. El sistema registró stock negativo.";
            }
        }
        return null;
    }

    private void handleDebt(Long ventaId, String clienteNombre, Long clienteId, Double totalVenta, List<PagoVenta> pagosPersistidos, double chequesTotal) {
        Double totalPagadoEfectivo = pagosPersistidos.stream().mapToDouble(PagoVenta::getMonto).sum();
        Double deuda = totalVenta - (totalPagadoEfectivo + chequesTotal);
        if (deuda > PAYMENT_COMPLETE_EPSILON) {
            if (clienteId == null) throw new BusinessRuleException("No se puede registrar una venta con saldo deudor (FIADO) sin un cliente seleccionado de la base de datos.");
            if (clienteNombre == null || clienteNombre.isBlank()) throw new BusinessRuleException("Para dejar una deuda (Fiado), se requiere el nombre del cliente.");
            deudoresRepository.save(ventaId, clienteNombre, clienteId, Math.round(deuda * 100.0) / 100.0);
        }
    }

    @Transactional
    public void anularVentaHistorica(Long ventaId) {
        Venta venta = ventaRepository.findById(ventaId).orElseThrow(() -> new ResourceNotFoundException("Venta", ventaId));
        if (ANULADA.equals(venta.getEstado())) throw new BusinessRuleException("La venta ya se encuentra anulada.");
        if (!ACTIVA.equals(venta.getEstado())) throw new BusinessRuleException("Solo se pueden anular ventas con estado ACTIVA.");

        int rowsAffected = ventaRepository.updateEstadoAtomic(ventaId, ANULADA, ACTIVA);
        if (rowsAffected == 0) throw new BusinessRuleException("La venta está siendo anulada por otro proceso o ya no se encuentra activa.");

        List<DetalleVenta> detalles = ventaRepository.findDetallesByVentaId(ventaId);
        if (!detalles.isEmpty()) {
            List<com.centralizesys.model.product.Location> allLocations = stockRepository.findAllLocations();
            if (allLocations.isEmpty()) throw new BusinessRuleException("No hay ubicaciones configuradas para retornar el stock.");
            Long primaryLocationId = allLocations.getFirst().getId();
            for (DetalleVenta detalle : detalles) {
                Long yaDevuelta = devolucionesRepository.sumCantidadDevueltaByDetalleId(detalle.getId());
                Long netToReturn = detalle.getCantidad() - yaDevuelta;
                if (netToReturn > 0) {
                    stockRepository.addStock(detalle.getProductoId(), primaryLocationId, netToReturn);
                }
            }
        }

        deudoresRepository.findByVentaId(ventaId).ifPresent(deuda ->
                deudoresRepository.updateMontoAndEstado(deuda.getId(), deuda.getMontoDeuda(), ANULADA)
        );

        alertaChequeRepository.updateEstadoByVentaId(ventaId, ANULADA);

        // Vector 10: Refund 'Saldo a Favor' for historic annulments
        if (venta.getClienteId() != null) {
            List<PagoVenta> pagos = ventaRepository.findPagosActivosByVentaId(ventaId);
            for (PagoVenta pago : pagos) {
                metodoPagoRepository.findById(pago.getMetodoPagoId())
                        .filter(mp -> SALDO_ACRONIMO.equals(mp.getAcronimo()))
                        .ifPresent(mp -> clienteRepository.addSaldo(venta.getClienteId(), pago.getMonto()));
            }
        }

        Long currentUserId = com.centralizesys.security.SecurityUtils.getAuthenticatedUserId();
        auditoriaService.registrarAccion(currentUserId, "ANULAR_VENTA", "Se anuló la venta ID " + ventaId + " y se retornó el stock a la ubicación principal.");
    }

    // =========================================================================
    // PARTIAL RETURN (Devolución Parcial)
    // =========================================================================

    /**
     * Registers the partial (or full) return of one or more products from an ACTIVA sale.
     * <p>
     * Invariants enforced:
     * <ul>
     *   <li>The sale must be ACTIVA.</li>
     *   <li>Each returned line item must belong to this sale.</li>
     *   <li>The return quantity cannot exceed the net remaining quantity
     *       (original - already returned).</li>
     *   <li>If the sale has an active debt, the refund type is forced to SALDO.</li>
     *   <li>If all net quantities reach 0, the sale is fully annulled.</li>
     * </ul>
     * <p>
     * The original {@code detalles_venta} rows are NEVER mutated (immutable ledger).
     */
    @Transactional
    public void registrarDevolucionParcial(Long ventaId, DevolucionRequest request, Long usuarioId) {
        boolean isLocked = ventaRepository.lockVentaForReturn(ventaId);
        if (!isLocked) throw new BusinessRuleException("La venta está siendo procesada por otro usuario o ya no se encuentra activa.");

        Venta venta = validateSaleForReturn(ventaId, request);

        String tipoReembolso = request.getTipoReembolso() == null || request.getTipoReembolso().isBlank() ? "SALDO" : request.getTipoReembolso();
        java.util.Optional<com.centralizesys.model.debt.DeudaResponse> deudaOpt = validateDebtForRefund(ventaId, tipoReembolso);

        double totalReembolso = processReturnedItems(ventaId, request, usuarioId);

        applyRefund(ventaId, venta, deudaOpt, totalReembolso, tipoReembolso, usuarioId);

        auditoriaService.registrarAccion(usuarioId, "DEVOLUCION_PARCIAL", String.format("Devolución parcial en Venta ID %d. Reembolso: $%.2f vía %s.", ventaId, totalReembolso, tipoReembolso));
        checkAndUpdateFinalSaleStatus(ventaId, usuarioId);
    }

    private Venta validateSaleForReturn(Long ventaId, DevolucionRequest request) {
        Venta venta = ventaRepository.findById(ventaId).orElseThrow(() -> new ResourceNotFoundException("Venta", ventaId));
        if (!ACTIVA.equals(venta.getEstado()) && !"DEVUELTA_PARCIAL".equals(venta.getEstado())) {
            throw new BusinessRuleException("Solo se pueden registrar devoluciones en ventas con estado ACTIVA o DEVUELTA_PARCIAL.");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("La devolución debe incluir al menos un producto.");
        }
        return venta;
    }

    private java.util.Optional<com.centralizesys.model.debt.DeudaResponse> validateDebtForRefund(Long ventaId, String tipoReembolso) {
        java.util.Optional<com.centralizesys.model.debt.DeudaResponse> deudaOpt = deudoresRepository.findByVentaId(ventaId)
                .filter(d -> "PENDIENTE".equals(d.getEstado()) || "PARCIAL".equals(d.getEstado()));
        if (deudaOpt.isPresent() && "EFECTIVO".equals(tipoReembolso)) {
            throw new BusinessRuleException("No se puede hacer una devolución en efectivo cuando la venta tiene una deuda activa. Use 'Saldo a Favor'.");
        }
        return deudaOpt;
    }

    private double processReturnedItems(Long ventaId, DevolucionRequest request, Long usuarioId) {
        double totalReembolso = 0.0;
        List<com.centralizesys.model.product.Location> allLocations = stockRepository.findAllLocations();
        if (allLocations.isEmpty()) throw new BusinessRuleException("No hay ubicaciones configuradas para retornar el stock.");
        Long primaryLocationId = allLocations.getFirst().getId();

        Venta venta = ventaRepository.findById(ventaId).orElseThrow();
        Double descuentoGlobal = venta.getDescuentoGlobal();
        double subtotalVenta = venta.getTotalVenta() + descuentoGlobal;
        double discountProportion = (subtotalVenta > 0 && descuentoGlobal > 0) ? descuentoGlobal / subtotalVenta : 0.0;

        // ANTI-DEADLOCK GUARD: Sort items by ID before acquiring pessimistic row locks
        List<DevolucionRequest.DevolucionItemRequest> sortedItems = new java.util.ArrayList<>(request.getItems());
        sortedItems.sort(java.util.Comparator.comparing(DevolucionRequest.DevolucionItemRequest::getDetalleVentaId));

        for (DevolucionRequest.DevolucionItemRequest item : sortedItems) {
            totalReembolso += validateAndProcessSingleReturnedItem(ventaId, item, primaryLocationId, discountProportion, request, usuarioId);
        }
        return Math.round(totalReembolso * 100.0) / 100.0;
    }

    private double validateAndProcessSingleReturnedItem(Long ventaId, DevolucionRequest.DevolucionItemRequest item, Long primaryLocationId, double discountProportion, DevolucionRequest request, Long usuarioId) {
        // VULNERABILITY FIX (Vector 8): Use pessimistic locking (FOR UPDATE) to prevent concurrent
        // threads from reading the same `yaDevuelta` amount simultaneously.
        DetalleVenta detalle = ventaRepository.findDetalleByIdForUpdate(item.getDetalleVentaId())
                .orElseThrow(() -> new ResourceNotFoundException("DetalleVenta", item.getDetalleVentaId()));

        if (!ventaId.equals(detalle.getVentaId())) throw new BusinessRuleException("El detalle ID " + item.getDetalleVentaId() + " no pertenece a la venta " + ventaId + ".");
        if (Boolean.TRUE.equals(detalle.getAnulado())) throw new BusinessRuleException("El detalle ID " + item.getDetalleVentaId() + " ya fue anulado.");
        if (item.getCantidadDevuelta() == null || item.getCantidadDevuelta() <= 0) throw new BusinessRuleException("La cantidad a devolver debe ser mayor a 0.");

        Long yaDevuelta = devolucionesRepository.sumCantidadDevueltaByDetalleId(detalle.getId());
        Long netRestante = detalle.getCantidad() - yaDevuelta;
        if (item.getCantidadDevuelta() > netRestante) {
            throw new BusinessRuleException(String.format("Solo quedan %d unidades devolvibles del producto '%s'.", netRestante, detalle.getDescripcionSnapshot()));
        }

        double montoItemBruto = Math.round((detalle.getPrecioUnitario() * item.getCantidadDevuelta()) * 100.0) / 100.0;
        double deductDiscount = Math.round((montoItemBruto * discountProportion) * 100.0) / 100.0;
        double montoItemNeto = Math.round((montoItemBruto - deductDiscount) * 100.0) / 100.0;

        String obs = request.getObservaciones();
        if (obs != null && obs.length() > 255) obs = obs.substring(0, 255);
        devolucionesRepository.save(ventaId, item.getDetalleVentaId(), item.getCantidadDevuelta(), montoItemNeto, request.getTipoReembolso() != null ? request.getTipoReembolso() : "SALDO", obs, usuarioId);
        stockRepository.addStock(detalle.getProductoId(), primaryLocationId, item.getCantidadDevuelta());

        return montoItemNeto;
    }


    private void applyRefund(Long ventaId, Venta venta, java.util.Optional<com.centralizesys.model.debt.DeudaResponse> deudaOpt, double totalReembolso, String tipoReembolso, Long usuarioId) {
        double remainingReembolso = totalReembolso;

        if (deudaOpt.isPresent()) {
            com.centralizesys.model.debt.DeudaResponse deuda = deudaOpt.get();
            double appliedToDebt = Math.min(deuda.getMontoDeuda(), remainingReembolso);

            int rowsAffected = deudoresRepository.deductDeudaAtomic(deuda.getId(), appliedToDebt, deuda.getMontoOriginal());
            if (rowsAffected == 0) throw new BusinessRuleException("Error de concurrencia al actualizar la deuda. Posible intento de deducción excesivo.");

            MetodoPago efectivoDummy = metodoPagoRepository.findByAcronimo("E")
                    .orElseThrow(() -> new BusinessRuleException("Método EFECTIVO (E) no configurado."));
            Long metodoId = efectivoDummy.getId();
            deudoresRepository.insertarPagoDeuda(deuda.getId(), metodoId, appliedToDebt, "Abonado automáticamente por devolución de productos", usuarioId);

            remainingReembolso = Math.round((remainingReembolso - appliedToDebt) * 100.0) / 100.0;
        }

        if (remainingReembolso > 0.0) {
            if ("SALDO".equals(tipoReembolso)) {
                if (venta.getClienteId() == null) throw new BusinessRuleException("No se puede reembolsar a Saldo a Favor porque la venta no tiene un cliente asociado.");
                clienteRepository.addSaldo(venta.getClienteId(), remainingReembolso);
            } else if ("EFECTIVO".equals(tipoReembolso)) {
                MetodoPago efectivo = metodoPagoRepository.findByAcronimo("E").orElseThrow(() -> new BusinessRuleException("Método de pago 'Efectivo' no encontrado en el sistema."));
                ventaRepository.saveNegativoPago(ventaId, efectivo.getId(), remainingReembolso, usuarioId);
            } else {
                throw new BusinessRuleException("Tipo de reembolso no válido: " + tipoReembolso);
            }
        }
    }

    private void checkAndUpdateFinalSaleStatus(Long ventaId, Long usuarioId) {
        List<DetalleVenta> existingDetalles = ventaRepository.findDetallesByVentaId(ventaId);
        boolean allReturned = true;
        for (DetalleVenta d : existingDetalles) {
            Long devueltaAcum = devolucionesRepository.sumCantidadDevueltaByDetalleId(d.getId());
            if (devueltaAcum == null) devueltaAcum = 0L;
            if (devueltaAcum < d.getCantidad()) {
                allReturned = false;
                break;
            }
        }

        if (allReturned) {
            int rows = ventaRepository.updateEstadoSafeReturn(ventaId, ANULADA);
            if (rows == 0) throw new BusinessRuleException("La venta fue modificada concurrentemente o ya no se encuentra activa.");
            alertaChequeRepository.updateEstadoByVentaId(ventaId, ANULADA);
            auditoriaService.registrarAccion(usuarioId, "ANULAR_VENTA", "Venta " + ventaId + " anulada por devolución total.");
        } else {
            int rows = ventaRepository.updateEstadoSafeReturn(ventaId, "DEVUELTA_PARCIAL");
            if (rows == 0) throw new BusinessRuleException("La venta fue modificada concurrentemente o ya no se encuentra activa.");
            auditoriaService.registrarAccion(usuarioId, "DEVOLUCION_PARCIAL", "Devolución parcial registrada en venta " + ventaId);
        }
    }
}