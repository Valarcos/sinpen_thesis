package com.centralizesys.controller;

import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.model.sales.MetodoPago;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.repository.MetodoPagoRepository;
import com.centralizesys.security.SecurityUtils;
import com.centralizesys.service.VentaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ventas")
public class VentaController {

    private final VentaService ventaService;
    private final MetodoPagoRepository metodoPagoRepository;

    @Autowired
    public VentaController(VentaService ventaService, MetodoPagoRepository metodoPagoRepository) {
        this.ventaService = ventaService;
        this.metodoPagoRepository = metodoPagoRepository;
    }

    // --- TRANSACTIONS ---

    @PostMapping
    public ResponseEntity<VentaResponse> registrarVenta(@RequestBody VentaRequest request) {
        request.setUsuarioId(SecurityUtils.getAuthenticatedUserId());
        VentaResponse response = ventaService.registrarVenta(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/pendientes")
    public ResponseEntity<Long> crearPendiente(@RequestBody VentaRequest request) {
        Long authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        Long id = ventaService.crearPendiente(request, authenticatedUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @PostMapping("/{id}/pagos")
    public ResponseEntity<Void> registrarPago(
            @PathVariable Long id,
            @RequestBody List<PagoDeudaRequest> pagos) {
        Long authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        ventaService.registrarPago(id, pagos, authenticatedUserId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/finalizar")
    public ResponseEntity<VentaResponse> finalizarVenta(@PathVariable Long id) {
        Long authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        VentaResponse response = ventaService.finalizarVenta(id, authenticatedUserId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarPendiente(@PathVariable Long id) {
        Long authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        ventaService.cancelarPendiente(id, authenticatedUserId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<VentaResponse> modificarCarrito(
            @PathVariable Long id,
            @RequestBody VentaRequest request) {
        Long authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        VentaResponse response = ventaService.modificarCarrito(id, request, authenticatedUserId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/pagos/{pagoId}")
    public ResponseEntity<Void> anularPago(
            @PathVariable Long id,
            @PathVariable Long pagoId) {
        Long authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        ventaService.anularPago(id, pagoId, authenticatedUserId);
        return ResponseEntity.noContent().build();
    }

    // --- QUERIES ---

    @GetMapping
    public ResponseEntity<com.centralizesys.model.dto.PageResponse<Venta>> getAll(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long searchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ventaService.getVentasPage(startDate, endDate, searchId, page, size));
    }

    @GetMapping("/pendientes")
    public ResponseEntity<com.centralizesys.model.dto.PageResponse<Venta>> getAllPendientes(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ventaService.getVentasPendientesPage(startDate, endDate, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VentaResponse> getById(@PathVariable Long id) {
        VentaResponse response = ventaService.getVentaById(id);
        return ResponseEntity.ok(response);
    }

    // For backwards compatibility or specific frontend requests
    @GetMapping("/pendientes/{id}")
    public ResponseEntity<VentaResponse> getPendienteById(@PathVariable Long id) {
        return getById(id);
    }

    @GetMapping("/{id}/pagos")
    public ResponseEntity<List<com.centralizesys.model.sales.PagoVenta>> getPagos(@PathVariable Long id) {
        // Find by venta ID instead of pending sale ID
        // (Wait, I didn't add findPagosActivosByVentaId directly to service, it's just available in VentaResponse.
        // Oh, wait, the response already contains all active payments. But if they strictly want this endpoint...)
        VentaResponse venta = ventaService.getVentaById(id);
        return ResponseEntity.ok(venta.getPagos());
    }

    @PostMapping("/{id}/anular")
    public ResponseEntity<Void> anularVenta(@PathVariable Long id) {
        ventaService.anularVentaHistorica(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/devolucion-parcial")
    public ResponseEntity<Void> registrarDevolucionParcial(
            @PathVariable Long id,
            @RequestBody com.centralizesys.model.returns.DevolucionRequest request) {
        Long authenticatedUserId = SecurityUtils.getAuthenticatedUserId();
        request.setUsuarioId(authenticatedUserId);
        ventaService.registrarDevolucionParcial(id, request, authenticatedUserId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // --- CONFIG / UTILS ---

    @GetMapping("/metodos-pago")
    public ResponseEntity<List<MetodoPago>> getMetodosPago() {
        return ResponseEntity.ok(metodoPagoRepository.findAll());
    }

    @GetMapping("/clientes")
    public ResponseEntity<List<String>> getClientes() {
        return ResponseEntity.ok(ventaService.getClientes());
    }
}