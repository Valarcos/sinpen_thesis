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
        LocalDateTime end = (endDate == null || endDate.isBlank()) ? LocalDateTime.now(ZoneId.systemDefault()) : LocalDate.parse(endDate).atTime(23, 59, 59, 999999999);
        LocalDateTime start = (startDate == null || startDate.isBlank()) ? end.minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0) : LocalDate.parse(startDate).atStartOfDay();

        if (searchId == null) {
            long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(start.atZone(ZoneId.systemDefault()), end.atZone(ZoneId.systemDefault()));
            if (daysDiff < 0) throw new BusinessRuleException("La fecha de inicio no puede ser posterior a la fecha de fin.");
            if (daysDiff > 60) throw new BusinessRuleException("El rango de fechas no puede exceder los 60 días.");
        }

        int offset = page * size;
        List<Venta> ventas = ventaRepository.findVentasByFechaBetween(start, end, searchId, size, offset);
        long totalElements = ventaRepository.countVentasByFechaBetween(start, end, searchId);
        long totalPages = (long) Math.ceil((double) totalElements / size);

        return new PageResponse<>(ventas, (long) page, (long) size, totalElements, totalPages);
    }

    public PageResponse<Venta> getVentasPendientesPage(String startDate, String endDate, int page, int size) {
        LocalDateTime end = (endDate == null || endDate.isBlank()) ? LocalDateTime.now(ZoneId.systemDefault()) : LocalDate.parse(endDate).atTime(23, 59, 59, 999999999);
        LocalDateTime start = (startDate == null || startDate.isBlank()) ? end.minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0) : LocalDate.parse(startDate).atStartOfDay();

        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(start.atZone(ZoneId.systemDefault()), end.atZone(ZoneId.systemDefault()));
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
                venta.getTipoVenta(),
                detalles,
                pagos,
                null,
                venta.getEstado(),
                venta.getCostoTotal()
        );
    }

    @Transactional
    public VentaResponse registrarVenta(VentaRequest request) {
        validateRequest(request);
        ProcessedSaleResult processedData = processItems(request.getItems(), request.getTipoVenta());
        Double subtotal = processedData.getTotalVenta();
        Double descuentoGlobal = request.getDescuentoGlobal() != null ? request.getDescuentoGlobal() : 0.0;

        if (descuentoGlobal < 0) throw new BusinessRuleException("El descuento global no puede ser negativo.");
        if (descuentoGlobal > subtotal) throw new BusinessRuleException("El descuento global no puede ser mayor al subtotal.");

        Double finalTotal = Math.round((subtotal - descuentoGlobal) * 100.0) / 100.0;
        processedData.setTotalVenta(finalTotal);
        processedData.setDescuentoGlobal(descuentoGlobal);

        double pagosTotal = request.getPagos() != null ? request.getPagos().stream().mapToDouble(VentaRequest.PagoRequest::getMonto).sum() : 0.0;
        double chequesTotal = request.getCheques() != null ? request.getCheques().stream().mapToDouble(com.centralizesys.model.cheque.AlertaChequeRequest::getMonto).sum() : 0.0;
        double totalAbonadoRounded = Math.round((pagosTotal + chequesTotal) * 100.0) / 100.0;
        if (totalAbonadoRounded > finalTotal + 0.01) {
            throw new BusinessRuleException(String.format("La suma de los pagos y cheques ($%.2f) no puede superar el total de la venta ($%.2f).", totalAbonadoRounded, finalTotal));
        }

        PersistedTransactionInfo txInfo = saveTransactionData(request, processedData);
        List<String> stockAlerts = updateStockFromDetails(processedData.getDetalles());
        handleDebt(txInfo.getVentaId(), request.getClienteNombre(), request.getClienteId(), processedData.getTotalVenta(), txInfo.getPagosPersistidos());

        auditoriaService.registrarAccion(request.getUsuarioId(), "VENTA", "Venta ID " + txInfo.getVentaId() + " a " + request.getClienteNombre() + ". Total: $" + processedData.getTotalVenta() + " (Desc: " + descuentoGlobal + ")");
        String vendedorNombre = ventaRepository.findVendedorNombre(request.getUsuarioId());

        return new VentaResponse(
                txInfo.getVentaId(),
                LocalDateTime.now(ZoneId.systemDefault()),
                request.getClienteNombre(),
                request.getClienteId(),
                vendedorNombre,
                processedData.getTotalVenta(),
                descuentoGlobal,
                request.getTipoVenta() != null ? request.getTipoVenta().name() : MINORISTA,
                processedData.getDetalles(),
                txInfo.getPagosPersistidos(),
                stockAlerts,
                ACTIVA,
                processedData.getDetalles().stream().mapToDouble(d -> d.getCostoSnapshot() * d.getCantidad()).sum());
    }

    @Transactional
    public VentaResponse registrarVentaConCheques(VentaRequest request) {
        validateRequest(request);
        if (request.getCheques() == null || request.getCheques().isEmpty()) {
            throw new BusinessRuleException("La venta con cheques debe incluir al menos un cheque.");
        }
        for (AlertaChequeRequest cReq : request.getCheques()) {
            if (cReq.getFechaCobro() == null) throw new BusinessRuleException("La fecha de cobro de un cheque no puede estar vacía.");
            if (cReq.getMonto() == null || cReq.getMonto() <= 0) throw new BusinessRuleException("El monto de un cheque debe ser mayor a 0.");
        }

        ProcessedSaleResult processedData = processItems(request.getItems(), request.getTipoVenta());
        Double subtotal = processedData.getTotalVenta();
        Double descuentoGlobal = request.getDescuentoGlobal() != null ? request.getDescuentoGlobal() : 0.0;

        if (descuentoGlobal < 0) throw new BusinessRuleException("El descuento global no puede ser negativo.");
        if (descuentoGlobal > subtotal) throw new BusinessRuleException("El descuento global no puede ser mayor al subtotal.");

        Double finalTotal = Math.round((subtotal - descuentoGlobal) * 100.0) / 100.0;
        processedData.setTotalVenta(finalTotal);
        processedData.setDescuentoGlobal(descuentoGlobal);

        // Server-side guard: cheque installment amounts must equal the sale total.
        // Client-side validation alone violates Zero-Trust principles.
        double chequesTotal = request.getCheques().stream().mapToDouble(AlertaChequeRequest::getMonto).sum();
        double chequesTotalRounded = Math.round(chequesTotal * 100.0) / 100.0;
        if (Math.abs(chequesTotalRounded - finalTotal) > 0.01) {
            throw new BusinessRuleException(
                    String.format("La suma de los cheques ($%.2f) no coincide con el total de la venta ($%.2f).", chequesTotalRounded, finalTotal)
            );
        }

        // A cheque sale has no immediate payments.
        request.setPagos(Collections.emptyList());

        PersistedTransactionInfo txInfo = saveTransactionData(request, processedData);
        List<String> stockAlerts = updateStockFromDetails(processedData.getDetalles());

        // Save Cheques (Alertas)
        for (AlertaChequeRequest chequeReq : request.getCheques()) {
            AlertaCheque cheque = new AlertaCheque(null, txInfo.getVentaId(), chequeReq.getMonto(), chequeReq.getFechaCobro(), PENDIENTE, null, null);
            alertaChequeRepository.save(cheque);
        }

        auditoriaService.registrarAccion(request.getUsuarioId(), "VENTA_CHEQUE", "Venta con Cheques ID " + txInfo.getVentaId() + " a " + request.getClienteNombre() + ". Total: $" + processedData.getTotalVenta() + " (Desc: " + descuentoGlobal + ")");
        String vendedorNombre = ventaRepository.findVendedorNombre(request.getUsuarioId());

        return new VentaResponse(
                txInfo.getVentaId(),
                LocalDateTime.now(ZoneId.systemDefault()),
                request.getClienteNombre(),
                request.getClienteId(),
                vendedorNombre,
                processedData.getTotalVenta(),
                descuentoGlobal,
                request.getTipoVenta() != null ? request.getTipoVenta().name() : MINORISTA,
                processedData.getDetalles(),
                txInfo.getPagosPersistidos(), // Empty for now
                stockAlerts,
                ACTIVA,
                processedData.getDetalles().stream().mapToDouble(d -> d.getCostoSnapshot() * d.getCantidad()).sum());
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

        Long pagoVentaId = ventaRepository.savePagoUnicoReturningId(cheque.getVentaId(), metodoPagoId, cheque.getMonto(), authenticatedUserId);
        alertaChequeRepository.updateEstadoAndPagoVentaId(chequeId, COBRADO, pagoVentaId);

        auditoriaService.registrarAccion(authenticatedUserId, "COBRO_CHEQUE",
                "Cheque ID " + chequeId + " cobrado por $" + cheque.getMonto() + " (Pago ID: " + pagoVentaId + ")");
    }

    @Transactional
    public void cancelarCobroCheque(Long chequeId, Long authenticatedUserId) {
        internalCancelarCobroCheque(chequeId, authenticatedUserId);
    }

    private void internalCancelarCobroCheque(Long chequeId, Long authenticatedUserId) {
        AlertaCheque cheque = alertaChequeRepository.findById(chequeId)
                .orElseThrow(() -> new ResourceNotFoundException(ALERTA_CHEQUE, chequeId));

        if (!COBRADO.equals(cheque.getEstado()) || cheque.getPagoVentaId() == null) {
            throw new BusinessRuleException("El cheque no está cobrado o no tiene un pago asociado.");
        }

        ventaRepository.anularPagoVentaById(cheque.getPagoVentaId());
        alertaChequeRepository.updateEstadoAndPagoVentaId(chequeId, PENDIENTE, null);
        auditoriaService.registrarAccion(authenticatedUserId, "CANCELACION_COBRO_CHEQUE", "Cheque ID: " + chequeId);
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
        alertaChequeRepository.updateEstadoAndPagoVentaId(chequeId, ANULADA, null);

        auditoriaService.registrarAccion(authenticatedUserId, "ANULAR_CHEQUE",
                "Cheque ID " + chequeId + " anulado de la venta (eliminación lógica).");
    }

    @Transactional
    public Long crearPendiente(VentaRequest request, Long authenticatedUserId) {
        validateRequest(request);
        ProcessedSaleResult processedData = processItems(request.getItems(), request.getTipoVenta());

        Double subtotal = processedData.getTotalVenta();
        Double descuentoGlobal = request.getDescuentoGlobal() != null ? request.getDescuentoGlobal() : 0.0;
        if (descuentoGlobal < 0 || descuentoGlobal > subtotal) throw new BusinessRuleException("Descuento global inválido.");
        Double finalTotal = Math.round((subtotal - descuentoGlobal) * 100.0) / 100.0;

        Long pendingId = persistVentaPendienteBase(request, finalTotal, descuentoGlobal, authenticatedUserId);

        List<DetalleVenta> detalles = processedData.getDetalles();
        persistDetalles(pendingId, detalles);
        updateStockFromDetails(detalles);

        processPagosPendientes(pendingId, request, authenticatedUserId);
        processChequesPendientes(pendingId, request);

        auditoriaService.registrarAccion(authenticatedUserId, "CREAR_PENDIENTE", "Pedido creado con ID: " + pendingId);
        return pendingId;
    }

    private Long persistVentaPendienteBase(VentaRequest request, Double finalTotal, Double descuentoGlobal, Long authenticatedUserId) {
        Venta pendingSale = new Venta();
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        pendingSale.setFecha(now);
        pendingSale.setFechaCreacion(now);
        pendingSale.setClienteNombre(request.getClienteNombre());
        pendingSale.setClienteId(request.getClienteId());
        pendingSale.setTotalVenta(finalTotal);
        pendingSale.setDescuentoGlobal(descuentoGlobal);
        pendingSale.setTipoVenta(request.getTipoVenta() != null ? request.getTipoVenta().name() : MINORISTA);
        pendingSale.setUsuarioId(authenticatedUserId);
        pendingSale.setEstado(PENDIENTE);
        return ventaRepository.saveVenta(pendingSale);
    }

    private void processPagosPendientes(Long pendingId, VentaRequest request, Long authenticatedUserId) {
        if (request.getPagos() == null || request.getPagos().isEmpty()) return;
        MetodoPago saldoMethod = metodoPagoRepository.findByAcronimo(SALDO_ACRONIMO).orElse(null);
        Long saldoId = saldoMethod != null ? saldoMethod.getId() : null;
        for (VentaRequest.PagoRequest pvr : request.getPagos()) {
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
        validateMontoPagoPendiente(id, totalNuevoPago, pendingSale.getTotalVenta());
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

    private void validateMontoPagoPendiente(Long id, double totalNuevoPago, double totalVenta) {
        Double totalPagadoPrevio = ventaRepository.sumPagosActivosByVentaId(id);
        Double chequesPendientes = alertaChequeRepository.sumMontoPendienteByVentaId(id);
        double saldoRestante = Math.round((totalVenta - totalPagadoPrevio - chequesPendientes) * 100.0) / 100.0;
        double totalNuevoPagoRounded = Math.round(totalNuevoPago * 100.0) / 100.0;

        if (totalNuevoPagoRounded > saldoRestante + PAYMENT_COMPLETE_EPSILON) {
            throw new BusinessRuleException(Constants.ERR_PENDING_PAYMENT_EXCEEDS_BALANCE);
        }
    }

    private void processPagoPendienteRecords(Long id, List<PagoDeudaRequest> pagos, Venta pendingSale, Long usuarioId) {
        MetodoPago saldoMethod = metodoPagoRepository.findByAcronimo(SALDO_ACRONIMO).orElse(null);
        Long saldoId = saldoMethod != null ? saldoMethod.getId() : null;
        for (PagoDeudaRequest pago : pagos) {
            if (pago.getMontoPago() != null && pago.getMontoPago() > 0) {
                processSinglePagoPendienteRecord(id, pago, pendingSale, saldoId, usuarioId);
            }
        }
    }

    private void processSinglePagoPendienteRecord(Long id, PagoDeudaRequest pago, Venta pendingSale, Long saldoId, Long usuarioId) {
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
        Venta pendingSale = ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(VENTA_PENDIENTE, id));
        if (!PENDIENTE.equals(pendingSale.getEstado())) throw new BusinessRuleException("Solo se puede modificar un pedido en estado PENDIENTE.");

        TipoVenta tipoVenta = request.getTipoVenta() != null ? request.getTipoVenta() : TipoVenta.valueOf(pendingSale.getTipoVenta());
        ProcessedSaleResult processedData = processItems(request.getItems(), tipoVenta);

        Double subtotal = processedData.getTotalVenta();
        Double descuentoGlobal = request.getDescuentoGlobal() != null ? request.getDescuentoGlobal() : 0.0;
        if (descuentoGlobal < 0 || descuentoGlobal > subtotal) throw new BusinessRuleException("Descuento global inválido.");
        Double finalTotal = Math.round((subtotal - descuentoGlobal) * 100.0) / 100.0;

        Double totalPagado = ventaRepository.sumPagosActivosByVentaId(id);
        Double chequesPendientes = alertaChequeRepository.sumMontoPendienteByVentaId(id);
        double totalAbonado = Math.round((totalPagado + chequesPendientes) * 100.0) / 100.0;
        if (finalTotal < totalAbonado) throw new BusinessRuleException(String.format("El nuevo total ($%.2f) no puede ser menor al monto ya abonado ($%.2f).", finalTotal, totalAbonado));

        // Return old stock
        List<DetalleVenta> oldDetails = ventaRepository.findDetallesByVentaId(id);
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
        ventaRepository.updateTotalesConOCC(id, finalTotal, descuentoGlobal);

        auditoriaService.registrarAccion(usuarioId, "MODIFICAR_CARRITO_PENDIENTE", "Pedido ID: " + id + ". Nuevo Total: $" + finalTotal);
        List<PagoVenta> pagosActivos = ventaRepository.findPagosActivosByVentaId(id);

        return new VentaResponse(
                id,
                pendingSale.getFecha(),
                request.getClienteNombre(),
                pendingSale.getClienteId(),
                ventaRepository.findVendedorNombre(pendingSale.getUsuarioId()),
                finalTotal,
                descuentoGlobal,
                pendingSale.getTipoVenta(),
                detalles,
                pagosActivos,
                stockAlerts,
                PENDIENTE,
                pendingSale.getCostoTotal() != null ? pendingSale.getCostoTotal() : 0.0
        );
    }

    @Transactional
    public void anularPago(Long pendingId, Long pagoId, Long usuarioId) {
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
        Venta pendingSale = ventaRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(VENTA_PENDIENTE, id));
        if (!PENDIENTE.equals(pendingSale.getEstado())) throw new BusinessRuleException("Solo se pueden cancelar pedidos en estado PENDIENTE.");

        ventaRepository.updateEstado(id, "CANCELADA_PENDIENTE");
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
        if (totalPagado > pendingSale.getTotalVenta() + PAYMENT_COMPLETE_EPSILON) {
            throw new BusinessRuleException("El monto total abonado supera el total de la venta.");
        }

        LocalDateTime nuevaFecha = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        ventaRepository.updateFechaAndEstado(id, nuevaFecha, ACTIVA);

        double totalEstimado = pendingSale.getTotalVenta();
        double saldoPendiente = Math.round((totalEstimado - totalPagado) * 100.0) / 100.0;

        if (saldoPendiente > PAYMENT_COMPLETE_EPSILON) {
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
                pendingSale.getTipoVenta(),
                ventaRepository.findDetallesByVentaId(id),
                pagosActivos,
                Collections.emptyList(),
                ACTIVA,
                pendingSale.getCostoTotal() != null ? pendingSale.getCostoTotal() : 0.0
        );
    }

    // --- HELPER CLASSES (Internal DTOs) ---
    @Data
    static class ProcessedSaleResult {
        private Double totalVenta;
        private List<DetalleVenta> detalles;
        private Double descuentoGlobal;
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
                    venta.getTipoVenta(), null, null, null, venta.getEstado(),
                    venta.getCostoTotal() != null ? venta.getCostoTotal() : 0.0
            ));
        }
        return responses;
    }

    // --- HELPER METHODS ---
    private void validateRequest(VentaRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("La venta debe tener al menos un producto.");
        }
    }

    ProcessedSaleResult processItems(List<VentaRequest.ItemRequest> itemsReq, TipoVenta tipoVenta) {
        ProcessedSaleResult result = new ProcessedSaleResult();
        result.setDetalles(new ArrayList<>());
        Double totalAcumulado = 0.0;

        for (VentaRequest.ItemRequest itemReq : itemsReq) {
            Product producto = productRepository.findByIdIncludingInactive(itemReq.getProductoId()).orElseThrow(() -> new ResourceNotFoundException("Producto", itemReq.getProductoId()));
            if (!producto.isActivo()) throw new BusinessRuleException("El producto '" + producto.getDescripcion() + "' está eliminado y no puede ser incluido.");

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

        Double valorDescuento = itemReq.getValorDescuento() != null ? itemReq.getValorDescuento() : 0.0;
        Double precioFinal = calculateFinalPrice(precioBase, valorDescuento, producto.getDescripcion());

        detalle.setDescuentoValor(valorDescuento);
        detalle.setRazonDescuento(itemReq.getRazonDescuento());
        detalle.setPrecioUnitario(precioFinal);
        detalle.setSubtotal(itemReq.getCantidad() * precioFinal);

        return detalle;
    }

    private Double calculateFinalPrice(Double basePrice, Double value, String productName) {
        if (value < 0) throw new BusinessRuleException("El descuento no puede ser negativo para: " + productName);
        if (value > basePrice) throw new BusinessRuleException("El descuento no puede ser mayor al precio para: " + productName);
        return Math.round((basePrice - value) * 100.0) / 100.0;
    }

    private PersistedTransactionInfo saveTransactionData(VentaRequest request, ProcessedSaleResult processedData) {
        PersistedTransactionInfo info = new PersistedTransactionInfo();
        Long ventaId = persistVentaBase(request, processedData);
        info.setVentaId(ventaId);

        persistDetalles(ventaId, processedData.getDetalles());
        List<PagoVenta> pagosPersistidos = processPagosVenta(ventaId, request.getPagos(), request.getClienteId(), request.getUsuarioId());
        info.setPagosPersistidos(pagosPersistidos);

        return info;
    }

    private Long persistVentaBase(VentaRequest request, ProcessedSaleResult processedData) {
        Venta venta = new Venta();
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        venta.setFecha(now);
        venta.setFechaCreacion(now);
        venta.setClienteNombre(request.getClienteNombre());
        venta.setClienteId(request.getClienteId());
        venta.setTotalVenta(processedData.getTotalVenta());
        venta.setDescuentoGlobal(processedData.getDescuentoGlobal());
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
        MetodoPago saldoMethod = metodoPagoRepository.findByAcronimo(SALDO_ACRONIMO).orElse(null);
        Long saldoId = saldoMethod != null ? saldoMethod.getId() : null;
        List<PagoVenta> pagosEntities = new ArrayList<>();
        for (VentaRequest.PagoRequest p : pagos) {
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

    private void handleDebt(Long ventaId, String clienteNombre, Long clienteId, Double totalVenta, List<PagoVenta> pagosPersistidos) {
        Double totalPagado = pagosPersistidos.stream().mapToDouble(PagoVenta::getMonto).sum();
        Double deuda = totalVenta - totalPagado;
        if (deuda > PAYMENT_COMPLETE_EPSILON) {
            if (clienteNombre == null || clienteNombre.isBlank()) throw new BusinessRuleException("Para dejar una deuda (Fiado), se requiere el nombre del cliente.");
            deudoresRepository.save(ventaId, clienteNombre, clienteId, Math.round(deuda * 100.0) / 100.0);
        }
    }

    @Transactional
    public void anularVentaHistorica(Long ventaId) {
        Venta venta = ventaRepository.findById(ventaId).orElseThrow(() -> new ResourceNotFoundException("Venta", ventaId));
        if (ANULADA.equals(venta.getEstado())) throw new BusinessRuleException("La venta ya se encuentra anulada.");
        if (!ACTIVA.equals(venta.getEstado())) throw new BusinessRuleException("Solo se pueden anular ventas con estado ACTIVA.");

        ventaRepository.updateEstado(ventaId, ANULADA);

        List<DetalleVenta> detalles = ventaRepository.findDetallesByVentaId(ventaId);
        if (!detalles.isEmpty()) {
            List<com.centralizesys.model.product.Location> allLocations = stockRepository.findAllLocations();
            if (allLocations.isEmpty()) throw new BusinessRuleException("No hay ubicaciones configuradas para retornar el stock.");
            Long primaryLocationId = allLocations.getFirst().getId();
            for (DetalleVenta detalle : detalles) {
                Long yaDevuelta = devolucionesRepository.sumCantidadDevueltaByDetalleId(detalle.getId());
                Long netToReturn = detalle.getCantidad() - (yaDevuelta != null ? yaDevuelta : 0L);
                if (netToReturn > 0) {
                    stockRepository.addStock(detalle.getProductoId(), primaryLocationId, netToReturn);
                }
            }
        }

        deudoresRepository.findByVentaId(ventaId).ifPresent(deuda ->
                deudoresRepository.updateMontoAndEstado(deuda.getId(), deuda.getMontoDeuda(), ANULADA)
        );

        alertaChequeRepository.updateEstadoByVentaId(ventaId, ANULADA);

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
        Double descuentoGlobal = venta.getDescuentoGlobal() != null ? venta.getDescuentoGlobal() : 0.0;
        double subtotalVenta = venta.getTotalVenta() + descuentoGlobal;
        double discountProportion = (subtotalVenta > 0 && descuentoGlobal > 0) ? descuentoGlobal / subtotalVenta : 0.0;

        for (DevolucionRequest.DevolucionItemRequest item : request.getItems()) {
            totalReembolso += validateAndProcessSingleReturnedItem(ventaId, item, primaryLocationId, discountProportion, request, usuarioId);
        }
        return Math.round(totalReembolso * 100.0) / 100.0;
    }

    private double validateAndProcessSingleReturnedItem(Long ventaId, DevolucionRequest.DevolucionItemRequest item, Long primaryLocationId, double discountProportion, DevolucionRequest request, Long usuarioId) {
        DetalleVenta detalle = ventaRepository.findDetalleById(item.getDetalleVentaId())
                .orElseThrow(() -> new ResourceNotFoundException("DetalleVenta", item.getDetalleVentaId()));

        if (!ventaId.equals(detalle.getVentaId())) throw new BusinessRuleException("El detalle ID " + item.getDetalleVentaId() + " no pertenece a la venta " + ventaId + ".");
        if (Boolean.TRUE.equals(detalle.getAnulado())) throw new BusinessRuleException("El detalle ID " + item.getDetalleVentaId() + " ya fue anulado.");
        if (item.getCantidadDevuelta() == null || item.getCantidadDevuelta() <= 0) throw new BusinessRuleException("La cantidad a devolver debe ser mayor a 0.");

        Long yaDevuelta = devolucionesRepository.sumCantidadDevueltaByDetalleId(item.getDetalleVentaId());
        Long netRestante = detalle.getCantidad() - (yaDevuelta != null ? yaDevuelta : 0L);
        if (item.getCantidadDevuelta() > netRestante) {
            throw new BusinessRuleException(String.format("Solo quedan %d unidades devolvibles del producto '%s'.", netRestante, detalle.getDescripcionSnapshot()));
        }

        double montoItemBruto = Math.round((detalle.getPrecioUnitario() * item.getCantidadDevuelta()) * 100.0) / 100.0;
        double deductDiscount = Math.round((montoItemBruto * discountProportion) * 100.0) / 100.0;
        double montoItemNeto = Math.round((montoItemBruto - deductDiscount) * 100.0) / 100.0;

        devolucionesRepository.save(ventaId, item.getDetalleVentaId(), item.getCantidadDevuelta(), montoItemNeto, request.getTipoReembolso() != null ? request.getTipoReembolso() : "SALDO", request.getObservaciones(), usuarioId);
        stockRepository.addStock(detalle.getProductoId(), primaryLocationId, item.getCantidadDevuelta());

        return montoItemNeto;
    }


    private void applyRefund(Long ventaId, Venta venta, java.util.Optional<com.centralizesys.model.debt.DeudaResponse> deudaOpt, double totalReembolso, String tipoReembolso, Long usuarioId) {
        double remainingReembolso = totalReembolso;

        if (deudaOpt.isPresent()) {
            com.centralizesys.model.debt.DeudaResponse deuda = deudaOpt.get();
            double appliedToDebt = Math.min(deuda.getMontoDeuda(), remainingReembolso);

            double newDebt = Math.round((deuda.getMontoDeuda() - appliedToDebt) * 100.0) / 100.0;
            String newEstado = newDebt <= PAYMENT_COMPLETE_EPSILON ? "PAGADO" : "PARCIAL";

            int rowsAffected = deudoresRepository.deductDeudaAtomic(deuda.getId(), appliedToDebt, newEstado);
            if (rowsAffected == 0) throw new BusinessRuleException("Error de concurrencia al actualizar la deuda. Posible intento de deducción excesivo.");

            MetodoPago efectivoDummy = metodoPagoRepository.findByAcronimo("E").orElse(null);
            Long metodoId = efectivoDummy != null ? efectivoDummy.getId() : 1L;
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
            ventaRepository.updateEstado(ventaId, ANULADA);
            alertaChequeRepository.updateEstadoByVentaId(ventaId, ANULADA);
            auditoriaService.registrarAccion(usuarioId, "ANULAR_VENTA", "Venta " + ventaId + " anulada por devolución total.");
        } else {
            ventaRepository.updateEstado(ventaId, "DEVUELTA_PARCIAL");
            auditoriaService.registrarAccion(usuarioId, "DEVOLUCION_PARCIAL", "Devolución parcial registrada en venta " + ventaId);
        }
    }
}