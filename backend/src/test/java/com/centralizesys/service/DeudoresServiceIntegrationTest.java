package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeudaRequest; // NEW
import com.centralizesys.model.enums.DebtStatus;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DeudoresServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DeudoresService deudoresService;

    @Autowired
    private DeudoresRepository deudoresRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Test
    @DisplayName("Should handle partial and full payments with correct Double precision")
    void shouldHandlePaymentsRefactor() {
        // 1. Setup Data
        Long userId = createTestUser();

        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre("Test Debtor");
        venta.setTotalVenta(100.0);
        venta.setUsuarioId(userId);
        Long ventaId = ventaRepository.saveVenta(venta);

        // Create Debt of $100.50
        deudoresRepository.save(ventaId, "Test Debtor", null, 100.50);

        // Retrieve ID of the created debt
        DeudaResponse initialDebt = deudoresRepository.findAll().stream()
                .filter(d -> d.getVentaId().equals(ventaId))
                .findFirst()
                .orElseThrow();
        Long deudaId = initialDebt.getId();

        assertEquals(DebtStatus.PENDIENTE.name(), initialDebt.getEstado());

        // 2. Partial Payment ($50.20)
        PagoDeudaRequest p1 = new PagoDeudaRequest();
        p1.setMontoPago(50.20);
        p1.setMetodoPagoId(1L);
        p1.setObservaciones("Test Partial");
        DeudaResponse partial = deudoresService.registrarPago(deudaId, java.util.List.of(p1), userId);

        assertEquals(50.30, partial.getMontoDeuda(), 0.001, "Balance should be 50.30");
        assertEquals(DebtStatus.PARCIAL.name(), partial.getEstado());

        // 3. Full Payment ($50.30)
        PagoDeudaRequest p2 = new PagoDeudaRequest();
        p2.setMontoPago(50.30);
        p2.setMetodoPagoId(1L);
        p2.setObservaciones("Test Full");
        DeudaResponse full = deudoresService.registrarPago(deudaId, java.util.List.of(p2), userId);

        assertEquals(0.00, full.getMontoDeuda(), 0.001);
        assertEquals(DebtStatus.PAGADO.name(), full.getEstado());
    }

    @Test
    @DisplayName("Should handle tiny rounding issues gracefully")
    void shouldHandleRounding() {
        // Scenario: 10.00 debt. Payment of 3.33 repeated 3 times.
        Long userId = createTestUser();
        Venta venta = new Venta(null, LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0), LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0), "Math User", null, 10.0, 0.0, "MINORISTA", userId, "ACTIVA", 0.0, 0L);
        Long ventaId = ventaRepository.saveVenta(venta);

        deudoresRepository.save(ventaId, "Math User", null, 10.00);
        Long deudaId = deudoresRepository.findAll().getFirst().getId();

        // Helper
        PagoDeudaRequest p = new PagoDeudaRequest();
        p.setMetodoPagoId(1L);
        p.setObservaciones("Round");

        // Pay 1: 3.33
        p.setMontoPago(3.33);
        DeudaResponse r1 = deudoresService.registrarPago(deudaId, java.util.List.of(p), userId);
        assertEquals(6.67, r1.getMontoDeuda());

        // Pay 2: 3.33
        // Reuse object? Better new one to avoid side effects if service modifies it (it
        // shouldn't)
        PagoDeudaRequest p2 = new PagoDeudaRequest();
        p2.setMetodoPagoId(1L);
        p2.setMontoPago(3.33);
        DeudaResponse r2 = deudoresService.registrarPago(deudaId, java.util.List.of(p2), userId);
        assertEquals(3.34, r2.getMontoDeuda());

        // Pay 3: 3.34 (Clean finish)
        PagoDeudaRequest p3 = new PagoDeudaRequest();
        p3.setMetodoPagoId(1L);
        p3.setMontoPago(3.34);
        DeudaResponse r3 = deudoresService.registrarPago(deudaId, java.util.List.of(p3), userId);
        assertEquals(0.00, r3.getMontoDeuda());
        assertEquals(DebtStatus.PAGADO.name(), r3.getEstado());
    }

    @Test
    @DisplayName("Should record payment in pagos_deuda history")
    void shouldRecordPaymentInHistory() {
        Long userId = createTestUser();
        Venta venta = new Venta(null, LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0), LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0), "History User", null, 100.0, 0.0, "MINORISTA", userId, "ACTIVA", 0.0, 0L);
        Long ventaId = ventaRepository.saveVenta(venta);

        deudoresRepository.save(ventaId, "History User", null, 100.0);
        Long deudaId = deudoresRepository.findAll().stream()
                .filter(d -> d.getVentaId().equals(ventaId))
                .findFirst()
                .orElseThrow().getId();

        // Act
        PagoDeudaRequest p = new PagoDeudaRequest();
        p.setMontoPago(20.0);
        p.setMetodoPagoId(1L);
        p.setObservaciones("First Installment");
        deudoresService.registrarPago(deudaId, java.util.List.of(p), userId);

        // Assert History
        var pagos = deudoresService.getPagos(deudaId);
        assertEquals(1, pagos.size());
        assertEquals(20.0, pagos.getFirst().getMonto());
        assertEquals(1L, pagos.getFirst().getMetodoPagoId());
        assertEquals("First Installment", pagos.getFirst().getObservaciones());
        assertNotNull(pagos.getFirst().getUsuarioNombre());
        assertEquals("Efectivo", pagos.getFirst().getMetodoPagoNombre()); // Assuming 1=Efectivo
    }
}
