package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.product.StockLocation;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

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

    @InjectMocks
    private VentaService ventaService;

    // --- GROUP 1: INPUT VALIDATION (Public API) ---

    @Test
    @DisplayName("UT-01: registrarVenta throws BusinessRuleException when items list is null")
    void registrarVenta_Throws_WhenItemsNull() {
        VentaRequest request = new VentaRequest();
        request.setItems(null);

        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-01 (Var): registrarVenta throws BusinessRuleException when items list is empty")
    void registrarVenta_Throws_WhenItemsEmpty() {
        VentaRequest request = new VentaRequest();
        request.setItems(Collections.emptyList());

        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-02: registrarVenta throws ResourceNotFoundException when product does not exist")
    void registrarVenta_Throws_WhenProductNotFound() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(99L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));

        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> ventaService.registrarVenta(request));
    }

    // --- GROUP 2: PRICING & MATH LOGIC (Package-Private Testing) ---

    @Test
    @DisplayName("UT-03: processItems calculates discount correctly (Base - Discount)")
    void processItems_CalculatesDiscount_Correctly() {
        // Arrange
        Product product = new Product("A", "Test Product", 50.0, 80.0, 100.0);
        product.setId(1L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(2L);
        item.setValorDescuento(10.0); // 100 - 10 = 90

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // Act (Calling package-private method directly)
        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        // Assert
        assertEquals(180.0, result.getTotalVenta()); // 90 * 2
        DetalleVenta detail = result.getDetalles().getFirst();
        assertEquals(90.0, detail.getPrecioUnitario());
        assertEquals(10.0, detail.getDescuentoValor());
    }

    @Test
    @DisplayName("UT-16: processItems handles Zero or Null discount correctly")
    void processItems_HandlesZeroDiscount() {
        // Arrange
        Product product = new Product("A", "Test Product", 50.0, 80.0, 100.0);
        product.setId(1L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);
        item.setValorDescuento(null); // Should be treated as 0.0

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        // Act
        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        // Assert
        assertEquals(100.0, result.getTotalVenta());
        assertEquals(100.0, result.getDetalles().getFirst().getPrecioUnitario());
    }

    @Test
    @DisplayName("UT-04: processItems throws when discount is greater than price")
    void processItems_Throws_WhenDiscountExceedsPrice() {
        Product product = new Product("A", "Test", 50.0, 80.0, 100.0);
        product.setId(1L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);
        item.setValorDescuento(101.0); // Exceeds 100

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        List<VentaRequest.ItemRequest> items = List.of(item);

        assertThrows(BusinessRuleException.class, () -> ventaService.processItems(items, TipoVenta.MINORISTA));
    }

    @Test
    @DisplayName("UT-05: processItems throws when discount is negative")
    void processItems_Throws_WhenDiscountIsNegative() {
        Product product = new Product("A", "Test", 50.0, 80.0, 100.0);
        product.setId(1L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);
        item.setValorDescuento(-10.0);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        List<VentaRequest.ItemRequest> items = List.of(item);

        assertThrows(BusinessRuleException.class, () -> ventaService.processItems(items, TipoVenta.MINORISTA));
    }

    @Test
    @DisplayName("UT-06: processItems rounds total to two decimals")
    void processItems_RoundsTotal_ToTwoDecimals() {
        // Scenario: 3 items at 33.3333333...
        // We simulate this by having 1 product with a weird calculated price
        // OR simply 3 distinct items that sum up weirdly.
        // Let's use 1 item with quantity 1 and a calculated price that requires
        // rounding.
        // Wait, the logic is: Math.round(totalAcumulado * 100.0) / 100.0

        Product p1 = new Product("A", "P1", 10.0, 10.0, 10.555); // DB stores double
        p1.setId(1L);

        VentaRequest.ItemRequest i1 = new VentaRequest.ItemRequest();
        i1.setProductoId(1L);
        i1.setCantidad(1L);
        i1.setValorDescuento(0.0);

        when(productRepository.findById(1L)).thenReturn(Optional.of(p1));

        // Act
        var result = ventaService.processItems(List.of(i1), TipoVenta.MINORISTA);

        // Assert
        // 10.555 rounded should be 10.56
        assertEquals(10.56, result.getTotalVenta());
    }

    @Test
    @DisplayName("UT-06B: processItems uses Wholesale Price when TipoVenta is MAYORISTA")
    void processItems_UsesWholesalePrice() {
        // Arrange
        Product p = new Product("A", "P1", 50.0, 100.0, 150.0); // Cost, Wholesale, Retail
        p.setId(1L);

        VentaRequest.ItemRequest i1 = new VentaRequest.ItemRequest();
        i1.setProductoId(1L);
        i1.setCantidad(2L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // Act
        var result = ventaService.processItems(List.of(i1), TipoVenta.MAYORISTA);

        // Assert: 2 * 100.0 (Wholesale) = 200.0
        // If it used Retail, it would be 2 * 150.0 = 300.0
        assertEquals(200.0, result.getTotalVenta());
        assertEquals(100.0, result.getDetalles().getFirst().getPrecioUnitario());
    }

    // --- GLOBAL DISCOUNT TESTS ---

    @Test
    @DisplayName("UT-17: registrarVenta applies global discount correctly")
    void registrarVenta_AppliesGlobalDiscount() {
        // Arrange
        Product p = new Product("A", "P1", 50.0, 80.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        // Mock stock logic to avoid NPE
        when(stockRepository.findByProductId(anyLong())).thenReturn(List.of());
        when(ventaRepository.saveVenta(any())).thenReturn(1L);
        when(ventaRepository.findVendedorNombre(any())).thenReturn("Vendedora Test");

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(2L); // Subtotal: 200
        item.setValorDescuento(0.0);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setDescuentoGlobal(50.0); // 200 - 50 = 150
        request.setClienteNombre("Discount User");
        request.setUsuarioId(1L);

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertEquals(150.0, response.getTotalVenta());
        assertEquals(50.0, response.getDescuentoGlobal());
        assertEquals("Vendedora Test", response.getVendedorNombre());
    }

    @Test
    @DisplayName("UT-18: registrarVenta throws when global discount is negative")
    void registrarVenta_Throws_WhenGlobalDiscountNegative() {
        // Arrange
        Product p = new Product("A", "P1", 50.0, 80.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setDescuentoGlobal(-10.0);

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-19: registrarVenta throws when global discount exceeds subtotal")
    void registrarVenta_Throws_WhenGlobalDiscountExceedsTotal() {
        // Arrange
        Product p = new Product("A", "P1", 50.0, 80.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // Subtotal 100

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setDescuentoGlobal(101.0);

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    // --- GROUP 3: STOCK LOGIC (Package-Private Testing) ---

    @Test
    @DisplayName("UT-07: updateStock deducts from single location with sufficient stock")
    void deductStock_DeductsFromSingleLocation() {
        // Arrange
        Long prodId = 1L;
        Long qtyNeeded = 5L;
        StockLocation loc1 = new StockLocation(1L, prodId, 100L, "Depósito", 10L); // 10 available

        when(stockRepository.findByProductId(prodId)).thenReturn(List.of(loc1));

        // Act
        String alert = ventaService.deductStockFromInventory(prodId, "Test Product", qtyNeeded);

        // Assert
        assertNull(alert); // No alert expected
        verify(stockRepository).subtractStock(100L, prodId, 5L); // 100L is locId
    }

    @Test
    @DisplayName("UT-08: updateStock deducts across multiple locations")
    void deductStock_DeductsAcrossMultipleLocations() {
        // Arrange
        Long prodId = 1L;
        Long qtyNeeded = 10L;
        // Loc1 has 4, Loc2 has 8. Total 12. Enough.
        StockLocation loc1 = new StockLocation(1L, prodId, 101L, "Loc1", 4L);
        StockLocation loc2 = new StockLocation(2L, prodId, 102L, "Loc2", 8L);

        when(stockRepository.findByProductId(prodId)).thenReturn(List.of(loc1, loc2));

        // Act
        String alert = ventaService.deductStockFromInventory(prodId, "Test Product", qtyNeeded);

        // Assert
        assertNull(alert);
        // Should take 4 from Loc1
        verify(stockRepository).subtractStock(101L, prodId, 4L);
        // Should take remaining 6 from Loc2
        verify(stockRepository).subtractStock(102L, prodId, 6L);
    }

    @Test
    @DisplayName("UT-09: updateStock forces negative when stock insufficient (Alert)")
    void deductStock_ForcesNegative_WithAlert() {
        // Arrange
        Long prodId = 1L;
        Long qtyNeeded = 10L;
        // Only 3 available
        StockLocation loc1 = new StockLocation(1L, prodId, 101L, "Loc1", 3L);

        when(stockRepository.findByProductId(prodId)).thenReturn(List.of(loc1));

        // Act
        String alert = ventaService.deductStockFromInventory(prodId, "Socks", qtyNeeded);

        // Assert
        assertNotNull(alert);
        assertTrue(alert.contains("ATENCIÓN"));
        assertTrue(alert.contains("Socks"));

        // Should take ALL 3 first
        verify(stockRepository).subtractStock(101L, prodId, 3L);
        // Then force take the remaining 7 from the SAME location (first one found)
        verify(stockRepository).subtractStock(101L, prodId, 7L);
    }

    @Test
    @DisplayName("UT-10: updateStock returns critical alert when no location exists")
    void deductStock_CriticalAlert_NoLocation() {
        // Arrange
        Long prodId = 1L;
        when(stockRepository.findByProductId(prodId)).thenReturn(Collections.emptyList());

        // Act
        String alert = ventaService.deductStockFromInventory(prodId, "Socks", 1L);

        // Assert
        assertNotNull(alert);
        assertTrue(alert.contains("CRÍTICO"));
    }

    // --- GROUP 4: DEBT & ORCHESTRATION (Public API) ---
    // Since handleDebt is private, we verify it via the Orchestrator

    @Test
    @DisplayName("UT-11: handleDebt saves debt when payment is less than total")
    void registrarVenta_SavesDebt_Correctly() {
        // Arrange
        Product p = new Product("C", "Code", 50.0, 50.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
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
        verify(deudoresRepository).save(500L, "John Doe", 20.0);
    }

    @Test
    @DisplayName("UT-12: handleDebt throws exception when debt exists but no client name")
    void registrarVenta_Throws_WhenDebtButNoName() {
        // Arrange
        Product p = new Product("C", "Code", 50.0, 50.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
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
        Product p = new Product("C", "Code", 50.0, 50.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
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
        verify(deudoresRepository, never()).save(anyLong(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("UT-14 & UT-15: Full Flow Success + Audit")
    void registrarVenta_OrchestratesFullFlow_Success() {
        // Arrange
        Product p = new Product("C", "Code", 50.0, 50.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
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

    // --- GROUP 6: SOFT-DELETE PRODUCT GUARD ---

    @Test
    @DisplayName("UT-23: processItems throws BusinessRuleException when product is logically deleted (activo=false)")
    void processItems_Throws_WhenProductIsInactive() {
        // Arrange: Product exists in DB but is soft-deleted
        Product deletedProduct = new Product(1L, "OLD-CODE", "Producto Archivado", 50.0, 80.0, 100.0, 0L, false);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        // Simulate defence-in-depth: the service receives the inactive product object
        when(productRepository.findById(1L)).thenReturn(Optional.of(deletedProduct));

        List<VentaRequest.ItemRequest> items = List.of(item);

        // Act & Assert: must be BusinessRuleException, NOT ResourceNotFoundException
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> ventaService.processItems(items, TipoVenta.MINORISTA));

        assertTrue(ex.getMessage().contains("eliminado"),
                "Error message must indicate the product is archived, not missing");
        assertTrue(ex.getMessage().contains("Producto Archivado"),
                "Error message must include the product's description for user clarity");
    }

    // --- Portion 5: WAC & Stock Deduction Edge Cases ---

    @Test
    @DisplayName("processItems_SetsWACAsSnapshot_WithMultipleActiveVariants")
    void processItems_SetsWACAsSnapshot_WithMultipleActiveVariants() {
        Product p = new Product(1L, "WAC-CODE", "Desc", 10.0, 20.0, 30.0, 5L, true);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        // Return a WAC of $15.5
        when(productRepository.findWAC("WAC-CODE", null)).thenReturn(Optional.of(15.5));

        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        assertEquals(15.5, result.getDetalles().getFirst().getCostoSnapshot());
    }

    @Test
    @DisplayName("processItems_FallsBackToSelectedVariantCost_WhenWACIsNull")
    void processItems_FallsBackToSelectedVariantCost_WhenWACIsNull() {
        // Product's own cost is $25.0
        Product p = new Product(1L, "WAC-CODE", "Desc", 25.0, 20.0, 30.0, 0L, true);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        // DB says WAC is null (e.g. all stock is 0 or negative)
        when(productRepository.findWAC("WAC-CODE", null)).thenReturn(Optional.empty());

        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        // Should fallback to $25.0
        assertEquals(25.0, result.getDetalles().getFirst().getCostoSnapshot());
    }

    @Test
    @DisplayName("processItems_KeepsOriginalProductId_ForTraceability")
    void processItems_KeepsOriginalProductId_ForTraceability() {
        Product p = new Product(100L, "WAC-CODE", "Desc", 25.0, 20.0, 30.0, 5L, true);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(100L); // Item requests ID 100
        item.setCantidad(1L);

        when(productRepository.findById(100L)).thenReturn(Optional.of(p));
        when(productRepository.findWAC("WAC-CODE", null)).thenReturn(Optional.of(15.0));

        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        // The WAC doesn't change the fact that they explicitly sold variant ID 100
        assertEquals(100L, result.getDetalles().getFirst().getProductoId());
    }

    @Test
    @DisplayName("processItems_CorrectlyHandlesGenericProduct_WithDescriptionFilter")
    void processItems_CorrectlyHandlesGenericProduct_WithDescriptionFilter() {
        Product p = new Product(10L, "1", "Generic Item", 10.0, 20.0, 30.0, 5L, true);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(10L);
        item.setCantidad(1L);

        when(productRepository.findById(10L)).thenReturn(Optional.of(p));
        // Expect findWAC to be called with ("1", "Generic Item")
        when(productRepository.findWAC("1", "Generic Item")).thenReturn(Optional.of(12.0));

        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        assertEquals(12.0, result.getDetalles().getFirst().getCostoSnapshot());
        verify(productRepository).findWAC("1", "Generic Item");
    }

    @Test
    @DisplayName("deductStockFromInventory_PhantomLocation_CreatesNegativeRowOnFirstSystemLocation")
    void deductStockFromInventory_PhantomLocation_CreatesNegativeRowOnFirstSystemLocation() {
        Long prodId = 1L;
        Long qtyNeeded = 5L;

        // No stock locations exist for this specific product...
        when(stockRepository.findByProductId(prodId)).thenReturn(Collections.emptyList());
        // ...but there IS a valid location in the system
        com.centralizesys.model.product.Location fallbackLoc = new com.centralizesys.model.product.Location(1L, "Depósito Central");
        when(stockRepository.findAllLocations()).thenReturn(List.of(fallbackLoc));

        String alert = ventaService.deductStockFromInventory(prodId, "New Product", qtyNeeded);

        assertNotNull(alert);
        assertTrue(alert.contains("ATENCIÓN"));

        // Should CREATE the phantom row explicitly
        verify(stockRepository).addStock(prodId, 1L, -5L);
        // And NEVER call subtractStock because no rows existed
        verify(stockRepository, never()).subtractStock(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("deductStockFromInventory_PhantomLocation_ReturnsCriticoWhenNoSystemLocationsExist")
    void deductStockFromInventory_PhantomLocation_ReturnsCriticoWhenNoSystemLocationsExist() {
        Long prodId = 1L;

        when(stockRepository.findByProductId(prodId)).thenReturn(Collections.emptyList());
        // NO system locations at all (e.g. brand new install with empty DB)
        when(stockRepository.findAllLocations()).thenReturn(Collections.emptyList());

        String alert = ventaService.deductStockFromInventory(prodId, "New Product", 5L);

        assertNotNull(alert);
        assertTrue(alert.contains("CRÍTICO"));
        assertTrue(alert.contains("NINGUNA ubicación"));

        verify(stockRepository, never()).addStock(anyLong(), anyLong(), anyLong());
        verify(stockRepository, never()).subtractStock(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("updateStockFromDetails_CallsDeductForEachDetalleProductoId")
    void updateStockFromDetails_CallsDeductForEachDetalleProductoId() {
        // Create 2 completely different items
        DetalleVenta d1 = new DetalleVenta();
        d1.setProductoId(1L);
        d1.setDescripcionSnapshot("P1");
        d1.setCantidad(2L);

        DetalleVenta d2 = new DetalleVenta();
        d2.setProductoId(2L);
        d2.setDescripcionSnapshot("P2");
        d2.setCantidad(3L);

        // Setup locations to avoid logic branching into phantom code
        when(stockRepository.findByProductId(1L)).thenReturn(List.of(new StockLocation(1L, 1L, 100L, "L1", 10L)));
        when(stockRepository.findByProductId(2L)).thenReturn(List.of(new StockLocation(2L, 2L, 100L, "L1", 10L)));

        ventaService.updateStockFromDetails(List.of(d1, d2));

        // Verifies deduction logic triggered for BOTH
        verify(stockRepository).subtractStock(100L, 1L, 2L);
        verify(stockRepository).subtractStock(100L, 2L, 3L);
    }

    @Test
    @DisplayName("updateStockFromDetails_TwoDetailsWithSameProductoId_DeductsTwiceSeparately")
    void updateStockFromDetails_TwoDetailsWithSameProductoId_DeductsTwiceSeparately() {
        // Two details representing the exact SAME product ID
        DetalleVenta d1 = new DetalleVenta();
        d1.setProductoId(1L);
        d1.setDescripcionSnapshot("P1");
        d1.setCantidad(2L);

        DetalleVenta d2 = new DetalleVenta();
        d2.setProductoId(1L);
        d2.setDescripcionSnapshot("P1");
        d2.setCantidad(3L);

        when(stockRepository.findByProductId(1L)).thenReturn(List.of(new StockLocation(1L, 1L, 100L, "L1", 10L)));

        ventaService.updateStockFromDetails(List.of(d1, d2));

        // Should call subtractStock twice for product 1
        verify(stockRepository).subtractStock(100L, 1L, 2L);
        verify(stockRepository).subtractStock(100L, 1L, 3L);
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

        // When
        ventaService.cobrarCheque(chequeId, metodoPagoId, authenticatedUserId);

        // Then
        verify(alertaChequeRepository).updateEstadoAndPagoVentaId(chequeId, "COBRADO", 999L);
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

        // When
        ventaService.cancelarCobroCheque(chequeId, authenticatedUserId);

        // Then
        verify(ventaRepository).anularPagoVentaById(999L);
        verify(alertaChequeRepository).updateEstadoAndPagoVentaId(chequeId, "PENDIENTE", null);
        verify(auditoriaService).registrarAccion(eq(authenticatedUserId), eq("CANCELACION_COBRO_CHEQUE"), anyString());
    }

    // --- GROUP 7: EPIC 1 OVERPAYMENT RULES ---

    @Test
    @DisplayName("UT-22: registrarVenta throws when pagos + cheques > total")
    void registrarVenta_Throws_WhenOverpaid() {
        // Arrange
        Product p = new Product("C", "Code", 50.0, 50.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

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
        when(ventaRepository.findById(99L)).thenReturn(Optional.of(pendingSale));

        Product p = new Product("C", "Code", 50.0, 50.0, 100.0);
        p.setId(1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // New total: $100

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setClienteNombre("John");

        // Act
        assertDoesNotThrow(() -> ventaService.modificarCarrito(99L, request, 1L));
        verify(ventaRepository).updateTotalesConOCC(99L, 100.0, 0.0);
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
        when(alertaChequeRepository.sumMontoPendienteByVentaId(99L)).thenReturn(50.0); // 50 + 50 = 100

        // Act
        assertDoesNotThrow(() -> ventaService.finalizarVenta(99L, 1L));

        // Assert
        verify(ventaRepository).updateFechaAndEstado(eq(99L), any(), eq("ACTIVA"));
        // Debt repo should not save debt since paid in full
        verify(deudoresRepository, never()).save(anyLong(), anyString(), anyDouble());
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
        when(ventaRepository.sumPagosActivosByVentaId(ventaId)).thenReturn(0.0);
        // No pre-existing cheques for this venta
        when(alertaChequeRepository.sumMontoPendienteByVentaId(ventaId)).thenReturn(0.0);

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
        when(ventaRepository.sumPagosActivosByVentaId(ventaId)).thenReturn(0.0);
        when(alertaChequeRepository.sumMontoPendienteByVentaId(ventaId)).thenReturn(0.0);

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
        when(ventaRepository.sumPagosActivosByVentaId(ventaId)).thenReturn(0.0);
        when(alertaChequeRepository.sumMontoPendienteByVentaId(ventaId)).thenReturn(0.0);

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
        when(ventaRepository.sumPagosActivosByVentaId(ventaId)).thenReturn(80.0);
        // No pre-existing cheques
        when(alertaChequeRepository.sumMontoPendienteByVentaId(ventaId)).thenReturn(0.0);

        com.centralizesys.model.debt.PagoDeudaRequest overPayingCheque = new com.centralizesys.model.debt.PagoDeudaRequest();
        overPayingCheque.setMontoPago(30.0); // $80 + $30 = $110 > $100
        overPayingCheque.setMetodoPagoId(3L);
        overPayingCheque.setFechaCobro(LocalDate.now().plusDays(10));

        // Act & Assert: The overpayment guard MUST remain active
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> ventaService.registrarPago(ventaId, List.of(overPayingCheque), 5L));
        assertTrue(ex.getMessage().contains("saldo restante"),
                "Error must reference the remaining balance to help the cashier understand the issue");

        // Critical: must NOT save the cheque to alertas_cheques
        verify(alertaChequeRepository, never()).save(any());
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
        when(ventaRepository.sumPagosActivosByVentaId(ventaId)).thenReturn(0.0);
        // BUT $150 cheque already registered
        when(alertaChequeRepository.sumMontoPendienteByVentaId(ventaId)).thenReturn(150.0);

        com.centralizesys.model.debt.PagoDeudaRequest overPayingCash = new com.centralizesys.model.debt.PagoDeudaRequest();
        overPayingCash.setMontoPago(60.0); // $0 + $150 + $60 = $210 > $200
        overPayingCash.setMetodoPagoId(1L);
        overPayingCash.setFechaCobro(null);

        // Act & Assert
        assertThrows(BusinessRuleException.class,
                () -> ventaService.registrarPago(ventaId, List.of(overPayingCash), 5L),
                "System must account for pre-existing cheques in the remaining balance calculation");

        verify(ventaRepository, never()).savePagoUnico(anyLong(), anyLong(), anyDouble(), anyLong());
    }

    @Test
    @DisplayName("E2-UT-06: anularCheque logically deletes the cheque by updating its status to ANULADA")
    void anularCheque_LogicallyDeletesCheque() {
        // Arrange
        Long chequeId = 10L;
        Long userId = 5L;
        com.centralizesys.model.cheque.AlertaCheque cheque = new com.centralizesys.model.cheque.AlertaCheque(chequeId, 1L, 150.0, LocalDate.now(), "PENDIENTE", null, null);

        when(alertaChequeRepository.findById(chequeId)).thenReturn(Optional.of(cheque));

        // Act
        ventaService.anularCheque(chequeId, userId);

        // Assert
        verify(alertaChequeRepository).updateEstadoAndPagoVentaId(chequeId, "ANULADA", null);
        verify(auditoriaService).registrarAccion(eq(userId), eq("ANULAR_CHEQUE"), contains("eliminación lógica"));
    }
}
