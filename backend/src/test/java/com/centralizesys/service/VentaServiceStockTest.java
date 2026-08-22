package com.centralizesys.service;

import com.centralizesys.model.product.Product;
import com.centralizesys.model.product.StockLocation;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.VentaRequest;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class VentaServiceStockTest {

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

    // --- Portion 5: WAC & Stock Deduction Edge Cases ---

    @Test
    @DisplayName("processItems_SetsWACAsSnapshot_WithMultipleActiveVariants")
    void processItems_SetsWACAsSnapshot_WithMultipleActiveVariants() {
        Product p = Product.builder().id(1L).codigo("WAC-CODE").descripcion("Desc")
                .precioCosto(10.0).precioMayorista(20.0).precioMinorista(30.0)
                .cantidadStock(5L).activo(true).build();

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));
        // Return a WAC of $15.5
        when(productRepository.findWAC("WAC-CODE", null)).thenReturn(Optional.of(15.5));

        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        assertEquals(15.5, result.getDetalles().getFirst().getCostoSnapshot());
    }

    @Test
    @DisplayName("processItems_FallsBackToSelectedVariantCost_WhenWACIsNull")
    void processItems_FallsBackToSelectedVariantCost_WhenWACIsNull() {
        // Product's own cost is $25.0
        Product p = Product.builder().id(1L).codigo("WAC-CODE").descripcion("Desc")
                .precioCosto(25.0).precioMayorista(20.0).precioMinorista(30.0)
                .cantidadStock(0L).activo(true).build();

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));
        // DB says WAC is null (e.g. all stock is 0 or negative)
        when(productRepository.findWAC("WAC-CODE", null)).thenReturn(Optional.empty());

        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        // Should fallback to $25.0
        assertEquals(25.0, result.getDetalles().getFirst().getCostoSnapshot());
    }

    @Test
    @DisplayName("processItems_KeepsOriginalProductId_ForTraceability")
    void processItems_KeepsOriginalProductId_ForTraceability() {
        Product p = Product.builder().id(100L).codigo("WAC-CODE").descripcion("Desc")
                .precioCosto(25.0).precioMayorista(20.0).precioMinorista(30.0)
                .cantidadStock(5L).activo(true).build();

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(100L); // Item requests ID 100
        item.setCantidad(1L);

        when(productRepository.findByIdIncludingInactive(100L)).thenReturn(Optional.of(p));
        when(productRepository.findWAC("WAC-CODE", null)).thenReturn(Optional.of(15.0));

        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        // The WAC doesn't change the fact that they explicitly sold variant ID 100
        assertEquals(100L, result.getDetalles().getFirst().getProductoId());
    }

    @Test
    @DisplayName("processItems_CorrectlyHandlesGenericProduct_WithDescriptionFilter")
    void processItems_CorrectlyHandlesGenericProduct_WithDescriptionFilter() {
        Product p = Product.builder().id(10L).codigo("1").descripcion("Generic Item")
                .precioCosto(10.0).precioMayorista(20.0).precioMinorista(30.0)
                .cantidadStock(5L).activo(true).build();

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(10L);
        item.setCantidad(1L);

        when(productRepository.findByIdIncludingInactive(10L)).thenReturn(Optional.of(p));
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
}
