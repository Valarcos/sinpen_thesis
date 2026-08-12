package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeuda;
import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.model.enums.DebtStatus;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.VentaRepository;

import java.time.LocalDateTime;
import java.util.List;

class DeudoresServiceVoidingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DeudoresService deudoresService;

    @Autowired
    private DeudoresRepository deudoresRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Test
    void shouldRestoreBalanceAndStatusWhenPaymentIsVoided() {
        // 1. Arrange: Setup Data
        Long userId = createTestUser();

        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre("Voiding Debtor");
        venta.setTotalVenta(100.0);
        venta.setUsuarioId(userId);
        Long ventaId = ventaRepository.saveVenta(venta);

        // Create Debt of $100.00
        deudoresRepository.save(ventaId, "Voiding Debtor", null, 100.00);

        DeudaResponse initialDebt = deudoresRepository.findAll().stream()
                .filter(d -> d.getVentaId().equals(ventaId))
                .findFirst()
                .orElseThrow();
        Long deudaId = initialDebt.getId();

        // Pay $40.00
        PagoDeudaRequest p1 = new PagoDeudaRequest();
        p1.setMontoPago(40.00);
        p1.setMetodoPagoId(1L);
        p1.setObservaciones("Payment to be voided");

        DeudaResponse partialDebt = deudoresService.registrarPago(deudaId, List.of(p1), userId);
        assertEquals(60.00, partialDebt.getMontoDeuda(), 0.001);
        assertEquals(DebtStatus.PARCIAL.name(), partialDebt.getEstado());

        // Get the payment ID
        List<PagoDeuda> pagos = deudoresService.getPagos(deudaId);
        assertEquals(1, pagos.size());
        Long pagoId = pagos.getFirst().getId();

        // 2. Act: Void the payment
        deudoresService.anularPago(pagoId);

        // 3. Assert
        DeudaResponse restoredDebt = deudoresRepository.findById(deudaId).orElseThrow();

        // Balance is restored to 100.00
        assertEquals(100.00, restoredDebt.getMontoDeuda(), 0.001);
        // Status reverts back to PENDIENTE
        assertEquals(DebtStatus.PENDIENTE.name(), restoredDebt.getEstado());
    }
}
