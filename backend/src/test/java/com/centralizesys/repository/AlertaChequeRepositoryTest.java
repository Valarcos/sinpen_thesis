package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.cheque.AlertaCheque;
import com.centralizesys.model.sales.Venta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AlertaChequeRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private AlertaChequeRepository alertaChequeRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Helper to create a sale required for FK constraint on cheques.
     */
    private Long createTestSale() {
        Long userId = createTestUser();
        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.now());
        venta.setClienteNombre("Cheque Test Client");
        venta.setTotalVenta(5000.00);
        venta.setUsuarioId(userId);
        return ventaRepository.saveVenta(venta);
    }

    @Test
    @DisplayName("save - persists new cheque correctly")
    void save_persistsCheque() {
        // Arrange
        Long ventaId = createTestSale();
        AlertaCheque cheque = new AlertaCheque();
        cheque.setVentaId(ventaId);
        cheque.setMonto(1500.0);
        cheque.setFechaCobro(LocalDate.now().plusDays(5));
        cheque.setEstado("PENDIENTE");

        // Act
        Long chequeId = alertaChequeRepository.save(cheque);

        // Assert
        assertThat(chequeId).isNotNull();
        Optional<AlertaCheque> retrieved = alertaChequeRepository.findById(chequeId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getMonto()).isEqualTo(1500.0);
        assertThat(retrieved.get().getEstado()).isEqualTo("PENDIENTE");
        assertThat(retrieved.get().getTipoOrigen()).isEqualTo("VENTA"); // Default fallback
    }

    @Test
    @DisplayName("updateEstadoAndPagoVentaId - modifies state and links to pago_venta_id successfully")
    void updateEstadoAndPagoVentaId_modifiesSuccessfully() {
        // Arrange
        Long ventaId = createTestSale();
        AlertaCheque cheque = new AlertaCheque();
        cheque.setVentaId(ventaId);
        cheque.setMonto(2000.0);
        cheque.setFechaCobro(LocalDate.now());
        cheque.setEstado("PENDIENTE");
        Long chequeId = alertaChequeRepository.save(cheque);

        // Insert a real pago_venta to satisfy FK using existing metodo_pago_id 1
        jdbcTemplate.update("INSERT INTO pagos_venta (id, venta_id, metodo_pago_id, monto, fecha_pago, anulado) VALUES (999, ?, 1, 2000.0, ?, false)", ventaId, LocalDateTime.now());

        // Act
        alertaChequeRepository.updateEstadoAndPagoVentaId(chequeId, "COBRADO", 999L);

        // Assert
        Optional<AlertaCheque> updated = alertaChequeRepository.findById(chequeId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getEstado()).isEqualTo("COBRADO");
        assertThat(updated.get().getPagoVentaId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("sumMontoPendienteByVentaId - computes sum exclusively for PENDIENTE cheques")
    void sumMontoPendienteByVentaId_computesAccurately() {
        // Arrange
        Long ventaId = createTestSale();

        // Cheque 1: Pending (Should be summed)
        AlertaCheque c1 = new AlertaCheque();
        c1.setVentaId(ventaId);
        c1.setMonto(100.0);
        c1.setFechaCobro(LocalDate.now());
        c1.setEstado("PENDIENTE");
        alertaChequeRepository.save(c1);

        // Cheque 2: Pending (Should be summed)
        AlertaCheque c2 = new AlertaCheque();
        c2.setVentaId(ventaId);
        c2.setMonto(300.0);
        c2.setFechaCobro(LocalDate.now());
        c2.setEstado("PENDIENTE");
        alertaChequeRepository.save(c2);

        // Cheque 3: Cobrado (Should be ignored)
        AlertaCheque c3 = new AlertaCheque();
        c3.setVentaId(ventaId);
        c3.setMonto(500.0);
        c3.setFechaCobro(LocalDate.now());
        c3.setEstado("COBRADO");
        alertaChequeRepository.save(c3);

        // Act
        double sum = alertaChequeRepository.sumMontoPendienteByVentaId(ventaId);

        // Assert
        assertThat(sum).isEqualTo(400.0); // 100 + 300
    }
}
