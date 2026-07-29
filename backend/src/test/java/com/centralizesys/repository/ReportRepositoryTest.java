package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.sales.ReportesEstadisticasDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("getEstadisticas - correctly subtracts active Gastos Varios from Egresos Efectivo")
    void getEstadisticas_includesActiveGastosInCashOut() {
        // Arrange
        int year = 2026;
        int month = 10;
        int day = 15;

        // Ensure clean state for this day
        jdbcTemplate.update("DELETE FROM compras");
        jdbcTemplate.update("DELETE FROM gastos_caja");

        // 1. Add a purchase (Compra) for $1000 on that day
        jdbcTemplate.update("""
            INSERT INTO compras (proveedor, total_compra, fecha, nro_comprobante, usuario_id) 
            VALUES ('TEST_PROV', 1000.0, '2026-10-15 10:00:00', 'TEST', 1)
        """);

        // 2. Add an active Gasto Vario for $500 on that day
        jdbcTemplate.update("""
            INSERT INTO gastos_caja (monto, motivo, fecha_gasto, fecha_registro, persona_involucrada, registrado_por_usuario_id, categoria, anulado)
            VALUES (500.0, 'Luz', '2026-10-15 11:00:00', '2026-10-15 11:00:00', 'Admin', 1, 'Servicios', false)
        """);

        // 3. Add a VOIDED (anulado=true) Gasto Vario for $300 on that day
        jdbcTemplate.update("""
            INSERT INTO gastos_caja (monto, motivo, fecha_gasto, fecha_registro, persona_involucrada, registrado_por_usuario_id, categoria, anulado)
            VALUES (300.0, 'Agua', '2026-10-15 12:00:00', '2026-10-15 12:00:00', 'Admin', 1, 'Servicios', true)
        """);

        // Act
        ReportesEstadisticasDTO dto = reportRepository.getEstadisticas(year, month, day);

        // Assert
        ReportesEstadisticasDTO.FlujoDeCaja fc = dto.getFlujoDeCaja();
        // Egresos (compras) should be 1000
        // Gastos Varios should be 500 (active gasto only)
        // The 300 voided gasto must NOT be included!
        assertThat(fc.getEgresosEfectivo()).isEqualTo(1000.0);
        assertThat(fc.getGastosVariosEfectivo()).isEqualTo(500.0);
        assertThat(fc.getBalanceNeto()).isEqualTo(-1500.0);
    }

    @Test
    @DisplayName("getEstadisticas - ventasPendientes sums PENDIENTE orders in the period without overlap with finalized sales")
    void getEstadisticas_ventasPendientes_noOverlapWithFinalizedSales() {
        // Arrange
        int year = 2026;
        int month = 11;
        int day = 5;

        // Clean up relevant data for this isolated date
        jdbcTemplate.update("DELETE FROM ventas WHERE fecha::date = '2026-11-05' OR fecha_creacion::date = '2026-11-05'");

        // Insert 1 finalized venta for $500 on 2026-11-05
        jdbcTemplate.update("""
            INSERT INTO ventas (fecha, total_venta, estado)
            VALUES ('2026-11-05 10:00:00', 500.0, 'ACTIVA')
        """);

        // Insert 1 PENDIENTE venta for $300 on 2026-11-05
        jdbcTemplate.update("""
            INSERT INTO ventas (fecha, fecha_creacion, cliente_nombre, total_venta, estado)
            VALUES ('2026-11-05 11:00:00', '2026-11-05 11:00:00', 'Cliente Test', 300.0, 'PENDIENTE')
        """);

        // Insert 1 FINALIZADA venta_pendiente is equivalent to an ACTIVA venta created in the past but finalized now
        jdbcTemplate.update("""
            INSERT INTO ventas (fecha, fecha_creacion, cliente_nombre, total_venta, estado)
            VALUES ('2026-11-05 12:00:00', '2026-11-05 10:00:00', 'Cliente Test 2', 200.0, 'ACTIVA')
        """);

        // Act
        ReportesEstadisticasDTO dto = reportRepository.getEstadisticas(year, month, day);
        ReportesEstadisticasDTO.RendimientoComercial rc = dto.getRendimientoComercial();

        // Assert — ingresos (finalized) now includes both ACTIVA sales (500 + 200)
        assertThat(rc.getIngresosVentas()).isEqualTo(700.0);

        // ventasPendientes must include ONLY the PENDIENTE order ($300), NOT the ACTIVA one
        assertThat(rc.getVentasPendientes()).isEqualTo(300.0);

        // ventasTotalesProyectadas = 700 + 300 = 1000
        assertThat(rc.getVentasTotalesProyectadas()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("getEstadisticas - ventasPendientes is zero when no pending orders exist in the period")
    void getEstadisticas_ventasPendientes_zeroWhenNoPendingOrders() {
        // Year 1990 will have no pending orders
        ReportesEstadisticasDTO dto = reportRepository.getEstadisticas(1990, 1, null);

        ReportesEstadisticasDTO.RendimientoComercial rc = dto.getRendimientoComercial();
        assertThat(rc.getVentasPendientes()).isEqualTo(0.0);
        assertThat(rc.getVentasTotalesProyectadas()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getEstadisticas - payments on pending sales are deducted from ventasPendientes and added to ingresosVentas")
    void getEstadisticas_ventasPendientes_deductsPaymentsAndAddsToIngresos() {
        // Arrange
        int year = 2026;
        int month = 12;
        int day = 10;

        jdbcTemplate.update("DELETE FROM ventas WHERE fecha::date = '2026-12-10' OR fecha_creacion::date = '2026-12-10'");
        jdbcTemplate.update("DELETE FROM pagos_venta");
        jdbcTemplate.update("DELETE FROM alertas_cheques");

        // 1 Active Sale for $1000
        jdbcTemplate.update("""
            INSERT INTO ventas (fecha, fecha_creacion, cliente_nombre, total_venta, estado)
            VALUES ('2026-12-10 10:00:00', '2026-12-10 10:00:00', 'Active Client', 1000.0, 'ACTIVA')
        """);

        // 1 Pending Sale for $500
        // We need to fetch its ID to associate payments.
        jdbcTemplate.update("""
            INSERT INTO ventas (id, fecha, fecha_creacion, cliente_nombre, total_venta, estado)
            VALUES (9999, '2026-12-10 11:00:00', '2026-12-10 11:00:00', 'Pending Client', 500.0, 'PENDIENTE')
        """);

        // A $100 cash payment for the pending sale
        jdbcTemplate.update("""
            INSERT INTO pagos_venta (venta_id, metodo_pago_id, monto, fecha_pago, anulado)
            VALUES (9999, 1, 100.0, '2026-12-10 11:30:00', false)
        """);

        // A $50 pending cheque for the pending sale
        jdbcTemplate.update("""
            INSERT INTO alertas_cheques (venta_id, monto, fecha_cobro, estado)
            VALUES (9999, 50.0, '2026-12-20', 'PENDIENTE')
        """);

        // Act
        ReportesEstadisticasDTO dto = reportRepository.getEstadisticas(year, month, day);
        ReportesEstadisticasDTO.RendimientoComercial rc = dto.getRendimientoComercial();

        // Assert
        // ingresos_ventas should be 1000 (active) + 150 (paid towards pending) = 1150
        assertThat(rc.getIngresosVentas()).isEqualTo(1150.0);

        // ventasPendientes should be 500 (total) - 150 (paid) = 350
        assertThat(rc.getVentasPendientes()).isEqualTo(350.0);
    }
}
