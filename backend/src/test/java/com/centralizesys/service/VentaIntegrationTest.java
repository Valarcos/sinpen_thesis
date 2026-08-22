package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.sales.*;
import com.centralizesys.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VentaIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private VentaRepository ventaRepository;

    @Test
    @DisplayName("Should execute full sale flow: Stock update, Debt creation, and Audit logging")
    void shouldExecuteFullSaleFlow() {
        // 1. Setup Data using Helper
        Long userId = createTestUser();
        Long prodId = createTestProduct("FLOW-001", 100.0, 10L);

        // 2. Prepare Request (1 Item, Partial Payment)
        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Flow Client");
        request.setUsuarioId(userId);
        request.setTipoVenta(TipoVenta.MINORISTA);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(prodId);
        item.setCantidad(2L); // Total $200
        request.setItems(List.of(item));

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMetodoPagoId(1L); // Assumes ID 1 exists (Efectivo)
        pago.setMonto(150.0); // Debt of $50
        request.setPagos(List.of(pago));

        // 3. Execute
        VentaResponse response = ventaService.registrarVenta(request);

        // 4. Verify
        assertNotNull(response.getId());
        assertEquals(200.0, response.getTotalVenta());

        // Verify Debt (Implicitly via Service logic, but we can query DB to be sure)
        Double debt = jdbcTemplate.queryForObject(
                "SELECT monto_deuda FROM deudores WHERE venta_id = ?", Double.class, response.getId());
        assertEquals(50.0, debt);
    }

    @Test
    @DisplayName("Should persist Global Surcharge correctly")
    void shouldPersistGlobalSurcharge() {
        Long userId = createTestUser();
        Long prodId = createTestProduct("SUR-001", 100.0, 10L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Surcharge Integration Client");
        request.setUsuarioId(userId);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(prodId);
        item.setCantidad(2L); // 200
        request.setItems(List.of(item));
        request.setRecargoGlobal(30.0); // 230

        // Full payment
        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMetodoPagoId(1L);
        pago.setMonto(230.0);
        request.setPagos(List.of(pago));

        VentaResponse response = ventaService.registrarVenta(request);

        assertEquals(230.0, response.getTotalVenta());
        assertEquals(30.0, response.getRecargoGlobal());

        // Verify direct DB persistence
        Double dbSurcharge = jdbcTemplate.queryForObject("SELECT recargo_global FROM ventas WHERE id = ?", Double.class, response.getId());
        assertEquals(30.0, dbSurcharge);
    }

    @Test
    @DisplayName("Should persist Global Discount correctly")
    void shouldPersistGlobalDiscount() {
        // 1. Setup Data
        Long userId = createTestUser();
        Long prodId = createTestProduct("DISC-001", 100.0, 10L);

        // 2. Prepare Request with Discount
        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Discount Client");
        request.setUsuarioId(userId);
        request.setDescuentoGlobal(20.0); // Discount

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(prodId);
        item.setCantidad(1L); // Total $100
        request.setItems(List.of(item));
        request.setPagos(List.of()); // No payment -> Full Debt (doesn't matter for this test)

        // 3. Execute
        VentaResponse response = ventaService.registrarVenta(request);

        // 4. Verify Immediate Response
        assertEquals(80.0, response.getTotalVenta()); // 100 - 20
        assertEquals(20.0, response.getDescuentoGlobal());

        // 5. Verify Persistence
        Venta ventaDb = ventaRepository.findById(response.getId()).orElseThrow();
        assertEquals(80.0, ventaDb.getTotalVenta());
        assertEquals(20.0, ventaDb.getDescuentoGlobal());
    }
}
