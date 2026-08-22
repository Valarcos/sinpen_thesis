package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.PagoVenta;
import com.centralizesys.model.sales.Venta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VentaRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private VentaRepository ventaRepository;

    @Test
    @DisplayName("saveVenta - inserts header and returns generated ID")
    void saveVenta_insertsAndReturnsId() {
        // Arrange
        Long userId = createTestUser();
        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre("Test Client");
        venta.setTotalVenta(500.00);
        venta.setUsuarioId(userId);

        // Act
        Long id = ventaRepository.saveVenta(venta);

        // Assert
        assertThat(id).isNotNull().isPositive();
    }

    @Test
    @DisplayName("saveVenta - handles null clienteNombre for anonymous sales")
    void saveVenta_handlesNullClient() {
        // Arrange
        Long userId = createTestUser();
        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre(null);
        venta.setTotalVenta(100.00);
        venta.setUsuarioId(userId);

        // Act
        Long id = ventaRepository.saveVenta(venta);

        // Assert
        assertThat(id).isNotNull().isPositive();
    }

    @Test
    @DisplayName("saveDetalles - batch inserts with snapshot data")
    void saveDetalles_batchInsertsWithSnapshots() {
        // Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("VENTA-001", 150.0, 10L);

        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre("Snapshot Test");
        venta.setTotalVenta(300.00);
        venta.setUsuarioId(userId);
        Long ventaId = ventaRepository.saveVenta(venta);

        DetalleVenta det = new DetalleVenta();
        det.setVentaId(ventaId);
        det.setProductoId(productId);
        det.setCodigoSnapshot("VENTA-001");
        det.setDescripcionSnapshot("Test Product Description");
        det.setCostoSnapshot(100.0);
        det.setCantidad(2L);
        det.setPrecioLista(150.0);
        det.setDescuentoValor(0.0);
        det.setRazonDescuento("Test Reason 1");
        det.setPrecioUnitario(150.0);
        det.setSubtotal(300.0);

        // Act
        ventaRepository.saveDetalles(List.of(det));

        // Assert
        List<DetalleVenta> detalles = ventaRepository.findDetallesByVentaId(ventaId);
        assertThat(detalles).hasSize(1);
        assertThat(detalles.getFirst().getCodigoSnapshot()).isEqualTo("VENTA-001");
        assertThat(detalles.getFirst().getDescripcionSnapshot()).isEqualTo("Test Product Description");
        assertThat(detalles.getFirst().getCostoSnapshot()).isEqualTo(100.0);
        assertThat(detalles.getFirst().getRazonDescuento()).isEqualTo("Test Reason 1");
    }

    @Test
    @DisplayName("savePagos - batch inserts payment records")
    void savePagos_batchInsertsPayments() {
        // Arrange
        Long userId = createTestUser();
        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre("Payment Test");
        venta.setTotalVenta(1000.00);
        venta.setUsuarioId(userId);
        Long ventaId = ventaRepository.saveVenta(venta);

        // Assuming methodId 1 = Efectivo (from schema.sql seeding)
        PagoVenta pago1 = new PagoVenta();
        pago1.setVentaId(ventaId);
        pago1.setMetodoPagoId(1L);
        pago1.setMonto(600.00);

        PagoVenta pago2 = new PagoVenta();
        pago2.setVentaId(ventaId);
        pago2.setMetodoPagoId(1L);
        pago2.setMonto(400.00);

        // Act
        ventaRepository.savePagos(List.of(pago1, pago2));

        // Assert
        List<PagoVenta> pagos = ventaRepository.findPagosByVentaId(ventaId);
        assertThat(pagos).hasSize(2);
        double totalPaid = pagos.stream().mapToDouble(PagoVenta::getMonto).sum();
        assertThat(totalPaid).isEqualTo(1000.00);
    }

    @Test
    @DisplayName("savePagos - handles empty list gracefully")
    void savePagos_handlesEmptyList() {
        // Assert - should not throw for empty list
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> ventaRepository.savePagos(List.of()));
    }

    @Test
    @DisplayName("findById - returns Optional with venta")
    void findById_returnsVenta() {
        // Arrange
        Long userId = createTestUser();
        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre("Find Test");
        venta.setTotalVenta(250.00);
        venta.setUsuarioId(userId);
        Long id = ventaRepository.saveVenta(venta);

        // Act
        Optional<Venta> found = ventaRepository.findById(id);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getClienteNombre()).isEqualTo("Find Test");
        assertThat(found.get().getTotalVenta()).isEqualTo(250.00);
        // Ensure 0 is mapped correctly when there are no items
        assertThat(found.get().getCantidadProductos()).isZero();
    }

    @Test
    @DisplayName("findById - maps cantidad_productos correctly when items exist")
    void findById_mapsCantidadProductos() {
        // Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("MAP-002", 50.0, 5L);

        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre("Quantity Test");
        venta.setTotalVenta(90.00);
        venta.setUsuarioId(userId);
        Long ventaId = ventaRepository.saveVenta(venta);

        DetalleVenta det = new DetalleVenta();
        det.setVentaId(ventaId);
        det.setProductoId(productId);
        det.setCodigoSnapshot("MAP-002");
        det.setDescripcionSnapshot("Mapping Test");
        det.setCostoSnapshot(30.0);
        det.setCantidad(2L); // 2 items
        det.setPrecioLista(50.0);
        det.setDescuentoValor(5.0);
        det.setPrecioUnitario(45.0);
        det.setSubtotal(90.0);
        ventaRepository.saveDetalles(List.of(det));

        // Act
        Optional<Venta> found = ventaRepository.findById(ventaId);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getCantidadProductos()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findById - returns empty Optional for non-existent ID")
    void findById_returnsEmptyForMissing() {
        // Act
        Optional<Venta> found = ventaRepository.findById(99999L);

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findAll - returns ventas ordered by fecha DESC, id DESC")
    void findAll_returnsOrderedDescending() {
        // Arrange
        Long userId = createTestUser();

        Venta v1 = new Venta();
        v1.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        v1.setClienteNombre("First");
        v1.setTotalVenta(100.00);
        v1.setUsuarioId(userId);
        ventaRepository.saveVenta(v1);

        Venta v2 = new Venta();
        v2.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        v2.setClienteNombre("Second");
        v2.setTotalVenta(200.00);
        v2.setUsuarioId(userId);
        ventaRepository.saveVenta(v2);

        Venta v3 = new Venta();
        v3.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        v3.setClienteNombre("Pending Client");
        v3.setTotalVenta(300.00);
        v3.setEstado("PENDIENTE");
        v3.setUsuarioId(userId);
        ventaRepository.saveVenta(v3);

        // Act
        List<Venta> all = ventaRepository.findAll();

        // Assert - Second inserted should come first (higher ID, same date). Pending sale must be excluded!
        assertThat(all).hasSize(2);
        assertThat(all.getFirst().getClienteNombre()).isEqualTo("Second");
    }

    @Test
    @DisplayName("findDetallesByVentaId - maps all fields correctly")
    void findDetallesByVentaId_mapsAllFields() {
        // Arrange
        Long userId = createTestUser();
        Long productId = createTestProduct("MAP-001", 50.0, 5L);

        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre("Map Test");
        venta.setTotalVenta(45.00);
        venta.setUsuarioId(userId);
        Long ventaId = ventaRepository.saveVenta(venta);

        DetalleVenta det = new DetalleVenta();
        det.setVentaId(ventaId);
        det.setProductoId(productId);
        det.setCodigoSnapshot("MAP-001");
        det.setDescripcionSnapshot("Mapping Test");
        det.setCostoSnapshot(30.0);
        det.setCantidad(1L);
        det.setPrecioLista(50.0);
        det.setDescuentoValor(5.0);
        det.setRazonDescuento("Map Test Reason");
        det.setPrecioUnitario(45.0);
        det.setSubtotal(45.0);
        ventaRepository.saveDetalles(List.of(det));

        // Act
        List<DetalleVenta> detalles = ventaRepository.findDetallesByVentaId(ventaId);

        // Assert - All fields mapped
        assertThat(detalles).hasSize(1);
        DetalleVenta found = detalles.getFirst();
        assertThat(found.getVentaId()).isEqualTo(ventaId);
        assertThat(found.getProductoId()).isEqualTo(productId);
        assertThat(found.getCantidad()).isEqualTo(1L);
        assertThat(found.getCostoSnapshot()).isEqualTo(30.0);
        assertThat(found.getPrecioLista()).isEqualTo(50.0);
        assertThat(found.getDescuentoValor()).isEqualTo(5.0);
        assertThat(found.getRazonDescuento()).isEqualTo("Map Test Reason");
        assertThat(found.getPrecioUnitario()).isEqualTo(45.0);
        assertThat(found.getSubtotal()).isEqualTo(45.0);
    }

    @Test
    @DisplayName("findVentasByFechaBetween - returns paginated results in range")
    void findVentasByFechaBetween_returnsPaginatedResults() {
        // Arrange
        Long userId = createTestUser();
        // Insert 5 sales on different dates
        createVenta(userId, LocalDateTime.parse("2023-01-01T00:00:00"), "C1", 150.0);
        createVenta(userId, LocalDateTime.parse("2023-01-02T00:00:00"), "C2", 200.0);
        createVenta(userId, LocalDateTime.parse("2023-01-03T00:00:00"), "C3", 250.0); // Target
        createVenta(userId, LocalDateTime.parse("2023-01-04T00:00:00"), "C4", 300.0); // Target
        createVenta(userId, LocalDateTime.parse("2023-01-05T00:00:00"), "C5", 350.0);

        // Act - Request page 0, size 1, range 2023-01-03 to 2023-01-04
        // Should order by date DESC, so C4 then C3
        List<Venta> results = ventaRepository.findVentasByFechaBetween(LocalDateTime.parse("2023-01-03T00:00:00"), LocalDateTime.parse("2023-01-04T00:00:00"), null, 1, 0);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getClienteNombre()).isEqualTo("C4");
    }

    @Test
    @DisplayName("countVentasByFechaBetween - returns correct count")
    void countVentasByFechaBetween_returnsCount() {
        // Arrange
        Long userId = createTestUser();
        createVenta(userId, LocalDateTime.parse("2023-01-01T00:00:00"), "C1", 100.0);
        createVenta(userId, LocalDateTime.parse("2023-01-02T00:00:00"), "C2", 200.0); // Target
        createVenta(userId, LocalDateTime.parse("2023-01-03T00:00:00"), "C3", 300.0); // Target
        createVenta(userId, LocalDateTime.parse("2023-01-04T00:00:00"), "C4", 400.0);

        // Insert a pending sale in the target range (must be ignored)
        Venta pending = new Venta();
        pending.setFecha(LocalDateTime.parse("2023-01-02T12:00:00"));
        pending.setClienteNombre("Pending in range");
        pending.setTotalVenta(500.0);
        pending.setEstado("PENDIENTE");
        pending.setUsuarioId(userId);
        ventaRepository.saveVenta(pending);

        // Act
        long count = ventaRepository.countVentasByFechaBetween(LocalDateTime.parse("2023-01-02T00:00:00"), LocalDateTime.parse("2023-01-03T00:00:00"), null);

        // Assert - Should only count the 2 ACTIVA sales
        assertThat(count).isEqualTo(2);
    }

    private void createVenta(Long userId, LocalDateTime date, String client, Double total) {
        Venta v = new Venta();
        v.setFecha(date);
        v.setClienteNombre(client);
        v.setTotalVenta(total);
        v.setUsuarioId(userId);
        ventaRepository.saveVenta(v);
    }

    @Test
    @DisplayName("updatePendingSaleHeader - Updates all required fields")
    void updatePendingSaleHeader_UpdatesAllFields() {
        // Arrange
        Long userId = createTestUser();
        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre("Old Client");
        venta.setTotalVenta(100.00);
        venta.setDescuentoGlobal(0.0);
        venta.setRecargoGlobal(0.0);
        venta.setTipoVenta("MINORISTA");
        venta.setEstado("PENDIENTE");
        venta.setUsuarioId(userId);
        Long id = ventaRepository.saveVenta(venta);
        // Insert a client to satisfy foreign key constraint on update
        jdbcTemplate.update("INSERT INTO clientes (id, nombre, activo, saldo_a_favor) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING", 99L, "New Client", true, 0.0);

        // Act
        ventaRepository.updatePendingSaleHeader(id, 200.0, 10.0, 5.0, 0.0, 99L, "New Client", "MAYORISTA");

        // Assert
        Optional<Venta> updatedOpt = ventaRepository.findById(id);
        assertThat(updatedOpt).isPresent();
        Venta updated = updatedOpt.get();
        assertThat(updated.getTotalVenta()).isEqualTo(200.0);
        assertThat(updated.getDescuentoGlobal()).isEqualTo(10.0);
        assertThat(updated.getRecargoGlobal()).isEqualTo(5.0);
        assertThat(updated.getClienteId()).isEqualTo(99L);
        assertThat(updated.getClienteNombre()).isEqualTo("New Client");
        assertThat(updated.getTipoVenta()).isEqualTo("MAYORISTA");
    }
}
