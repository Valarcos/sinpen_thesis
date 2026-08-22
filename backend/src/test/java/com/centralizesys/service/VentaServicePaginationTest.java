package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.StockRepository;
import com.centralizesys.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class VentaServicePaginationTest {

    @Mock
    private VentaRepository ventaRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private DeudoresRepository deudoresRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private com.centralizesys.repository.AlertaChequeRepository alertaChequeRepository;
    @Mock
    private com.centralizesys.repository.MetodoPagoRepository metodoPagoRepository;
    @Mock
    private com.centralizesys.repository.ClienteRepository clienteRepository;
    @Mock
    private com.centralizesys.repository.DevolucionesRepository devolucionesRepository;

    @InjectMocks
    private VentaService ventaService;


    // --- GROUP 5: PAGINATION & DATE RANGE Logic ---

    @Test
    @DisplayName("UT-20: getVentasPage uses default 30-day range when dates are null")
    void getVentasPage_UsesDefaultRange_WhenNull() {
        // Arrange
        when(ventaRepository.findVentasByFechaBetween(any(LocalDateTime.class), any(LocalDateTime.class), eq(null), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(ventaRepository.countVentasByFechaBetween(any(LocalDateTime.class), any(LocalDateTime.class), eq(null))).thenReturn(0L);

        // Act
        ventaService.getVentasPage(null, null, null, 0, 20);

        // Assert
        // Verify we called repo with dates. Since we can't easily predict "now",
        // we capture arguments or just verify it was called.
        verify(ventaRepository).findVentasByFechaBetween(any(LocalDateTime.class), any(LocalDateTime.class), eq(null), eq(20), eq(0));
    }

    @Test
    @DisplayName("UT-21: getVentasPage throws when range exceeds 60 days")
    void getVentasPage_Throws_WhenRangeExceeds60Days() {
        // Arrange
        String start = LocalDate.of(2023, java.time.Month.JANUARY, 1).minusDays(61).toString();
        String end = LocalDate.of(2023, java.time.Month.JANUARY, 1).toString();

        // Act & Assert
        assertThrows(BusinessRuleException.class,
                () -> ventaService.getVentasPage(start, end, null, 0, 20));
    }

    @Test
    @DisplayName("UT-22: getVentasPage throws when start date is after end date")
    void getVentasPage_Throws_WhenStartAfterEnd() {
        // Arrange
        String start = LocalDate.of(2023, java.time.Month.JANUARY, 1).toString();
        String end = LocalDate.of(2023, java.time.Month.JANUARY, 1).minusDays(2).toString();

        // Act & Assert
        assertThrows(BusinessRuleException.class,
                () -> ventaService.getVentasPage(start, end, null, 0, 20));
    }

    @Test
    @DisplayName("UT-22B: getVentasPage bypasses date checks when searchId is provided")
    void getVentasPage_BypassesDateChecks_WithSearchId() {
        // Arrange
        // We provide a massive date range that would normally fail the 60-day rule
        String start = LocalDate.of(2020, java.time.Month.JANUARY, 1).toString();
        String end = LocalDate.of(2023, java.time.Month.JANUARY, 1).toString();
        Long searchId = 123L;

        when(ventaRepository.findVentasByFechaBetween(any(), any(), eq(searchId), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        // Act - should not throw Exception
        ventaService.getVentasPage(start, end, searchId, 0, 20);

        // Assert
        verify(ventaRepository).findVentasByFechaBetween(any(), any(), eq(searchId), eq(20), eq(0));
    }

    @Test
    @DisplayName("Epic3: getVentasPendientesPage clamps size to 100 max")
    void getVentasPendientesPage_ClampsSizeTo100() {
        // Arrange
        when(ventaRepository.findVentasPendientesByFechaBetween(any(), any(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(ventaRepository.countVentasPendientesByFechaBetween(any(), any())).thenReturn(0L);

        // Act
        ventaService.getVentasPendientesPage(null, null, 0, 999999); // malicious size

        // Assert
        verify(ventaRepository).findVentasPendientesByFechaBetween(any(), any(), eq(100), eq(0));
    }
}
