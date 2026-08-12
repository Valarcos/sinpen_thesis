package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeuda;
import com.centralizesys.repository.DeudoresRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeudoresServiceTest {

    @Mock
    private DeudoresRepository repository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private DeudoresService deudoresService;

    @Test
    @DisplayName("cancelarPago - should successfully annul payment and restore balance to PAGADO")
    void cancelarPago_Success_ToPagado() {
        // Given
        Long pagoId = 1L;
        Long deudaId = 100L;
        PagoDeuda pago = new PagoDeuda(pagoId, deudaId, 1L, 100.0, null, null, 1L, false, "Efectivo", "Sistema");

        // Deuda has current balance of 0, original was 100
        DeudaResponse deuda = new DeudaResponse(deudaId, 10L, "Juan", 1L, 0.0, null, "PAGADO", 100.0, null);

        when(repository.findPagoById(pagoId)).thenReturn(Optional.of(pago));
        when(repository.findById(deudaId)).thenReturn(Optional.of(deuda));
        when(repository.addDeudaAtomic(deudaId, 100.0, "PENDIENTE")).thenReturn(1);

        // Mock security context for Auditoria (if not using static mock, just ensure auditoria runs)

        // When
        assertDoesNotThrow(() -> deudoresService.anularPago(pagoId));

        // Then
        // Balance becomes 0 + 100 = 100. Since 100 == 100 (original), status becomes PENDIENTE.
        verify(repository).updatePagoAnulado(pagoId);
        verify(repository).addDeudaAtomic(deudaId, 100.0, "PENDIENTE");
        verify(auditoriaService).registrarAccion(any(), eq("PAGO_DEUDA"), anyString());
    }

    @Test
    @DisplayName("cancelarPago - should throw when payment already annulled")
    void cancelarPago_Throws_WhenAlreadyAnnulled() {
        // Given
        Long pagoId = 1L;
        PagoDeuda pago = new PagoDeuda(pagoId, 100L, 1L, 100.0, null, null, 1L, true, "Efectivo", "Sistema");

        when(repository.findPagoById(pagoId)).thenReturn(Optional.of(pago));

        // When/Then
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> deudoresService.anularPago(pagoId));
        assertEquals("El pago ya ha sido anulado.", ex.getMessage());
        verifyNoMoreInteractions(repository);
    }
}
