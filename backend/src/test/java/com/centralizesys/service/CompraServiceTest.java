package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.purchase.CompraItemRequest;
import com.centralizesys.model.purchase.CompraRequest;
import com.centralizesys.model.purchase.CompraResponse;
import com.centralizesys.repository.CompraRepository;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.StockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompraServiceTest {

    @Mock
    private CompraRepository compraRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private CompraService compraService;

    // --- GROUP 1: VALIDATION & GUARDS ---

    @Test
    @DisplayName("UT-01: registrarCompra throws BusinessRuleException when items list is null or empty")
    void registrarCompra_Throws_WhenItemsNullOrEmpty() {
        CompraRequest requestNull = new CompraRequest();
        requestNull.setItems(null);
        assertThrows(BusinessRuleException.class, () -> compraService.registrarCompra(requestNull));

        CompraRequest requestEmpty = new CompraRequest();
        requestEmpty.setItems(Collections.emptyList());
        assertThrows(BusinessRuleException.class, () -> compraService.registrarCompra(requestEmpty));
    }

    @Test
    @DisplayName("UT-02: validateItemInput throws BusinessRuleException for non-positive quantity")
    void validateItemInput_Throws_WhenQtyNonPositive() {
        // Zero Quantity
        CompraItemRequest itemZero = new CompraItemRequest();
        itemZero.setProductoId(1L);
        itemZero.setCantidad(0L);
        itemZero.setCostoUnitario(100.0);
        assertThrows(BusinessRuleException.class, () -> compraService.validateItemInput(itemZero));

        // Negative Quantity
        CompraItemRequest itemNeg = new CompraItemRequest();
        itemNeg.setProductoId(1L);
        itemNeg.setCantidad(-5L);
        itemNeg.setCostoUnitario(100.0);
        assertThrows(BusinessRuleException.class, () -> compraService.validateItemInput(itemNeg));

        // Null Quantity
        CompraItemRequest itemNull = new CompraItemRequest();
        itemNull.setProductoId(1L);
        itemNull.setCantidad(null);
        itemNull.setCostoUnitario(100.0);
        assertThrows(BusinessRuleException.class, () -> compraService.validateItemInput(itemNull));
    }

    @Test
    @DisplayName("UT-03: validateItemInput throws BusinessRuleException for non-positive cost")
    void validateItemInput_Throws_WhenCostNonPositive() {
        // Zero Cost
        CompraItemRequest itemZero = new CompraItemRequest();
        itemZero.setProductoId(1L);
        itemZero.setCantidad(1L);
        itemZero.setCostoUnitario(0.0);
        assertThrows(BusinessRuleException.class, () -> compraService.validateItemInput(itemZero));

        // Negative Cost
        CompraItemRequest itemNeg = new CompraItemRequest();
        itemNeg.setProductoId(1L);
        itemNeg.setCantidad(1L);
        itemNeg.setCostoUnitario(-10.0);
        assertThrows(BusinessRuleException.class, () -> compraService.validateItemInput(itemNeg));

        // Null Cost
        CompraItemRequest itemNull = new CompraItemRequest();
        itemNull.setProductoId(1L);
        itemNull.setCantidad(1L);
        itemNull.setCostoUnitario(null);
        assertThrows(BusinessRuleException.class, () -> compraService.validateItemInput(itemNull));
    }

    // --- GROUP 2: BUSINESS RULES (Cost Consistency) ---

    @Test
    @DisplayName("UT-04: validateCostConsistency throws when cost mismatches DB variant")
    void validateCostConsistency_Throws_WhenCostMismatch() {
        // DB says $100
        Product product = Product.builder().codigo("A").descripcion("Desc").precioCosto(100.0).precioMayorista(150.0).precioMinorista(200.0).build();
        product.setId(1L);

        // Request says $150
        assertThrows(BusinessRuleException.class,
                () -> compraService.validateCostConsistency(product, 150.0));
    }

    @Test
    @DisplayName("UT-05: validateCostConsistency passes when cost matches exact")
    void validateCostConsistency_Passes_WhenCostMatchesExact() {
        Product product = Product.builder().codigo("A").descripcion("Desc").precioCosto(100.0).precioMayorista(150.0).precioMinorista(200.0).build();
        assertDoesNotThrow(() -> compraService.validateCostConsistency(product, 100.0));
    }

    @Test
    @DisplayName("UT-06: validateCostConsistency passes when difference is microscopic (Epsilon)")
    void validateCostConsistency_Passes_WhenDifferenceIsMicroscopic() {
        Product product = Product.builder().codigo("A").descripcion("Desc").precioCosto(100.0).precioMayorista(150.0).precioMinorista(200.0).build();
        // 100.0000001 vs 100.0 -> Should pass
        assertDoesNotThrow(() -> compraService.validateCostConsistency(product, 100.0000001));
    }

    // --- GROUP 3: MATH & PROCESSING (Internal Logic) ---

    @Test
    @DisplayName("UT-07: processItems throws ResourceNotFoundException when product map is empty")
    void processItems_Throws_WhenProductNotFound_EmptyMap() {
        CompraItemRequest item = new CompraItemRequest();
        item.setProductoId(99L);
        item.setCantidad(1L);
        item.setCostoUnitario(10.0);

        Map<Long, Product> emptyMap = Collections.emptyMap();

        List<CompraItemRequest> items = List.of(item);

        assertThrows(ResourceNotFoundException.class,
                () -> compraService.processItems(items, emptyMap));
    }

    @Test
    @DisplayName("UT-08: processItems throws ResourceNotFoundException when map is partial")
    void processItems_Throws_WhenProductNotFound_PartialMap() {
        // Arrange: Request IDs 1 and 2
        CompraItemRequest item1 = new CompraItemRequest();
        item1.setProductoId(1L); item1.setCantidad(1L); item1.setCostoUnitario(10.0);

        CompraItemRequest item2 = new CompraItemRequest();
        item2.setProductoId(2L); item2.setCantidad(1L); item2.setCostoUnitario(10.0);

        // Map only contains ID 1
        Product p1 = Product.builder().codigo("A").descripcion("P1").precioCosto(10.0).precioMayorista(0.0).precioMinorista(0.0).build(); p1.setId(1L);
        Map<Long, Product> partialMap = Map.of(1L, p1);

        List<CompraItemRequest> items = List.of(item1, item2);

        // Act & Assert
        assertThrows(ResourceNotFoundException.class,
                () -> compraService.processItems(items, partialMap));
    }

    @Test
    @DisplayName("UT-09: processItems calculates totals and subtotals correctly")
    void processItems_CalculatesTotalAndSubtotals_Correctly() {
        // Arrange
        Product p1 = Product.builder().codigo("A").descripcion("P1").precioCosto(10.0).precioMayorista(0.0).precioMinorista(20.0).build(); p1.setId(1L);
        Product p2 = Product.builder().codigo("B").descripcion("P2").precioCosto(20.0).precioMayorista(0.0).precioMinorista(40.0).build(); p2.setId(2L);
        Map<Long, Product> productMap = Map.of(1L, p1, 2L, p2);

        CompraItemRequest i1 = new CompraItemRequest();
        i1.setProductoId(1L); i1.setCantidad(10L); i1.setCostoUnitario(10.0); // Subtotal 100

        CompraItemRequest i2 = new CompraItemRequest();
        i2.setProductoId(2L); i2.setCantidad(5L); i2.setCostoUnitario(20.0); // Subtotal 100

        // Act
        var result = compraService.processItems(List.of(i1, i2), productMap);

        // Assert
        assertEquals(200.0, result.getTotalCompra());
        assertEquals(2, result.getDetallesToSave().size());
        assertEquals(100.0, result.getDetallesToSave().get(0).getSubtotal());
        assertEquals(100.0, result.getDetallesToSave().get(1).getSubtotal());
    }

    @Test
    @DisplayName("UT-10: processItems handles Duplicate Items correctly (Aggregation)")
    void processItems_Handles_DuplicateItems_Correctly() {
        // Arrange
        Product p1 = Product.builder().codigo("A").descripcion("P1").precioCosto(10.0).precioMayorista(0.0).precioMinorista(20.0).build(); p1.setId(1L);
        Map<Long, Product> productMap = Map.of(1L, p1);

        // Add the SAME item twice
        CompraItemRequest i1 = new CompraItemRequest();
        i1.setProductoId(1L); i1.setCantidad(5L); i1.setCostoUnitario(10.0);

        CompraItemRequest i2 = new CompraItemRequest();
        i2.setProductoId(1L); i2.setCantidad(5L); i2.setCostoUnitario(10.0);

        // Act
        var result = compraService.processItems(List.of(i1, i2), productMap);

        // Assert
        assertEquals(100.0, result.getTotalCompra()); // 50 + 50
        assertEquals(2, result.getDetallesToSave().size()); // Should save 2 separate rows
    }

    // --- GROUP 4: STOCK LOGIC ---

    @Test
    @DisplayName("UT-11: updateStock calls repository with correct arguments")
    void updateStock_CallsRepository_Correctly() {
        // Arrange
        CompraItemRequest i1 = new CompraItemRequest();
        i1.setProductoId(1L); i1.setUbicacionId(100L); i1.setCantidad(5L);

        CompraItemRequest i2 = new CompraItemRequest();
        i2.setProductoId(2L); i2.setUbicacionId(200L); i2.setCantidad(10L);

        // Act
        compraService.updateStockFromDetails(List.of(i1, i2));

        // Assert
        verify(stockRepository).addStock(1L, 100L, 5L);
        verify(stockRepository).addStock(2L, 200L, 10L);
    }

    @Test
    @DisplayName("UT-12: updateStock throws friendly error on DataAccessException")
    void updateStock_ThrowsFriendlyError_OnDataAccessException() {
        // Arrange
        CompraItemRequest i1 = new CompraItemRequest();
        i1.setProductoId(1L); i1.setUbicacionId(999L); i1.setCantidad(5L);

        // Simulate DB Constraint Violation
        doThrow(new DataIntegrityViolationException("FK Error"))
                .when(stockRepository).addStock(anyLong(), anyLong(), anyLong());

        List<CompraItemRequest> items = List.of(i1);

        // Act & Assert
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> compraService.updateStockFromDetails(items));

        assertTrue(ex.getMessage().contains("Verifique que la Ubicación ID"));
    }

    // --- GROUP 5: ORCHESTRATION & AUDITING ---

    @Test
    @DisplayName("UT-13 & UT-14: registrarCompra orchestrates full flow and logs strict audit")
    void registrarCompra_OrchestratesFullFlow_Success() {
        // Arrange
        Product p = Product.builder().codigo("A").descripcion("P").precioCosto(100.0).precioMayorista(0.0).precioMinorista(200.0).build(); p.setId(1L);

        when(productRepository.findAllById(anyList())).thenReturn(List.of(p));
        when(compraRepository.saveCompra(any())).thenReturn(500L); // Mock generated ID

        CompraItemRequest item = new CompraItemRequest();
        item.setProductoId(1L);
        item.setCantidad(2L);
        item.setCostoUnitario(100.0); // Total 200
        item.setUbicacionId(10L);

        CompraRequest request = new CompraRequest();
        request.setProveedor("Sony");
        request.setUsuarioId(7L);
        request.setNroComprobante("F001");
        request.setItems(List.of(item));

        // Act
        CompraResponse response = compraService.registrarCompra(request);

        // Assert
        assertEquals(500L, response.getId());
        assertEquals(200.0, response.getTotalCompra());

        // Verify Sequence
        verify(compraRepository).saveCompra(any());
        verify(compraRepository).saveDetalles(anyList());
        verify(stockRepository).addStock(1L, 10L, 2L);

        // Verify Strict Audit String (UT-14)
        verify(auditoriaService).registrarAccion(
                7L,
                "COMPRA",
                "Registrada Compra ID 500 (Prov: Sony) - Total: $200.0"
        );
    }

    // --- GROUP 6: SOFT-DELETE PRODUCT GUARD ---

    @Test
    @DisplayName("UT-15: processItems throws BusinessRuleException when product is logically deleted (activo=false)")
    void processItems_Throws_WhenProductIsInactive() {
        // Arrange: product is present in the map but soft-deleted.
        // This covers the defence-in-depth check inside processItems(), independently
        // of findAllById() which filters activo=true in the repository.
        Product inactiveProduct = Product.builder().id(1L).codigo("OLD").descripcion("Producto Archivado")
                .precioCosto(100.0).precioMayorista(150.0).precioMinorista(200.0)
                .cantidadStock(0L).activo(false).build();

        CompraItemRequest item = new CompraItemRequest();
        item.setProductoId(1L);
        item.setCantidad(2L);
        item.setCostoUnitario(100.0);

        Map<Long, Product> productMap = Map.of(1L, inactiveProduct);
        List<CompraItemRequest> items = List.of(item);

        // Act & Assert: must be BusinessRuleException, NOT ResourceNotFoundException
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> compraService.processItems(items, productMap));

        assertTrue(ex.getMessage().contains("eliminado"),
                "Error message must indicate the product is archived, not missing");
        assertTrue(ex.getMessage().contains("Producto Archivado"),
                "Error message must include the product's description for user clarity");
    }
}