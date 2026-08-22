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

    @Mock
    private com.centralizesys.repository.ClienteRepository clienteRepository;

    @Mock
    private com.centralizesys.repository.MetodoPagoRepository metodoPagoRepository;

    @Mock
    private com.centralizesys.repository.AlertaChequeRepository alertaChequeRepository;

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
        when(repository.updatePagoAnulado(pagoId)).thenReturn(1);
        when(repository.addDeudaAtomic(deudaId, 100.0, 100.0)).thenReturn(1);

        // Mock security context for Auditoria (if not using static mock, just ensure auditoria runs)

        // When
        assertDoesNotThrow(() -> deudoresService.anularPago(pagoId));

        // Then
        // Balance becomes 0 + 100 = 100. Since 100 == 100 (original), status becomes PENDIENTE.
        verify(repository).updatePagoAnulado(pagoId);
        verify(repository).addDeudaAtomic(deudaId, 100.0, 100.0);
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

    // --- PHASE 2.2 EXPLOITS ---

    @Test
    @DisplayName("UT-31: anularPago restores Saldo a Favor when payment method was SALDO (Vector 10)")
    void anularPago_RestoresSaldoAFavor() {
        // Given
        Long pagoId = 1L;
        Long deudaId = 100L;
        // MetodoPagoId is 4L (mocked as SALDO)
        PagoDeuda pago = new PagoDeuda(pagoId, deudaId, 4L, 50.0, null, null, 1L, false, "SALDO", "Sistema");

        DeudaResponse deuda = new DeudaResponse(deudaId, 10L, "Juan", 5L, 50.0, null, "PARCIAL", 100.0, null);

        when(repository.findPagoById(pagoId)).thenReturn(Optional.of(pago));
        when(repository.findById(deudaId)).thenReturn(Optional.of(deuda));
        when(repository.updatePagoAnulado(pagoId)).thenReturn(1);
        when(repository.addDeudaAtomic(deudaId, 50.0, 100.0)).thenReturn(1);

        com.centralizesys.model.sales.MetodoPago saldoMethod = new com.centralizesys.model.sales.MetodoPago();
        saldoMethod.setId(4L);
        saldoMethod.setAcronimo("SALDO");
        when(metodoPagoRepository.findById(4L)).thenReturn(Optional.of(saldoMethod));

        // When
        assertDoesNotThrow(() -> deudoresService.anularPago(pagoId));

        // Then
        verify(clienteRepository).addSaldo(5L, 50.0); // Client ID is 5, amount 50
    }

    @Test
    @DisplayName("UT-32: registrarPago blocks overpayment of debt (Vector 6 Underflow)")
    void registrarPago_Throws_WhenOverpayingDebt() {
        // Given
        Long deudaId = 100L;
        DeudaResponse deuda = new DeudaResponse(deudaId, 10L, "Juan", 5L, 10.0, null, "PARCIAL", 100.0, null); // Debt is 10.0

        when(repository.findById(deudaId)).thenReturn(Optional.of(deuda));

        com.centralizesys.model.debt.PagoDeudaRequest overPayment = new com.centralizesys.model.debt.PagoDeudaRequest();
        overPayment.setMontoPago(10000.0); // Paying 10000 for a 10 debt!
        overPayment.setMetodoPagoId(1L);

        // When/Then
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> deudoresService.registrarPago(deudaId, java.util.List.of(overPayment), 1L));
        assertTrue(ex.getMessage().toLowerCase().contains("supera") || ex.getMessage().toLowerCase().contains("mayor"),
                "Error message must indicate overpayment");
    }

    @Test
    @DisplayName("UT-34: registrarPago blocks deactivated payment methods (Vector 14)")
    void registrarPago_BlocksDeactivatedMethod() {
        // Given
        Long deudaId = 100L;
        DeudaResponse deuda = new DeudaResponse(deudaId, 10L, "Juan", 5L, 100.0, null, "PARCIAL", 100.0, null);

        when(repository.findById(deudaId)).thenReturn(Optional.of(deuda));

        com.centralizesys.model.debt.PagoDeudaRequest pagoReq = new com.centralizesys.model.debt.PagoDeudaRequest();
        pagoReq.setMontoPago(50.0);
        pagoReq.setMetodoPagoId(9L);

        com.centralizesys.model.sales.MetodoPago inactiveMethod = new com.centralizesys.model.sales.MetodoPago();
        inactiveMethod.setId(9L);
        inactiveMethod.setDescripcion("Tarjeta Vieja");
        inactiveMethod.setActivo(false);

        com.centralizesys.model.sales.MetodoPago saldoMethod = new com.centralizesys.model.sales.MetodoPago();
        saldoMethod.setId(4L);
        saldoMethod.setAcronimo("SALDO");
        when(metodoPagoRepository.findByAcronimo("SALDO")).thenReturn(Optional.of(saldoMethod));

        when(metodoPagoRepository.findById(9L)).thenReturn(Optional.of(inactiveMethod));
        when(repository.deductDeudaAtomic(eq(deudaId), eq(50.0), anyDouble())).thenReturn(1);

        // When/Then
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                deudoresService.registrarPago(deudaId, java.util.List.of(pagoReq), 1L)
        );
        assertTrue(ex.getMessage().contains("desactivado"));
    }
}
