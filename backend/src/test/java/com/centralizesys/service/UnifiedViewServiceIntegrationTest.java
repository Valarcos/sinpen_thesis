package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedViewServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UnifiedViewService unifiedViewService;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private DeudoresRepository deudoresRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Helper to create a complete testing scenario involving ventas, details, and debts.
     */
    private Long seedTestSaleWithDebt(Long userId, String clientName, double total, double activeCost, double annulledCost) {
        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.now());
        venta.setClienteNombre(clientName);
        venta.setTotalVenta(total);
        venta.setUsuarioId(userId);
        venta.setEstado("ACTIVA");
        Long ventaId = ventaRepository.saveVenta(venta);

        // Required: A product must exist to satisfy FK constraints if needed (depends on schema.sql)
        jdbcTemplate.update("""
            INSERT INTO productos (codigo, descripcion, precio_costo, precio_mayorista, precio_minorista, cantidad_stock, activo)
            VALUES ('TEST-UVS-1', 'Product 1', 10, 20, 30, 100, true)
            ON CONFLICT DO NOTHING
        """);

        Long productId = jdbcTemplate.queryForObject("SELECT id FROM productos WHERE codigo = 'TEST-UVS-1' LIMIT 1", Long.class);

        // Active Product (Not Annulled)
        jdbcTemplate.update("""
                INSERT INTO detalles_venta (venta_id, producto_id, descripcion_snapshot, codigo_snapshot, cantidad, precio_lista, precio_unitario, costo_snapshot, subtotal, anulado)
                VALUES (?, ?, 'Product Snapshot', 'COD-1', 1, ?, ?, ?, ?, false)
                """, ventaId, productId, total, total, activeCost, total);

        // Annulled Product (Must be excluded from Unified View)
        if (annulledCost > 0) {
            jdbcTemplate.update("""
                    INSERT INTO detalles_venta (venta_id, producto_id, descripcion_snapshot, codigo_snapshot, cantidad, precio_lista, precio_unitario, costo_snapshot, subtotal, anulado)
                    VALUES (?, ?, 'Annulled Snapshot', 'COD-2', 5, 0, 0, ?, 0, true)
                    """, ventaId, productId, annulledCost / 5); // Example math for quantity 5
        }

        // Register Debt so it appears in the Unified View under FIADO
        deudoresRepository.save(ventaId, clientName, null, total);

        return ventaId;
    }

    @Test
    @DisplayName("getCobrosYPedidos excludes annulled items from costo_total and cantidad_productos")
    void getCobrosYPedidos_ExcludesAnnulledItems() {
        // Arrange
        Long userId = createTestUser();
        Long ventaId = seedTestSaleWithDebt(userId, "Integration Client", 1000.0, 400.0, 300.0);

        // Act
        List<Map<String, Object>> cobrosYPedidos = unifiedViewService.getCobrosYPedidos();

        // Assert
        assertThat(cobrosYPedidos).isNotEmpty();

        // Find our seeded sale
        Map<String, Object> saleData = cobrosYPedidos.stream()
                .filter(row -> row.get("venta_id") != null && Long.valueOf(row.get("venta_id").toString()).equals(ventaId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Test sale not found in Unified View"));

        assertThat(saleData.get("tipo")).isEqualTo("FIADO");
        assertThat(Double.valueOf(saleData.get("monto_total").toString())).isEqualTo(1000.0);

        // Verify mathematically that ONLY the active cost (400.0) was captured,
        // completely ignoring the annulled details (300.0)
        assertThat(Double.valueOf(saleData.get("costo_total").toString())).isEqualTo(400.0);

        // Verify quantity ignores the annulled items (quantity of 5)
        assertThat(Long.valueOf(saleData.get("cantidad_productos").toString())).isEqualTo(1L);
    }
}
