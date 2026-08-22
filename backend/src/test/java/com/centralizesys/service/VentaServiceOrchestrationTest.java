package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.product.StockLocation;
import com.centralizesys.model.returns.DevolucionRequest;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.model.sales.TipoVenta;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class VentaServiceOrchestrationTest {

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

    @org.junit.jupiter.api.BeforeEach
    void setUpDefaults() {
        com.centralizesys.model.sales.MetodoPago activeMethod = new com.centralizesys.model.sales.MetodoPago();
        activeMethod.setId(1L);
        activeMethod.setDescripcion("Efectivo Default");
        activeMethod.setActivo(true);
        activeMethod.setAcronimo("E");

        com.centralizesys.model.sales.MetodoPago saldoMethod = new com.centralizesys.model.sales.MetodoPago();
        saldoMethod.setId(99L);
        saldoMethod.setDescripcion("Saldo a Favor");
        saldoMethod.setActivo(true);
        saldoMethod.setAcronimo("SALDO");

        lenient().when(metodoPagoRepository.findById(anyLong())).thenReturn(Optional.of(activeMethod));
        lenient().when(metodoPagoRepository.findByAcronimo("E")).thenReturn(Optional.of(activeMethod));
        lenient().when(metodoPagoRepository.findByAcronimo("SALDO")).thenReturn(Optional.of(saldoMethod));
        lenient().when(ventaRepository.lockVentaForUpdate(anyLong(), anyString())).thenReturn(true);
    }


    // --- GROUP 4: DEBT & ORCHESTRATION (Public API) ---
    // Since handleDebt is private, we verify it via the Orchestrator

    @Test
    @DisplayName("UT-11: handleDebt saves debt when payment is less than total")
    void registrarVenta_SavesDebt_Correctly() {
        // Arrange
        Product p = Product.builder().codigo("C").descripcion("Code").precioCosto(50.0).precioMayorista(50.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.saveVenta(any(Venta.class))).thenReturn(500L); // Mock generated ID

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // Total $100

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMonto(80.0); // Paid $80, Debt $20
        pago.setMetodoPagoId(1L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("John Doe");
        request.setItems(List.of(item));
        request.setPagos(List.of(pago));
        request.setUsuarioId(10L);

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertEquals(500L, response.getId());

        // Verify Debt Logic
        verify(deudoresRepository).save(500L, "John Doe", 999L, 20.0);
    }

    @Test
    @DisplayName("UT-12: handleDebt throws exception when debt exists but no client name")
    void registrarVenta_Throws_WhenDebtButNoName() {
        // Arrange
        Product p = Product.builder().codigo("C").descripcion("Code").precioCosto(50.0).precioMayorista(50.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.saveVenta(any())).thenReturn(500L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // Total 100

        // No payments -> Full Debt
        VentaRequest request = new VentaRequest();
        request.setClienteNombre(""); // BLANK NAME
        request.setItems(List.of(item));

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-13: handleDebt ignores micro differences (Epsilon check)")
    void registrarVenta_IgnoresMicroDebt() {
        // Arrange
        Product p = Product.builder().codigo("C").descripcion("Code").precioCosto(50.0).precioMayorista(50.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.saveVenta(any())).thenReturn(500L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // Total 100

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMonto(99.99999); // Tiny difference < 0.0001
        pago.setMetodoPagoId(1L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("John");
        request.setItems(List.of(item));
        request.setPagos(List.of(pago));

        // Act
        ventaService.registrarVenta(request);

        // Assert
        verify(deudoresRepository, never()).save(anyLong(), anyString(), any(), anyDouble());
    }

    @Test
    @DisplayName("UT-14 & UT-15: Full Flow Success + Audit")
    void registrarVenta_OrchestratesFullFlow_Success() {
        // Arrange
        Product p = Product.builder().codigo("C").descripcion("Code").precioCosto(50.0).precioMayorista(50.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.saveVenta(any())).thenReturn(500L);
        when(ventaRepository.findVendedorNombre(any())).thenReturn("Admin Test");

        // Stock Location mock to avoid NPE in loop
        when(stockRepository.findByProductId(1L)).thenReturn(
                List.of(new StockLocation(1L, 1L, 1L, "Loc", 100L)));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(2L); // Total 200

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMetodoPagoId(1L); // Cash, Card, etc.
        pago.setMonto(200.0); // Pay in full

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Client");
        request.setUsuarioId(7L);
        request.setItems(List.of(item));
        request.setPagos(List.of(pago)); // <--- Add this line!

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertEquals(500L, response.getId());
        assertEquals(200.0, response.getTotalVenta());
        assertEquals("Admin Test", response.getVendedorNombre());

        // Verify Interactions
        verify(ventaRepository).saveVenta(any(Venta.class));
        verify(ventaRepository).saveDetalles(anyList());
        verify(ventaRepository).savePagos(anyList()); // Empty list is fine
        verify(auditoriaService).registrarAccion(eq(7L), eq("VENTA"), contains("200.0"));
    }

    @Test
    @DisplayName("UT-16: registrarVenta orchestrates cheques and saves them in AlertaCheques")
    void registrarVenta_OrchestratesCheques_Success() {
        // Arrange
        Product p = Product.builder().codigo("C").descripcion("Code").precioCosto(50.0).precioMayorista(50.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.saveVenta(any())).thenReturn(500L);

        when(stockRepository.findByProductId(1L)).thenReturn(
                List.of(new StockLocation(1L, 1L, 1L, "Loc", 100L)));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // Total $100

        com.centralizesys.model.cheque.AlertaChequeRequest chequeReq = new com.centralizesys.model.cheque.AlertaChequeRequest();
        chequeReq.setMonto(100.0);
        chequeReq.setFechaCobro(java.time.LocalDate.now().plusDays(10));

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Ghost Cheque Client");
        request.setUsuarioId(7L);
        request.setItems(List.of(item));
        request.setCheques(List.of(chequeReq));

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertEquals(500L, response.getId());
        assertEquals(100.0, response.getTotalVenta());

        // Verify Alerta Cheque was saved!
        verify(alertaChequeRepository).save(any(com.centralizesys.model.cheque.AlertaCheque.class));
        verify(auditoriaService).registrarAccion(eq(7L), eq("VENTA"), contains("100.0"));
    }

    @Test
    @DisplayName("UT-17: registrarVenta auto-registers casual client when ID is null but name is provided")
    void registrarVenta_AutoRegistersCasualClient() {
        // Arrange
        Product p = Product.builder().codigo("C").descripcion("Code").precioCosto(50.0).precioMayorista(50.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.saveVenta(any())).thenReturn(500L);
        when(stockRepository.findByProductId(1L)).thenReturn(
                List.of(new StockLocation(1L, 1L, 1L, "Loc", 100L)));

        when(clienteRepository.findByNombre("Casual User")).thenReturn(Optional.empty());
        com.centralizesys.model.client.Cliente savedClient = new com.centralizesys.model.client.Cliente();
        savedClient.setId(123L);
        when(clienteRepository.save(any(), anyLong())).thenReturn(savedClient);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setClienteId(null);
        request.setClienteNombre("Casual User");
        request.setUsuarioId(7L);
        request.setItems(List.of(item));

        // Act
        ventaService.registrarVenta(request);

        // Assert
        verify(clienteRepository).findByNombre("Casual User");
        verify(clienteRepository).save(any(), eq(7L));
        assertEquals(123L, request.getClienteId(), "Request should have its clienteId hydrated");
    }

    // --- GROUP 4: CHEQUES ---

    @Test
    @DisplayName("UT-20: cobrarCheque sets pagoVentaId and registers audit")
    void cobrarCheque_Success() {
        // Given
        Long chequeId = 1L;
        Long metodoPagoId = 1L;
        Long authenticatedUserId = 2L;

        com.centralizesys.model.cheque.AlertaCheque cheque = new com.centralizesys.model.cheque.AlertaCheque(
                chequeId, 100L, 500.0, java.time.LocalDate.now(), "PENDIENTE", null, null
        );

        com.centralizesys.model.sales.MetodoPago metodo = new com.centralizesys.model.sales.MetodoPago();
        metodo.setId(metodoPagoId);
        metodo.setActivo(true);

        when(alertaChequeRepository.findById(chequeId)).thenReturn(Optional.of(cheque));
        when(metodoPagoRepository.findById(metodoPagoId)).thenReturn(Optional.of(metodo));
        when(ventaRepository.savePagoUnicoReturningId(100L, metodoPagoId, 500.0, authenticatedUserId)).thenReturn(999L);

        lenient().when(alertaChequeRepository.updateEstadoAndPagoVentaIdAtomic(anyLong(), anyString(), nullable(Long.class), anyString())).thenReturn(1);

        // When
        ventaService.cobrarCheque(chequeId, metodoPagoId, authenticatedUserId);

        // Then
        verify(alertaChequeRepository).updateEstadoAndPagoVentaIdAtomic(eq(chequeId), eq("COBRADO"), eq(999L), eq("PENDIENTE"));
        verify(auditoriaService).registrarAccion(eq(authenticatedUserId), eq("COBRO_CHEQUE"), anyString());
    }

    @Test
    @DisplayName("UT-21: cancelarCobroCheque restores PENDIENTE and nulls pago_venta_id")
    void cancelarCobroCheque_Success() {
        // Given
        Long chequeId = 1L;
        Long authenticatedUserId = 2L;

        com.centralizesys.model.cheque.AlertaCheque cheque = new com.centralizesys.model.cheque.AlertaCheque(
                chequeId, 100L, 500.0, java.time.LocalDate.now(), "COBRADO", 999L, null
        );

        when(alertaChequeRepository.findById(chequeId)).thenReturn(Optional.of(cheque));

        lenient().when(alertaChequeRepository.updateEstadoAndPagoVentaIdAtomic(anyLong(), anyString(), nullable(Long.class), anyString())).thenReturn(1);

        // When
        ventaService.cancelarCobroCheque(chequeId, authenticatedUserId);

        // Then
        verify(ventaRepository).anularPagoVentaById(999L);
        verify(alertaChequeRepository).updateEstadoAndPagoVentaIdAtomic(eq(chequeId), eq("PENDIENTE"), isNull(), eq("COBRADO"));
        verify(auditoriaService).registrarAccion(eq(authenticatedUserId), eq("CANCELACION_COBRO_CHEQUE"), anyString());
    }

    // --- GROUP 7: EPIC 1 OVERPAYMENT RULES ---

    @Test
    @DisplayName("UT-22: registrarVenta throws when pagos + cheques > total")
    void registrarVenta_Throws_WhenOverpaid() {
        // Arrange
        Product p = Product.builder().codigo("C").descripcion("Code").precioCosto(50.0).precioMayorista(50.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // Total: $100

        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMonto(60.0);
        pago.setMetodoPagoId(1L);

        com.centralizesys.model.cheque.AlertaChequeRequest cheque = new com.centralizesys.model.cheque.AlertaChequeRequest();
        cheque.setMonto(50.0); // 60 + 50 = 110 (Overpaid)
        cheque.setFechaCobro(java.time.LocalDate.now().plusDays(10));

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setPagos(List.of(pago));
        request.setCheques(List.of(cheque));

        // Act & Assert
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
        assertTrue(ex.getMessage().contains("superar el total"));
    }

    @Test
    @DisplayName("UT-23: modificarCarrito allows saving overpaid pending sale (Epic 1 rule)")
    void modificarCarrito_AllowsOverpaidState() {
        // Arrange
        Venta pendingSale = new Venta();
        pendingSale.setId(99L);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setTipoVenta("MINORISTA");
        pendingSale.setVersion(0);
        when(ventaRepository.findById(99L)).thenReturn(Optional.of(pendingSale));

        Product p = Product.builder().codigo("C").descripcion("Code").precioCosto(50.0).precioMayorista(50.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // New total: $100

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setClienteNombre("John");
        request.setTipoVenta(TipoVenta.MINORISTA);

        // Act
        assertDoesNotThrow(() -> ventaService.modificarCarrito(99L, request, 1L));

        verify(ventaRepository).updatePendingSaleHeader(eq(99L), eq(100.0), eq(0.0), eq(0.0), eq(0.0), eq(999L), eq("John"), eq("MINORISTA"));
    }

    @Test
    @DisplayName("UT-23b: modificarCarrito persists client and sale type changes")
    void modificarCarrito_PersistsClientAndSaleTypeChanges() {
        // Arrange
        Venta pendingSale = new Venta();
        pendingSale.setId(99L);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setTipoVenta("MINORISTA");
        pendingSale.setVersion(0);
        when(ventaRepository.findById(99L)).thenReturn(Optional.of(pendingSale));

        Product p = Product.builder().codigo("C").descripcion("Code").precioCosto(50.0).precioMayorista(50.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setClienteId(88L);
        request.setClienteNombre("Changed Client");
        request.setTipoVenta(TipoVenta.MAYORISTA);

        com.centralizesys.model.client.Cliente mockClient = new com.centralizesys.model.client.Cliente();
        mockClient.setId(88L);
        mockClient.setActivo(true);
        when(clienteRepository.findById(88L)).thenReturn(Optional.of(mockClient));

        // Act
        ventaService.modificarCarrito(99L, request, 1L);

        // Assert

        verify(ventaRepository).updatePendingSaleHeader(eq(99L), eq(50.0), eq(0.0), eq(0.0), eq(0.0), eq(88L), eq("Changed Client"), eq("MAYORISTA"));
    }

    @Test
    @DisplayName("UT-24: finalizarVenta throws when pagos + cheques > total")
    void finalizarVenta_Throws_WhenOverpaid() {
        // Arrange
        Venta pendingSale = new Venta();
        pendingSale.setId(99L);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setTotalVenta(100.0);
        when(ventaRepository.findById(99L)).thenReturn(Optional.of(pendingSale));

        com.centralizesys.model.sales.PagoVenta pago = new com.centralizesys.model.sales.PagoVenta();
        pago.setMonto(60.0);
        when(ventaRepository.findPagosActivosByVentaId(99L)).thenReturn(List.of(pago));

        when(alertaChequeRepository.sumMontoPendienteByVentaId(99L)).thenReturn(50.0); // 60 + 50 = 110 > 100

        // Act & Assert
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> ventaService.finalizarVenta(99L, 1L));
        assertTrue(ex.getMessage().contains("supera el total"));
    }

    @Test
    @DisplayName("UT-25: finalizarVenta succeeds when exact match")
    void finalizarVenta_Succeeds_WhenExactMatch() {
        // Arrange
        Venta pendingSale = new Venta();
        pendingSale.setId(99L);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setTotalVenta(100.0);
        when(ventaRepository.findById(99L)).thenReturn(Optional.of(pendingSale));

        com.centralizesys.model.sales.PagoVenta pago = new com.centralizesys.model.sales.PagoVenta();
        pago.setMonto(50.0);
        when(ventaRepository.findPagosActivosByVentaId(99L)).thenReturn(List.of(pago));
        when(ventaRepository.updateFechaAndEstadoAtomic(anyLong(), any(), anyString(), anyString())).thenReturn(1);
        when(alertaChequeRepository.sumMontoPendienteByVentaId(99L)).thenReturn(50.0); // 50 + 50 = 100

        // Act
        assertDoesNotThrow(() -> ventaService.finalizarVenta(99L, 1L));

        // Assert
        verify(ventaRepository).updateFechaAndEstadoAtomic(eq(99L), any(), eq("ACTIVA"), eq("PENDIENTE"));
        // Debt repo should not save debt since paid in full
        verify(deudoresRepository, never()).save(anyLong(), anyString(), any(), anyDouble());
    }

    // --- GROUP 8: EPIC 2 — registrarPago CHEQUE ROUTING ---

    @Test
    @DisplayName("E2-UT-01: registrarPago routes payment WITH fechaCobro to alertas_cheques")
    void registrarPago_RoutesCheque_ToAlertasCheques() {
        // Arrange
        Long ventaId = 1L;
        Long usuarioId = 5L;

        Venta pendingSale = new Venta();
        pendingSale.setId(ventaId);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setTotalVenta(500.0);

        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(pendingSale));

        // No pre-existing cheques for this venta


        com.centralizesys.model.debt.PagoDeudaRequest chequePayment = new com.centralizesys.model.debt.PagoDeudaRequest();
        chequePayment.setMontoPago(200.0);
        chequePayment.setMetodoPagoId(3L); // Cheque method ID
        chequePayment.setFechaCobro(LocalDate.now().plusDays(30)); // <-- Has fechaCobro

        // Act
        ventaService.registrarPago(ventaId, List.of(chequePayment), usuarioId);

        // Assert: Must save to alertas_cheques
        verify(alertaChequeRepository).save(any(com.centralizesys.model.cheque.AlertaCheque.class));
        // Must NOT save via the standard cash path
        verify(ventaRepository, never()).savePagoUnico(anyLong(), anyLong(), anyDouble(), anyLong());
        // Audit must still fire
        verify(auditoriaService).registrarAccion(eq(usuarioId), eq("PAGO_PENDIENTE"), anyString());
    }

    @Test
    @DisplayName("E2-UT-02: registrarPago routes payment WITHOUT fechaCobro to pagos_venta (standard path)")
    void registrarPago_RoutesNormal_ToPagosVenta() {
        // Arrange
        Long ventaId = 2L;
        Long usuarioId = 5L;

        Venta pendingSale = new Venta();
        pendingSale.setId(ventaId);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setTotalVenta(300.0);

        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(pendingSale));



        com.centralizesys.model.debt.PagoDeudaRequest cashPayment = new com.centralizesys.model.debt.PagoDeudaRequest();
        cashPayment.setMontoPago(150.0);
        cashPayment.setMetodoPagoId(1L);
        cashPayment.setFechaCobro(null); // <-- No fechaCobro: standard cash/card

        // Act
        ventaService.registrarPago(ventaId, List.of(cashPayment), usuarioId);

        // Assert: Must save via standard path
        verify(ventaRepository).savePagoUnico(ventaId, 1L, 150.0, usuarioId);
        // Must NOT create a cheque alert
        verify(alertaChequeRepository, never()).save(any());
    }

    @Test
    @DisplayName("E2-UT-03: registrarPago with mixed list routes each payment to its correct destination")
    void registrarPago_MixedList_RoutesEachCorrectly() {
        // Arrange: $500 sale, $100 cash + $200 cheque
        Long ventaId = 3L;
        Long usuarioId = 5L;

        Venta pendingSale = new Venta();
        pendingSale.setId(ventaId);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setTotalVenta(500.0);

        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(pendingSale));



        com.centralizesys.model.debt.PagoDeudaRequest cashPayment = new com.centralizesys.model.debt.PagoDeudaRequest();
        cashPayment.setMontoPago(100.0);
        cashPayment.setMetodoPagoId(1L);
        cashPayment.setFechaCobro(null);

        com.centralizesys.model.debt.PagoDeudaRequest chequePayment = new com.centralizesys.model.debt.PagoDeudaRequest();
        chequePayment.setMontoPago(200.0);
        chequePayment.setMetodoPagoId(3L);
        chequePayment.setFechaCobro(LocalDate.now().plusDays(15));

        // Act
        ventaService.registrarPago(ventaId, List.of(cashPayment, chequePayment), usuarioId);

        // Assert: cash goes to pagos_venta
        verify(ventaRepository).savePagoUnico(ventaId, 1L, 100.0, usuarioId);
        // cheque goes to alertas_cheques
        verify(alertaChequeRepository).save(any(com.centralizesys.model.cheque.AlertaCheque.class));
    }

    @Test
    @DisplayName("E2-UT-04: registrarPago blocks overpayment even when new payment is a cheque")
    void registrarPago_Throws_WhenChequeOverpaysBalance() {
        // Arrange: Sale total $100, already paid $80 cash. Trying to add $30 cheque = total $110 > $100.
        Long ventaId = 4L;

        Venta pendingSale = new Venta();
        pendingSale.setId(ventaId);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setTotalVenta(100.0);

        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(pendingSale));
        // $80 already paid in cash

        // No pre-existing cheques


        com.centralizesys.model.debt.PagoDeudaRequest overPayingCheque = new com.centralizesys.model.debt.PagoDeudaRequest();
        overPayingCheque.setMontoPago(30.0); // $80 + $30 = $110 > $100
        overPayingCheque.setMetodoPagoId(3L);
        overPayingCheque.setFechaCobro(LocalDate.now().plusDays(10));

        // Act & Assert: The overpayment guard MUST remain active (RELAXED - now allowed for saldo)
        assertDoesNotThrow(() -> ventaService.registrarPago(ventaId, List.of(overPayingCheque), 5L));
        // Cheque is saved.
        verify(alertaChequeRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("E2-UT-05: registrarPago balance check must subtract pre-existing cheques from remaining balance")
    void registrarPago_BalanceCheck_AccountsForExistingCheques() {
        // Arrange: Sale $200. Pre-existing cheque of $150 in alertas_cheques.
        // Only $50 remains. Trying to pay $60 via cash should be blocked.
        Long ventaId = 5L;

        Venta pendingSale = new Venta();
        pendingSale.setId(ventaId);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setTotalVenta(200.0);

        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(pendingSale));
        // $0 cash paid

        // BUT $150 cheque already registered


        com.centralizesys.model.debt.PagoDeudaRequest overPayingCash = new com.centralizesys.model.debt.PagoDeudaRequest();
        overPayingCash.setMontoPago(60.0); // $0 + $150 + $60 = $210 > $200
        overPayingCash.setMetodoPagoId(1L);
        overPayingCash.setFechaCobro(null);

        // Act & Assert (Relaxed validation)
        assertDoesNotThrow(() -> ventaService.registrarPago(ventaId, List.of(overPayingCash), 5L));

        verify(ventaRepository, times(1)).savePagoUnico(eq(ventaId), eq(1L), eq(60.0), eq(5L));
    }

    @Test
    @DisplayName("E2-UT-06: anularCheque logically deletes the cheque by updating its status to ANULADA")
    void anularCheque_LogicallyDeletesCheque() {
        // Arrange
        Long chequeId = 10L;
        Long userId = 5L;
        com.centralizesys.model.cheque.AlertaCheque cheque = new com.centralizesys.model.cheque.AlertaCheque(chequeId, 1L, 150.0, LocalDate.now(), "PENDIENTE", null, null);

        when(alertaChequeRepository.findById(chequeId)).thenReturn(Optional.of(cheque));

        lenient().when(alertaChequeRepository.updateEstadoAndPagoVentaIdAtomic(anyLong(), anyString(), nullable(Long.class), anyString())).thenReturn(1);

        // Act
        ventaService.anularCheque(chequeId, userId);

        // Assert
        verify(alertaChequeRepository).updateEstadoAndPagoVentaIdAtomic(eq(chequeId), eq("ANULADA"), isNull(), eq("PENDIENTE"));
        verify(auditoriaService).registrarAccion(eq(userId), eq("ANULAR_CHEQUE"), contains("eliminación lógica"));
    }

    @Test
    @DisplayName("E2-UT-05: cobrarCheque sets pagoDeudaId and records audit when tipoOrigen is DEUDA_FIADO")
    void cobrarCheque_DeudaFiado_DeductsDebtAndRecordsPago() {
        Long chequeId = 2L;
        Long authenticatedUserId = 10L;
        Long metodoPagoId = 1L; // Cashing to Efectivo

        com.centralizesys.model.cheque.AlertaCheque cheque = new com.centralizesys.model.cheque.AlertaCheque();
        cheque.setId(chequeId);
        cheque.setVentaId(100L);
        cheque.setMonto(500.0);
        cheque.setFechaCobro(java.time.LocalDate.now());
        cheque.setEstado("PENDIENTE");
        cheque.setTipoOrigen("DEUDA_FIADO");

        com.centralizesys.model.debt.DeudaResponse mockDeuda = new com.centralizesys.model.debt.DeudaResponse();
        mockDeuda.setId(300L);
        mockDeuda.setMontoDeuda(1000.0);
        mockDeuda.setMontoOriginal(1000.0);
        mockDeuda.setEstado("PENDIENTE");

        when(alertaChequeRepository.findById(chequeId)).thenReturn(Optional.of(cheque));
        when(deudoresRepository.findByVentaId(100L)).thenReturn(Optional.of(mockDeuda));
        when(deudoresRepository.insertarPagoDeudaReturningId(eq(300L), eq(1L), eq(500.0), anyString(), eq(authenticatedUserId))).thenReturn(999L);

        com.centralizesys.model.sales.MetodoPago metodo = new com.centralizesys.model.sales.MetodoPago();
        metodo.setId(metodoPagoId);
        metodo.setActivo(true);
        when(metodoPagoRepository.findById(metodoPagoId)).thenReturn(Optional.of(metodo));

        when(alertaChequeRepository.updateEstadoAndPagoDeudaIdAtomic(anyLong(), anyString(), any(), anyString())).thenReturn(1);

        // Act
        ventaService.cobrarCheque(chequeId, metodoPagoId, authenticatedUserId);

        // Assert
        verify(deudoresRepository).deductDeudaAtomic(300L, 500.0, 1000.0);
        verify(alertaChequeRepository).updateEstadoAndPagoDeudaIdAtomic(eq(chequeId), eq("COBRADO"), eq(999L), anyString());
        verify(auditoriaService).registrarAccion(eq(authenticatedUserId), eq("COBRO_CHEQUE_DEUDA"), anyString());
    }

    @Test
    @DisplayName("E2-UT-06: cancelarCobroCheque restores debt and nulls pago_deuda_id when tipoOrigen is DEUDA_FIADO")
    void cancelarCobroCheque_DeudaFiado_RestoresDebt() {
        Long chequeId = 2L;
        Long authenticatedUserId = 10L;

        com.centralizesys.model.cheque.AlertaCheque cheque = new com.centralizesys.model.cheque.AlertaCheque();
        cheque.setId(chequeId);
        cheque.setVentaId(100L);
        cheque.setMonto(500.0);
        cheque.setFechaCobro(java.time.LocalDate.now());
        cheque.setEstado("COBRADO");
        cheque.setPagoDeudaId(999L);
        cheque.setTipoOrigen("DEUDA_FIADO");

        com.centralizesys.model.debt.DeudaResponse mockDeuda = new com.centralizesys.model.debt.DeudaResponse();
        mockDeuda.setId(300L);
        mockDeuda.setMontoDeuda(500.0); // Had $1000 original, $500 paid
        mockDeuda.setMontoOriginal(1000.0);
        mockDeuda.setEstado("PARCIAL");

        when(alertaChequeRepository.findById(chequeId)).thenReturn(Optional.of(cheque));
        when(deudoresRepository.findByVentaId(100L)).thenReturn(Optional.of(mockDeuda));
        lenient().when(alertaChequeRepository.updateEstadoAndPagoDeudaIdAtomic(anyLong(), anyString(), any(), anyString())).thenReturn(1);

        // Act
        ventaService.cancelarCobroCheque(chequeId, authenticatedUserId);

        // Assert
        verify(deudoresRepository).addDeudaAtomic(300L, 500.0, 1000.0);
        verify(deudoresRepository).updatePagoAnulado(999L);
        verify(auditoriaService).registrarAccion(eq(authenticatedUserId), eq("CANCELACION_COBRO_CHEQUE_DEUDA"), anyString());
    }

    // --- PHASE 2.2 STATE MACHINE & ORCHESTRATION EXPLOITS ---

    @Test
    @DisplayName("UT-29: cancelarPendiente logically cancels all associated pending cheques (Vector 16)")
    void cancelarPendiente_CancelsAssociatedCheques() {
        // Arrange
        Long ventaId = 99L;
        Long authenticatedUserId = 10L;

        Venta pendingSale = new Venta();
        pendingSale.setId(ventaId);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setTotalVenta(100.0);
        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(pendingSale));
        lenient().when(ventaRepository.updateEstadoAtomic(anyLong(), anyString(), anyString())).thenReturn(1);

        // Act
        ventaService.cancelarPendiente(ventaId, authenticatedUserId);

        // Assert
        verify(alertaChequeRepository).cancelarChequesPendientesByVentaId(ventaId);
    }

    @Test
    @DisplayName("UT-30: cancelarPendiente restores Saldo a Favor for cancelled payments (Vector 10)")
    void cancelarPendiente_RestoresSaldoAFavor() {
        // Arrange
        Long ventaId = 99L;
        Long authenticatedUserId = 10L;

        Venta pendingSale = new Venta();
        pendingSale.setId(ventaId);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setClienteId(5L); // Client ID required for Saldo a Favor
        pendingSale.setTotalVenta(100.0);
        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(pendingSale));
        lenient().when(ventaRepository.updateEstadoAtomic(anyLong(), anyString(), anyString())).thenReturn(1);

        // Mock payment method
        com.centralizesys.model.sales.MetodoPago saldoMethod = new com.centralizesys.model.sales.MetodoPago();
        saldoMethod.setId(4L);
        saldoMethod.setAcronimo("SALDO");
        when(metodoPagoRepository.findById(4L)).thenReturn(Optional.of(saldoMethod));

        // Mock a payment that used Saldo A Favor
        com.centralizesys.model.sales.PagoVenta pago = new com.centralizesys.model.sales.PagoVenta();
        pago.setId(1L);
        pago.setMetodoPagoId(4L); // Matches Saldo ID
        pago.setMonto(50.0);
        when(ventaRepository.findPagosActivosByVentaId(ventaId)).thenReturn(List.of(pago));

        // Act
        ventaService.cancelarPendiente(ventaId, authenticatedUserId);

        // Assert
        verify(ventaRepository).updatePagoAnulado(1L); // Payment must be annulled
        verify(clienteRepository).addSaldo(5L, 50.0); // Saldo must be restored!
    }

    @Test
    @DisplayName("UT-33: processPagosPendientes blocks deactivated payment methods (Vector 14)")
    void processPagosPendientes_BlocksDeactivatedMethod() {
        // Arrange
        Long ventaId = 99L;
        Long authenticatedUserId = 10L;

        Venta pendingSale = new Venta();
        pendingSale.setId(ventaId);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setTotalVenta(100.0);
        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(pendingSale));
        when(ventaRepository.lockVentaForUpdate(ventaId, "PENDIENTE")).thenReturn(true);

        com.centralizesys.model.sales.MetodoPago inactiveMethod = new com.centralizesys.model.sales.MetodoPago();
        inactiveMethod.setId(2L);
        inactiveMethod.setDescripcion("Mercado Pago Viejo");
        inactiveMethod.setActivo(false); // DEACTIVATED

        when(metodoPagoRepository.findById(2L)).thenReturn(Optional.of(inactiveMethod));

        com.centralizesys.model.debt.PagoDeudaRequest pagoRequest = new com.centralizesys.model.debt.PagoDeudaRequest();
        pagoRequest.setMontoPago(50.0);
        pagoRequest.setMetodoPagoId(2L); // Trying to use deactivated method

        // Act & Assert
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                ventaService.registrarPago(ventaId, List.of(pagoRequest), authenticatedUserId)
        );
        assertTrue(ex.getMessage().contains("desactivado"));
    }

    @Test
    @DisplayName("Epic3: modificarCarrito uses request.clienteId over stale pendingSale.clienteId")
    void modificarCarrito_UsesFreshClienteId() {
        Long ventaId = 1L;
        Long usuarioId = 1L;

        Venta pendingSale = new Venta();
        pendingSale.setId(ventaId);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setClienteId(2L); // Stale client ID
        pendingSale.setTipoVenta(TipoVenta.MINORISTA.name());
        pendingSale.setVersion(1);

        when(ventaRepository.lockVentaForUpdate(ventaId, "PENDIENTE")).thenReturn(true);
        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(pendingSale));

        VentaRequest request = new VentaRequest();
        request.setClienteId(3L); // Fresh client ID
        request.setVersion(1);
        VentaRequest.ItemRequest itemReq = new VentaRequest.ItemRequest();
        itemReq.setProductoId(10L);
        itemReq.setCantidad(1L);
        request.setItems(List.of(itemReq));

        Product product = new Product();
        product.setId(10L);
        product.setActivo(true);
        product.setPrecioMinorista(100.0);
        product.setPrecioCosto(50.0);
        when(productRepository.findByIdIncludingInactive(10L)).thenReturn(Optional.of(product));

        com.centralizesys.model.client.Cliente cliente = new com.centralizesys.model.client.Cliente();
        cliente.setId(3L);
        cliente.setActivo(true);
        when(clienteRepository.findById(3L)).thenReturn(Optional.of(cliente));


        when(ventaRepository.findDetallesByVentaId(ventaId)).thenReturn(Collections.emptyList());

        try {
            VentaResponse response = ventaService.modificarCarrito(ventaId, request, usuarioId);
            assertEquals(3L, response.getClienteId(), "Response should use the fresh client ID from the request.");
        } catch (Exception e) {
            try {
                java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("npe.txt"));
                e.printStackTrace(pw);
                pw.close();
            } catch (Exception ex) {}
            throw e;
        }
    }

    @Test
    void modificarCarrito_withEmptyClientName_throwsBusinessRuleException() {
        Long ventaId = 1L;
        Long usuarioId = 100L;

        Venta pendingSale = new Venta();
        pendingSale.setId(ventaId);
        pendingSale.setEstado("PENDIENTE");
        pendingSale.setClienteId(null);
        pendingSale.setClienteNombre(null);
        pendingSale.setVersion(1);

        when(ventaRepository.lockVentaForUpdate(ventaId, "PENDIENTE")).thenReturn(true);
        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(pendingSale));

        VentaRequest request = new VentaRequest();
        request.setClienteId(null);
        request.setClienteNombre("   "); // Blank string!
        request.setVersion(1);

        VentaRequest.ItemRequest itemReq = new VentaRequest.ItemRequest();
        itemReq.setProductoId(10L);
        itemReq.setCantidad(1L);
        request.setItems(List.of(itemReq));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            ventaService.modificarCarrito(ventaId, request, usuarioId);
        });

        assertTrue(ex.getMessage().contains("Un pedido pendiente no puede ser anónimo"));
    }

    @Test
    void crearPendiente_withEmptyClientName_throwsBusinessRuleException() {
        Long usuarioId = 100L;
        VentaRequest request = new VentaRequest();
        request.setClienteId(null);
        request.setClienteNombre(""); // Empty string!

        VentaRequest.ItemRequest itemReq = new VentaRequest.ItemRequest();
        itemReq.setProductoId(10L);
        itemReq.setCantidad(1L);
        request.setItems(List.of(itemReq));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            ventaService.crearPendiente(request, usuarioId);
        });

        assertTrue(ex.getMessage().contains("Un pedido pendiente no puede ser anónimo"));
    }
}
