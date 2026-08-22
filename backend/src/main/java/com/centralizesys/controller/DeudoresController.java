package com.centralizesys.controller;

import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeuda;
import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.model.dto.PageResponse;
import com.centralizesys.service.DeudoresService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deudores")
public class DeudoresController {

    private final DeudoresService service;

    public DeudoresController(DeudoresService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<DeudaResponse>> getPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        return ResponseEntity.ok(service.getPage(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeudaResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/venta/{ventaId}")
    public ResponseEntity<DeudaResponse> getByVentaId(@PathVariable Long ventaId) {
        return ResponseEntity.ok(service.getByVentaId(ventaId));
    }

    @PostMapping("/{id}/pagar")
    public ResponseEntity<DeudaResponse> pagarDeuda(
            @PathVariable Long id,
            @RequestBody List<PagoDeudaRequest> pagos) {

        // Use SecurityContext to identify user
        Long usuarioId = com.centralizesys.security.SecurityUtils.getAuthenticatedUserId();

        // Pass the extracted ID to the Service
        DeudaResponse updated = service.registrarPago(
                id,
                pagos,
                usuarioId);

        return ResponseEntity.ok(updated);
    }

    // GET /api/deudores/reminder
    // Returns true if active debts exist (for 15-day reminder badge)
    @GetMapping("/reminder")
    public ResponseEntity<Boolean> hasActiveDebtsReminder() {
        return ResponseEntity.ok(service.hasActiveDebts());
    }

    @GetMapping("/expired")
    public ResponseEntity<List<DeudaResponse>> getExpiredDebts() {
        return ResponseEntity.ok(service.getExpiredDebts());
    }

    @GetMapping("/{id}/pagos")
    public ResponseEntity<List<PagoDeuda>> getPagos(@PathVariable Long id) {
        return ResponseEntity.ok(service.getPagos(id));
    }

    @PostMapping("/pagos/{pagoId}/cancelar")
    public ResponseEntity<Void> cancelarPago(@PathVariable Long pagoId) {
        service.anularPago(pagoId);
        return ResponseEntity.ok().build();
    }
}