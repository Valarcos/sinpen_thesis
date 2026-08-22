package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.gastos.GastoCaja;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GastoCajaRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private GastoCajaRepository gastoCajaRepository;

    // -----------------------------------------------------------------------
    // save
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("save - correctly inserts and returns ID")
    void save_Success() {
        Long userId = createTestUser();

        GastoCaja gasto = new GastoCaja();
        gasto.setMonto(250.0);
        gasto.setMotivo("Insumos");
        gasto.setFechaGasto(LocalDateTime.of(2026, Month.OCTOBER, 15, 10, 0));
        gasto.setFechaRegistro(LocalDateTime.now());
        gasto.setPersonaInvolucrada("Juan");
        gasto.setRegistradoPorUsuarioId(userId);
        gasto.setCategoria("Operativos");

        Long id = gastoCajaRepository.save(gasto);

        assertThat(id).isNotNull().isPositive();

        assertThat(gastoCajaRepository.findById(id))
                .isPresent()
                .get()
                .extracting(GastoCaja::getMonto, GastoCaja::getAnulado)
                .containsExactly(250.0, false);
    }

    @Test
    @DisplayName("save - enforces constraints like missing motivo")
    void save_FailsConstraints() {
        GastoCaja gasto = new GastoCaja();
        // gasto.setMotivo(null); // Missing required field
        gasto.setMonto(100.0);
        gasto.setFechaGasto(LocalDateTime.of(2026, java.time.Month.OCTOBER, 15, 10, 0));
        gasto.setFechaRegistro(LocalDateTime.now());

        // Exception type might vary based on DB driver mapping, usually DataIntegrityViolationException for NOT NULL
        assertThrows(DataIntegrityViolationException.class, () -> gastoCajaRepository.save(gasto));
    }

    // -----------------------------------------------------------------------
    // findAll (paginated)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("findAll - returns rows ordered by fecha_gasto DESC")
    void findAll_ReturnsOrdered() {
        Long userId = createTestUser();

        GastoCaja g1 = new GastoCaja();
        g1.setMonto(100.0);
        g1.setMotivo("Old");
        g1.setPersonaInvolucrada("Test");
        g1.setFechaGasto(LocalDateTime.of(2026, Month.JANUARY, 1, 10, 0));
        g1.setFechaRegistro(LocalDateTime.now());
        g1.setRegistradoPorUsuarioId(userId);
        gastoCajaRepository.save(g1);

        GastoCaja g2 = new GastoCaja();
        g2.setMonto(200.0);
        g2.setMotivo("New");
        g2.setPersonaInvolucrada("Test");
        g2.setFechaGasto(LocalDateTime.of(2099, Month.FEBRUARY, 1, 10, 0));
        g2.setFechaRegistro(LocalDateTime.now());
        g2.setRegistradoPorUsuarioId(userId);
        gastoCajaRepository.save(g2);

        // No date filter, large page to capture all
        List<GastoCaja> list = gastoCajaRepository.findAll(100L, 0L, null, null, null);

        assertThat(list).hasSizeGreaterThanOrEqualTo(2);
        // The first one should be the newest (g2)
        assertThat(list.getFirst().getMotivo()).isEqualTo("New");
    }

    @Test
    @DisplayName("findAll - respects year+month date filter")
    void findAll_FiltersByYearAndMonth() {
        Long userId = createTestUser();

        // Gasto in October 2026
        GastoCaja oct = new GastoCaja();
        oct.setMonto(300.0); oct.setMotivo("October"); oct.setPersonaInvolucrada("Test");
        oct.setFechaGasto(LocalDateTime.of(2026, Month.OCTOBER, 5, 10, 0));
        oct.setFechaRegistro(LocalDateTime.now());
        oct.setRegistradoPorUsuarioId(userId);
        gastoCajaRepository.save(oct);

        // Gasto in November 2026
        GastoCaja nov = new GastoCaja();
        nov.setMonto(400.0); nov.setMotivo("November"); nov.setPersonaInvolucrada("Test");
        nov.setFechaGasto(LocalDateTime.of(2026, Month.NOVEMBER, 5, 10, 0));
        nov.setFechaRegistro(LocalDateTime.now());
        nov.setRegistradoPorUsuarioId(userId);
        gastoCajaRepository.save(nov);

        // Filter October only
        List<GastoCaja> result = gastoCajaRepository.findAll(100L, 0L, 2026, 10, null);

        assertThat(result)
                .isNotEmpty()
                .allMatch(g -> g.getMotivo().equals("October"));
    }

    @Test
    @DisplayName("findAll - applies LIMIT/OFFSET correctly for pagination")
    void findAll_AppliesLimitOffset() {
        Long userId = createTestUser();

        for (int i = 1; i <= 5; i++) {
            GastoCaja g = new GastoCaja();
            g.setMonto((double) i * 10);
            g.setMotivo("G" + i);
            g.setPersonaInvolucrada("Test");
            g.setFechaGasto(LocalDateTime.of(2026, Month.MARCH, i, 10, 0));
            g.setFechaRegistro(LocalDateTime.now());
            g.setRegistradoPorUsuarioId(userId);
            gastoCajaRepository.save(g);
        }

        // Page 1 (0-indexed), size 2 → offset 2
        List<GastoCaja> page0 = gastoCajaRepository.findAll(2L, 0L, 2026, 3, null);
        List<GastoCaja> page1 = gastoCajaRepository.findAll(2L, 2L, 2026, 3, null);

        assertThat(page0).hasSize(2);
        assertThat(page1).hasSize(2);
        // Verify no overlap
        assertThat(page0.stream().map(GastoCaja::getId).toList())
                .doesNotContainAnyElementsOf(page1.stream().map(GastoCaja::getId).toList());
    }

    // -----------------------------------------------------------------------
    // countAll
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("countAll - returns correct total count with date filter")
    void countAll_WithFilter_ReturnsCount() {
        Long userId = createTestUser();

        // 2 gastos in 2026/10
        for (int d = 1; d <= 2; d++) {
            GastoCaja g = new GastoCaja();
            g.setMonto(50.0); g.setMotivo("Counted"); g.setPersonaInvolucrada("Test");
            g.setFechaGasto(LocalDateTime.of(2026, Month.OCTOBER, d, 10, 0));
            g.setFechaRegistro(LocalDateTime.now());
            g.setRegistradoPorUsuarioId(userId);
            gastoCajaRepository.save(g);
        }

        Long count = gastoCajaRepository.countAll(2026, 10, null);

        assertThat(count).isGreaterThanOrEqualTo(2L);
    }

    @Test
    @DisplayName("countAll - returns 0 when no gastos match the filter")
    void countAll_NoMatch_ReturnsZero() {
        // Year 1900 will have no rows
        Long count = gastoCajaRepository.countAll(1900, 1, null);

        assertThat(count).isZero();
    }

    // -----------------------------------------------------------------------
    // anular
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("anular - correctly updates boolean and reason")
    void anular_UpdatesDb() {
        Long userId = createTestUser();

        GastoCaja gasto = new GastoCaja();
        gasto.setMonto(150.0);
        gasto.setMotivo("To Cancel");
        gasto.setPersonaInvolucrada("Test");
        gasto.setFechaGasto(LocalDateTime.of(2026, Month.OCTOBER, 15, 10, 0));
        gasto.setFechaRegistro(LocalDateTime.now());
        gasto.setRegistradoPorUsuarioId(userId);

        Long id = gastoCajaRepository.save(gasto);

        // Act
        gastoCajaRepository.anular(id, "Error de tipeo");

        // Assert
        GastoCaja updated = gastoCajaRepository.findById(id).orElseThrow();
        assertThat(updated)
                .extracting(GastoCaja::getAnulado, GastoCaja::getRazonAnulacion)
                .containsExactly(true, "Error de tipeo");
    }
}
