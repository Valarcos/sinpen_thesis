package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.returns.DevolucionRequest;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.model.sales.VentaResponse;
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
class VentaServiceReturnTest {

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
        lenient().when(metodoPagoRepository.findById(anyLong())).thenReturn(Optional.of(activeMethod));
        lenient().when(ventaRepository.lockVentaForUpdate(anyLong(), anyString())).thenReturn(true);
    }


    // =========================================================================
    // GROUP 9: PARTIAL RETURN (Devolucion Parcial) — TDD
    // =========================================================================

    // Helper: builds a minimal Venta for return flow tests.
    // Uses the @NoArgsConstructor + setters to avoid coupling to constructor arg order.
    private Venta buildActiveSale(Long id, Long clienteId) {
        Venta v = new Venta();
        v.setId(id);
        v.setEstado("ACTIVA");
        v.setClienteId(clienteId);
        v.setTotalVenta(100.0);
        return v;
    }

    // Helper: builds a DetalleVenta for return flow tests.
    private com.centralizesys.model.sales.DetalleVenta buildDetalle(Long id, Long ventaId, Long productoId, Long cantidad, Double precio) {
        com.centralizesys.model.sales.DetalleVenta d = new com.centralizesys.model.sales.DetalleVenta();
        d.setId(id);
        d.setVentaId(ventaId);
        d.setProductoId(productoId);
        d.setCantidad(cantidad);
        d.setPrecioUnitario(precio);
        d.setDescripcionSnapshot("Test Product");
        d.setAnulado(false);
        return d;
    }

    @Test
    @DisplayName("DEV-01: registrarDevolucionParcial - SALDO happy path: saves ledger record, restores stock, adds saldo")
    void devolucion_Saldo_HappyPath() {
        // Arrange
        Long ventaId = 1L;
        Long clienteId = 10L;
        Long detalleId = 5L;
        Long productoId = 99L;
        Long usuarioId = 2L;

        Venta venta = buildActiveSale(ventaId, clienteId);
        com.centralizesys.model.sales.DetalleVenta detalle = buildDetalle(detalleId, ventaId, productoId, 3L, 100.0);

        com.centralizesys.model.product.Location loc = new com.centralizesys.model.product.Location(1L, "Primary");

        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(venta));
        when(ventaRepository.findDetalleByIdForUpdate(detalleId)).thenReturn(Optional.of(detalle));
        // First call: validation (0 already returned → 3 remaining → 2 fits)
        // Second call: auto-annulment check (after save, simulate 2 of 3 returned → NOT full)
        when(devolucionesRepository.sumCantidadDevueltaByDetalleId(detalleId)).thenReturn(0L, 2L);
        when(stockRepository.findAllLocations()).thenReturn(List.of(loc));
        when(ventaRepository.lockVentaForReturn(anyLong())).thenReturn(true);
        lenient().when(ventaRepository.updateEstadoSafeReturn(anyLong(), anyString())).thenReturn(1);
        lenient().when(deudoresRepository.findByVentaId(ventaId)).thenReturn(Optional.empty());
        when(ventaRepository.findDetallesByVentaId(ventaId)).thenReturn(List.of(detalle));

        DevolucionRequest request = new DevolucionRequest();
        DevolucionRequest.DevolucionItemRequest item = new DevolucionRequest.DevolucionItemRequest();
        item.setDetalleVentaId(detalleId);
        item.setCantidadDevuelta(2L); // Return 2 of 3
        request.setItems(List.of(item));
        request.setTipoReembolso("SALDO");

        // Act
        ventaService.registrarDevolucionParcial(ventaId, request, usuarioId);

        // Assert: ledger record saved
        verify(devolucionesRepository).save(eq(ventaId), eq(detalleId), eq(2L), eq(200.0), eq("SALDO"), any(), eq(usuarioId));
        // Assert: stock returned to primary location
        verify(stockRepository).addStock(productoId, 1L, 2L);
        // Assert: saldo added to client
        verify(clienteRepository).addSaldo(clienteId, 200.0);
        // Assert: NOT fully annulled (1 unit still left)
        verify(ventaRepository, never()).updateEstadoSafeReturn(ventaId, "ANULADA");
    }

    @Test
    @DisplayName("DEV-02: registrarDevolucionParcial - EFECTIVO happy path: saves negative payment entry")
    void devolucion_Efectivo_HappyPath() {
        // Arrange
        Long ventaId = 1L;
        Long detalleId = 5L;
        Long productoId = 99L;
        Long usuarioId = 2L;
        Long efectivoMetodoId = 3L;

        Venta venta = buildActiveSale(ventaId, null);
        com.centralizesys.model.sales.DetalleVenta detalle = buildDetalle(detalleId, ventaId, productoId, 2L, 50.0);
        com.centralizesys.model.product.Location loc = new com.centralizesys.model.product.Location(1L, "Primary");
        com.centralizesys.model.sales.MetodoPago efectivo = new com.centralizesys.model.sales.MetodoPago(efectivoMetodoId, "E", "Efectivo", true);

        when(ventaRepository.lockVentaForReturn(anyLong())).thenReturn(true);
        lenient().when(ventaRepository.updateEstadoSafeReturn(anyLong(), anyString())).thenReturn(1);
        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(venta));
        when(ventaRepository.findDetalleByIdForUpdate(detalleId)).thenReturn(Optional.of(detalle));
        when(devolucionesRepository.sumCantidadDevueltaByDetalleId(detalleId)).thenReturn(0L);
        when(stockRepository.findAllLocations()).thenReturn(List.of(loc));
        lenient().when(deudoresRepository.findByVentaId(ventaId)).thenReturn(Optional.empty());
        when(metodoPagoRepository.findByAcronimo("E")).thenReturn(Optional.of(efectivo));
        when(ventaRepository.findDetallesByVentaId(ventaId)).thenReturn(List.of(detalle));
        when(devolucionesRepository.sumCantidadDevueltaByDetalleId(detalleId)).thenReturn(1L); // Not fully returned

        DevolucionRequest request = new DevolucionRequest();
        DevolucionRequest.DevolucionItemRequest item = new DevolucionRequest.DevolucionItemRequest();
        item.setDetalleVentaId(detalleId);
        item.setCantidadDevuelta(1L);
        request.setItems(List.of(item));
        request.setTipoReembolso("EFECTIVO");

        // Act
        ventaService.registrarDevolucionParcial(ventaId, request, usuarioId);

        // Assert: negative payment saved
        verify(ventaRepository).saveNegativoPago(ventaId, efectivoMetodoId, 50.0, usuarioId);
        // Assert: saldo NOT touched
        verify(clienteRepository, never()).addSaldo(anyLong(), anyDouble());
    }

    @Test
    @DisplayName("DEV-03: registrarDevolucionParcial throws when sale is not ACTIVA")
    void devolucion_Throws_WhenSaleNotActiva() {
        Venta venta = new Venta();
        venta.setId(1L);
        venta.setEstado("ANULADA");
        when(ventaRepository.lockVentaForReturn(anyLong())).thenReturn(true);
        lenient().when(ventaRepository.updateEstadoSafeReturn(anyLong(), anyString())).thenReturn(1);
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(venta));

        DevolucionRequest request = new DevolucionRequest();
        DevolucionRequest.DevolucionItemRequest item = new DevolucionRequest.DevolucionItemRequest();
        item.setDetalleVentaId(1L);
        item.setCantidadDevuelta(1L);
        request.setItems(List.of(item));
        request.setTipoReembolso("SALDO");

        assertThrows(BusinessRuleException.class,
                () -> ventaService.registrarDevolucionParcial(1L, request, 1L));
    }

    @Test
    @DisplayName("DEV-04: registrarDevolucionParcial throws when detalle belongs to a different sale (security check)")
    void devolucion_Throws_WhenDetalleNotOwnedBySale() {
        Long ventaId = 1L;
        Long detalleId = 5L;

        Venta venta = buildActiveSale(ventaId, null);
        com.centralizesys.model.sales.DetalleVenta detalle = buildDetalle(detalleId, 999L /* Different sale */, 1L, 2L, 50.0);
        com.centralizesys.model.product.Location loc = new com.centralizesys.model.product.Location(1L, "Primary");

        when(ventaRepository.lockVentaForReturn(anyLong())).thenReturn(true);
        lenient().when(ventaRepository.updateEstadoSafeReturn(anyLong(), anyString())).thenReturn(1);
        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(venta));
        when(ventaRepository.findDetalleByIdForUpdate(detalleId)).thenReturn(Optional.of(detalle));
        lenient().when(deudoresRepository.findByVentaId(ventaId)).thenReturn(Optional.empty());
        // Note: stockRepository and devolucionesRepository stubs are NOT registered here because
        // the ownership check fires BEFORE stock is ever consulted, making them unnecessary.
        when(stockRepository.findAllLocations()).thenReturn(List.of(loc));

        DevolucionRequest request = new DevolucionRequest();
        DevolucionRequest.DevolucionItemRequest item = new DevolucionRequest.DevolucionItemRequest();
        item.setDetalleVentaId(detalleId);
        item.setCantidadDevuelta(1L);
        request.setItems(List.of(item));
        request.setTipoReembolso("SALDO");

        assertThrows(BusinessRuleException.class,
                () -> ventaService.registrarDevolucionParcial(ventaId, request, 1L));
    }

    @Test
    @DisplayName("DEV-05: registrarDevolucionParcial throws when return qty exceeds net remaining qty")
    void devolucion_Throws_WhenQuantityExceedsNetRemaining() {
        Long ventaId = 1L;
        Long detalleId = 5L;

        Venta venta = buildActiveSale(ventaId, null);
        // Original qty: 3, already returned: 2, remaining: 1. Trying to return 2.
        com.centralizesys.model.sales.DetalleVenta detalle = buildDetalle(detalleId, ventaId, 1L, 3L, 50.0);
        com.centralizesys.model.product.Location loc = new com.centralizesys.model.product.Location(1L, "Primary");

        when(ventaRepository.lockVentaForReturn(anyLong())).thenReturn(true);
        lenient().when(ventaRepository.updateEstadoSafeReturn(anyLong(), anyString())).thenReturn(1);
        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(venta));
        when(ventaRepository.findDetalleByIdForUpdate(detalleId)).thenReturn(Optional.of(detalle));
        when(stockRepository.findAllLocations()).thenReturn(List.of(loc));
        lenient().when(deudoresRepository.findByVentaId(ventaId)).thenReturn(Optional.empty());
        when(devolucionesRepository.sumCantidadDevueltaByDetalleId(detalleId)).thenReturn(2L); // 2 already returned

        DevolucionRequest request = new DevolucionRequest();
        DevolucionRequest.DevolucionItemRequest item = new DevolucionRequest.DevolucionItemRequest();
        item.setDetalleVentaId(detalleId);
        item.setCantidadDevuelta(2L); // Only 1 remaining
        request.setItems(List.of(item));
        request.setTipoReembolso("SALDO");

        assertThrows(BusinessRuleException.class,
                () -> ventaService.registrarDevolucionParcial(ventaId, request, 1L));
    }

    @Test
    @DisplayName("DEV-06: registrarDevolucionParcial throws EFECTIVO when sale has active debt")
    void devolucion_Throws_EfectivoWhenDebtExists() {
        Long ventaId = 1L;
        Long detalleId = 5L;

        Venta venta = buildActiveSale(ventaId, null);
        com.centralizesys.model.debt.DeudaResponse deuda = new com.centralizesys.model.debt.DeudaResponse(
                1L, ventaId, "Client", 1L, 50.0, java.time.LocalDateTime.now(), "PENDIENTE", 100.0, null);

        when(ventaRepository.lockVentaForReturn(anyLong())).thenReturn(true);
        lenient().when(ventaRepository.updateEstadoSafeReturn(anyLong(), anyString())).thenReturn(1);
        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(venta));
        lenient().when(deudoresRepository.findByVentaId(ventaId)).thenReturn(Optional.of(deuda));

        DevolucionRequest request = new DevolucionRequest();
        DevolucionRequest.DevolucionItemRequest item = new DevolucionRequest.DevolucionItemRequest();
        item.setDetalleVentaId(detalleId);
        item.setCantidadDevuelta(1L);
        request.setItems(List.of(item));
        request.setTipoReembolso("EFECTIVO"); // MUST be rejected

        assertThrows(BusinessRuleException.class,
                () -> ventaService.registrarDevolucionParcial(ventaId, request, 1L));
    }
    @Test
    @DisplayName("DEV-07: registrarDevolucionParcial throws when SALDO is used and sale has no clienteId")
    void devolucion_Throws_SaldoWhenNoClienteId() {
        // Arrange
        Long ventaId = 1L;
        Venta venta = new Venta();
        venta.setId(ventaId);
        venta.setEstado("ACTIVA");
        venta.setClienteId(null); // Anonymous legacy sale
        venta.setTotalVenta(100.0);

        DetalleVenta detalle = new DetalleVenta();
        detalle.setId(10L);
        detalle.setVentaId(ventaId);
        detalle.setCantidad(5L);
        detalle.setPrecioUnitario(100.0);
        detalle.setAnulado(false);

        DevolucionRequest request = new DevolucionRequest();
        DevolucionRequest.DevolucionItemRequest itemReq = new DevolucionRequest.DevolucionItemRequest();
        itemReq.setDetalleVentaId(10L);
        itemReq.setCantidadDevuelta(2L);
        request.setItems(List.of(itemReq));
        request.setTipoReembolso("SALDO");

        when(ventaRepository.lockVentaForReturn(anyLong())).thenReturn(true);
        lenient().when(ventaRepository.updateEstadoSafeReturn(anyLong(), anyString())).thenReturn(1);
        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(venta));
        lenient().when(deudoresRepository.findByVentaId(ventaId)).thenReturn(Optional.empty());

        com.centralizesys.model.product.Location loc = new com.centralizesys.model.product.Location();
        loc.setId(1L);
        when(stockRepository.findAllLocations()).thenReturn(List.of(loc));

        when(ventaRepository.findDetalleByIdForUpdate(10L)).thenReturn(Optional.of(detalle));
        when(devolucionesRepository.sumCantidadDevueltaByDetalleId(10L)).thenReturn(0L);

        // Act & Assert
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> ventaService.registrarDevolucionParcial(ventaId, request, 1L));
        assertEquals("No se puede reembolsar a Saldo a Favor porque la venta no tiene un cliente asociado.", ex.getMessage());
    }

    @Test
    @DisplayName("getVentasByClienteId maps results to VentaResponse list")
    void getVentasByClienteId_ReturnsMappedList() {
        // Arrange
        Long clienteId = 1L;
        Venta v1 = new Venta();
        v1.setId(10L);
        v1.setUsuarioId(2L);
        v1.setClienteNombre("John Doe");

        when(ventaRepository.findVentasByClienteId(clienteId)).thenReturn(List.of(v1));
        when(ventaRepository.findVendedorNombre(2L)).thenReturn("Admin");

        // Act
        List<VentaResponse> result = ventaService.getVentasByClienteId(clienteId);

        // Assert
        assertEquals(1, result.size());
        assertEquals(10L, result.getFirst().getId());
        assertEquals("John Doe", result.getFirst().getClienteNombre());
        assertEquals("Admin", result.getFirst().getVendedorNombre());
    }
    @Test
    @DisplayName("DEV-08: registrarDevolucionParcial transitions to DEVUELTA_PARCIAL when partial items remain")
    void devolucion_TransitionsToDevueltaParcial() {
        // Arrange
        Long ventaId = 1L;
        Long clienteId = 10L;
        Long detalleId = 5L;

        Venta venta = buildActiveSale(ventaId, clienteId);
        com.centralizesys.model.sales.DetalleVenta detalle = buildDetalle(detalleId, ventaId, 99L, 3L, 100.0);

        when(ventaRepository.lockVentaForReturn(anyLong())).thenReturn(true);
        lenient().when(ventaRepository.updateEstadoSafeReturn(anyLong(), anyString())).thenReturn(1);
        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(venta));
        when(ventaRepository.findDetalleByIdForUpdate(detalleId)).thenReturn(Optional.of(detalle));
        // First check returns 0 returned, so 3 remaining.
        // Second check (after this save) simulates returning 1 item, so 1 out of 3 returned, meaning NOT full return.
        when(devolucionesRepository.sumCantidadDevueltaByDetalleId(detalleId)).thenReturn(0L, 1L);
        when(ventaRepository.findDetallesByVentaId(ventaId)).thenReturn(List.of(detalle));

        com.centralizesys.model.product.Location loc = new com.centralizesys.model.product.Location(1L, "Primary");
        when(stockRepository.findAllLocations()).thenReturn(List.of(loc));
        when(metodoPagoRepository.findByAcronimo("E")).thenReturn(Optional.of(new com.centralizesys.model.sales.MetodoPago(1L, "Efectivo", "E", true)));

        List<com.centralizesys.model.returns.DevolucionRequest.DevolucionItemRequest> reqDetalles = new java.util.ArrayList<>();
        com.centralizesys.model.returns.DevolucionRequest.DevolucionItemRequest dReq = new com.centralizesys.model.returns.DevolucionRequest.DevolucionItemRequest();
        dReq.setDetalleVentaId(detalleId);
        dReq.setCantidadDevuelta(1L);
        reqDetalles.add(dReq);

        com.centralizesys.model.returns.DevolucionRequest request = new com.centralizesys.model.returns.DevolucionRequest();
        request.setItems(reqDetalles);
        request.setTipoReembolso("EFECTIVO");

        // Act
        ventaService.registrarDevolucionParcial(ventaId, request, 2L);

        // Assert
        verify(ventaRepository).updateEstadoSafeReturn(ventaId, "DEVUELTA_PARCIAL");
    }

    @Test
    @DisplayName("DEV-09: anularVentaHistorica avoids double-restock by deducting cantidadDevuelta")
    void anularVentaHistorica_AvoidsDoubleRestock() {
        // Arrange
        Long ventaId = 1L;
        Venta venta = buildActiveSale(ventaId, 10L);
        com.centralizesys.model.sales.DetalleVenta detalle = buildDetalle(5L, ventaId, 99L, 5L, 100.0);

        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(venta));
        when(ventaRepository.findDetallesByVentaId(ventaId)).thenReturn(List.of(detalle));
        when(devolucionesRepository.sumCantidadDevueltaByDetalleId(5L)).thenReturn(2L); // 2 out of 5 were already returned and restocked

        com.centralizesys.model.product.Location loc = new com.centralizesys.model.product.Location(1L, "Primary");
        when(stockRepository.findAllLocations()).thenReturn(List.of(loc));
        when(ventaRepository.updateEstadoAtomic(anyLong(), anyString(), anyString())).thenReturn(1);

        // Act
        ventaService.anularVentaHistorica(ventaId);

        // Assert
        // Original quantity was 5. Already returned was 2. So it should only restock 3.
        verify(stockRepository).addStock(99L, 1L, 3L);
        verify(ventaRepository).updateEstadoAtomic(ventaId, "ANULADA", "ACTIVA");
    }

    @Test
    @DisplayName("DEV-10: anularVentaHistorica throws when trying to fully annul a DEVUELTA_PARCIAL sale")
    void anularVentaHistorica_ThrowsOnDevueltaParcial() {
        // Arrange
        Long ventaId = 1L;
        Venta venta = buildActiveSale(ventaId, 10L);
        venta.setEstado("DEVUELTA_PARCIAL");

        when(ventaRepository.findById(ventaId)).thenReturn(Optional.of(venta));

        // Act & Assert
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                ventaService.anularVentaHistorica(ventaId));
        assertEquals("Solo se pueden anular ventas con estado ACTIVA.", ex.getMessage());
    }
}
