package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.debt.DeudaResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DeudoresRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private DeudoresRepository deudoresRepository;

    @Autowired
    private VentaRepository ventaRepository;

    // Helper to create a sale (required for debt FK)
    private Long createTestSale() {
        Long userId = createTestUser();
        var venta = new com.centralizesys.model.sales.Venta();
        venta.setFecha(java.time.LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0));
        venta.setClienteNombre("Debt Test Client");
        venta.setTotalVenta(1000.00);
        venta.setUsuarioId(userId);
        return ventaRepository.saveVenta(venta);
    }

    @Test
    @DisplayName("save - inserts debt with PENDIENTE status and auto-date")
    void save_insertsDebtWithPendienteStatus() {
        // Arrange
        Long ventaId = createTestSale();

        // Act
        deudoresRepository.save(ventaId, "Juan Perez", null, 500.00);

        // Assert
        List<DeudaResponse> all = deudoresRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().getClienteNombre()).isEqualTo("Juan Perez");
        assertThat(all.getFirst().getMontoDeuda()).isEqualTo(500.00);
        assertThat(all.getFirst().getEstado()).isEqualTo("PENDIENTE");
        assertThat(all.getFirst().getFechaDeuda()).isNotNull();
    }

    @Test
    @DisplayName("findById - returns Optional with debt")
    void findById_returnsDebt() {
        // Arrange
        Long ventaId = createTestSale();
        deudoresRepository.save(ventaId, "Maria Garcia", null, 300.00);

        // Get the ID from findAll
        Long debtId = deudoresRepository.findAll().getFirst().getId();

        // Act
        Optional<DeudaResponse> found = deudoresRepository.findById(debtId);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getClienteNombre()).isEqualTo("Maria Garcia");
    }

    @Test
    @DisplayName("findById - returns empty Optional for non-existent ID")
    void findById_returnsEmptyForMissing() {
        // Act
        Optional<DeudaResponse> found = deudoresRepository.findById(99999L);

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("updateMontoAndEstado - atomically updates balance and status")
    void updateMontoAndEstado_updatesAtomically() {
        // Arrange
        Long ventaId = createTestSale();
        deudoresRepository.save(ventaId, "Pedro Lopez", null, 1000.00);
        Long debtId = deudoresRepository.findAll().getFirst().getId();

        // Act - Partial payment
        deudoresRepository.updateMontoAndEstado(debtId, 500.00, "PARCIAL");

        // Assert
        Optional<DeudaResponse> updated = deudoresRepository.findById(debtId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getMontoDeuda()).isEqualTo(500.00);
        assertThat(updated.get().getEstado()).isEqualTo("PARCIAL");
    }

    @Test
    @DisplayName("updateMontoAndEstado - can mark as PAGADO")
    void updateMontoAndEstado_canMarkAsPagado() {
        // Arrange
        Long ventaId = createTestSale();
        deudoresRepository.save(ventaId, "Ana Rodriguez", null, 250.00);
        Long debtId = deudoresRepository.findAll().getFirst().getId();

        // Act - Full payment
        deudoresRepository.updateMontoAndEstado(debtId, 0.0, "PAGADO");

        // Assert
        Optional<DeudaResponse> updated = deudoresRepository.findById(debtId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getMontoDeuda()).isEqualTo(0.0);
        assertThat(updated.get().getEstado()).isEqualTo("PAGADO");
    }

    @Test
    @DisplayName("findAll - returns debts ordered by ID DESC")
    void findAll_returnsOrderedDescending() {
        // Arrange
        Long ventaId1 = createTestSale();
        Long ventaId2 = createTestSale();

        deudoresRepository.save(ventaId1, "First Debtor", null, 100.00);
        deudoresRepository.save(ventaId2, "Second Debtor", null, 200.00);

        // Act
        List<DeudaResponse> all = deudoresRepository.findAll();

        // Assert - Second inserted should come first (higher ID)
        assertThat(all).hasSize(2);
        assertThat(all.getFirst().getClienteNombre()).isEqualTo("Second Debtor");
    }

    @Test
    @DisplayName("hasActiveDebts - returns true when active debts exist")
    void hasActiveDebts_returnsTrueWhenExists() {
        // Arrange
        Long ventaId = createTestSale();
        deudoresRepository.save(ventaId, "Active Debtor", null, 500.00);

        // Act
        boolean hasDebts = deudoresRepository.hasActiveDebts();

        // Assert
        assertThat(hasDebts).isTrue();
    }

    @Test
    @DisplayName("hasActiveDebts - returns false when no active debts")
    void hasActiveDebts_returnsFalseWhenEmpty() {
        // Arrange - No debts created

        // Act
        boolean hasDebts = deudoresRepository.hasActiveDebts();

        // Assert
        assertThat(hasDebts).isFalse();
    }

    @Test
    @DisplayName("hasActiveDebts - returns false when all debts are PAGADO")
    void hasActiveDebts_returnsFalseWhenAllPagado() {
        // Arrange
        Long ventaId = createTestSale();
        deudoresRepository.save(ventaId, "Paid Debtor", null, 100.00);
        Long debtId = deudoresRepository.findAll().getFirst().getId();
        deudoresRepository.updateMontoAndEstado(debtId, 0.0, "PAGADO");

        // Act
        boolean hasDebts = deudoresRepository.hasActiveDebts();

        // Assert
        assertThat(hasDebts).isFalse();
    }

    @Test
    @DisplayName("findExpiredDebts - returns only debts older than X days")
    void findExpiredDebts_returnsOldDebts() {
        // Arrange
        Long ventaId1 = createTestSale();
        Long ventaId2 = createTestSale();

        // Debt 1: Today (Not Expired)
        deudoresRepository.save(ventaId1, "New Debtor", null, 100.0);

        // Debt 2: 20 Days Ago (Expired)
        deudoresRepository.save(ventaId2, "Old Debtor", null, 200.0);
        // Manually backdate the second debt
        java.time.LocalDateTime oldDate = java.time.LocalDateTime.of(2026, java.time.Month.JANUARY, 1, 12, 0).minusDays(20);
        // Get ID of the last inserted (Old Debtor)
        Long oldDebtId = deudoresRepository.findAll().getFirst().getId(); // List is ordered DESC, so first is newest (Old
        // Debtor)

        jdbcTemplate.update("UPDATE deudores SET fecha_deuda = ? WHERE id = ?", oldDate, oldDebtId);

        // Act
        List<DeudaResponse> expired = deudoresRepository.findExpiredDebts(15);

        // Assert
        assertThat(expired).hasSize(1);
        assertThat(expired.getFirst().getClienteNombre()).isEqualTo("Old Debtor");
    }
}
