package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.dto.PageResponse;
import com.centralizesys.model.product.Product;
import com.centralizesys.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository repository;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private StockService stockService; // [NEW MOCK]

    @org.mockito.InjectMocks
    private ProductService service;

    // Helper: builds a fully-populated product (RowMapper-equivalent state)
    private Product buildProduct(Long id, String codigo, String descripcion,
                                 Double costo, Double mayorista, Double minorista,
                                 Long stock, boolean activo) {
        return Product.builder()
                .id(id)
                .codigo(codigo)
                .descripcion(descripcion)
                .precioCosto(costo)
                .precioMayorista(mayorista)
                .precioMinorista(minorista)
                .cantidadStock(stock)
                .activo(activo)
                .build();
    }

    // --- Read / Pass-through Tests ---

    @Test
    @DisplayName("getAll returns list from repository")
    void getAll_ReturnsList() {
        Product p = buildProduct(1L, "C", "D", 1.0, 1.0, 1.0, 0L, true);
        when(repository.findAll()).thenReturn(List.of(p));
        List<Product> result = service.getAll();
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getAllOrSearch (Browse) returns PageResponse")
    void getAllOrSearch_Browse() {
        Product p = buildProduct(1L, "C", "D", 1.0, 1.0, 1.0, 0L, true);
        when(repository.findAll(20L, 0L)).thenReturn(List.of(p));
        when(repository.countAll()).thenReturn(1L);

        PageResponse<Product> result = service.getAllOrSearch(null, 0L, 20L);

        assertEquals(1, result.content().size());
        assertEquals(1L, result.totalElements());
    }

    @Test
    @DisplayName("getAllOrSearch (Search) returns PageResponse")
    void getAllOrSearch_Search() {
        Product p = buildProduct(1L, "C", "D", 1.0, 1.0, 1.0, 0L, true);
        when(repository.search("query")).thenReturn(List.of(p));

        PageResponse<Product> result = service.getAllOrSearch("query", 0L, 20L);

        assertEquals(1, result.content().size());
        assertEquals(100L, result.size()); // Search creates fixed size 100 PageResponse
        assertEquals(0L, result.page());
    }

    @Test
    @DisplayName("getById returns product when found")
    void getById_Success() {
        Product p = buildProduct(1L, "C", "D", 1.0, 1.0, 1.0, 0L, true);
        when(repository.findById(1L)).thenReturn(Optional.of(p));

        Product result = service.getById(1L);
        assertNotNull(result);
        assertEquals("C", result.getCodigo());
    }

    @Test
    @DisplayName("getById throws when not found")
    void getById_NotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getById(99L));
    }

    @Test
    @DisplayName("getVariantsByCode delegates to repository")
    void getVariantsByCode_Delegates() {
        when(repository.findAllByCodigo("ABC")).thenReturn(Collections.emptyList());
        assertTrue(service.getVariantsByCode("ABC").isEmpty());
        verify(repository).findAllByCodigo("ABC");
    }

    @Test
    @DisplayName("search delegates to repository")
    void search_Delegates() {
        when(repository.search("query")).thenReturn(Collections.emptyList());
        assertTrue(service.search("query").isEmpty());
        verify(repository).search("query");
    }

    @Test
    @DisplayName("Validate throws for null code")
    void validate_NullCode() {
        Product p = Product.builder().codigo(null).descripcion("Desc").precioCosto(10.0).precioMayorista(10.0).precioMinorista(10.0).build();
        assertThrows(BusinessRuleException.class, () -> service.validate(p));
    }

    @Test
    @DisplayName("Validate throws for blank description")
    void validate_BlankDescription() {
        Product p = Product.builder().codigo("A").descripcion("").precioCosto(10.0).precioMayorista(10.0).precioMinorista(10.0).build();
        assertThrows(BusinessRuleException.class, () -> service.validate(p));
    }

    @Test
    @DisplayName("Validate throws for negative retail price")
    void validate_NegativeRetail() {
        Product p = Product.builder().codigo("A").descripcion("Desc").precioCosto(10.0).precioMayorista(10.0).precioMinorista(-1.0).build();
        assertThrows(BusinessRuleException.class, () -> service.validate(p));
    }

    @Test
    @DisplayName("Validate throws for negative cost")
    void validate_NegativeCost() {
        Product p = Product.builder().codigo("A").descripcion("Desc").precioCosto(-1.0).precioMayorista(10.0).precioMinorista(10.0).build();
        assertThrows(BusinessRuleException.class, () -> service.validate(p));
    }

    // --- CompareDouble Tests ---

    @Test
    @DisplayName("CompareDouble handles nulls gracefully")
    void compareDouble_Nulls() {
        assertFalse(service.compareDouble(null, 10.0));
        assertFalse(service.compareDouble(10.0, null));
        assertFalse(service.compareDouble(null, null));
    }

    @Test
    @DisplayName("CompareDouble detects equality within epsilon")
    void compareDouble_Equality() {
        assertTrue(service.compareDouble(10.0, 10.0));
        assertTrue(service.compareDouble(10.0, 10.0000001));
    }

    @Test
    @DisplayName("CompareDouble detects inequality")
    void compareDouble_Inequality() {
        assertFalse(service.compareDouble(10.0, 10.01));
    }

    // --- Create Tests ---

    @Test
    @DisplayName("Create saves valid product")
    void create_Success() {
        Product p = Product.builder().codigo("CODE").descripcion("Desc").precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();
        when(repository.findSiblingsByFamily("CODE", null)).thenReturn(Collections.emptyList());
        when(repository.findAllByCodigo("CODE")).thenReturn(Collections.emptyList());
        when(repository.save(p)).thenReturn(p);

        Product created = service.create(p);
        assertNotNull(created);
        verify(repository).save(p);
    }

    @Test
    @DisplayName("createWithStock saves product and calls stock service")
    void createWithStock_Success() {
        Product p = Product.builder().codigo("CODE").descripcion("Desc").precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();
        Product saved = buildProduct(1L, "CODE", "Desc", 10.0, 10.0, 20.0, 0L, true);

        when(repository.findSiblingsByFamily("CODE", null)).thenReturn(Collections.emptyList());
        when(repository.findAllByCodigo("CODE")).thenReturn(Collections.emptyList());
        when(repository.save(p)).thenReturn(saved);
        when(repository.findById(1L)).thenReturn(Optional.of(saved)); // For refresh call

        Product created = service.createWithStock(p, 5L, 10L, 99L);

        assertNotNull(created);
        verify(repository).save(p);
        verify(stockService).addStock(1L, 5L, 10L);
    }

    @Test
    @DisplayName("createWithStock does NOT call stock service if quantity is null/zero")
    void createWithStock_NoStock() {
        Product p = Product.builder().codigo("CODE").descripcion("Desc").precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();
        Product saved = buildProduct(1L, "CODE", "Desc", 10.0, 10.0, 20.0, 0L, true);

        when(repository.findSiblingsByFamily("CODE", null)).thenReturn(Collections.emptyList());
        when(repository.findAllByCodigo("CODE")).thenReturn(Collections.emptyList());
        when(repository.save(p)).thenReturn(saved);

        service.createWithStock(p, 5L, null, 99L);

        verify(repository).save(p);
        verifyNoInteractions(stockService);
    }

    @Test
    @DisplayName("Create throws on exact duplicate (Code+Cost+Price)")
    void create_Duplicate_Throws() {
        Product p = Product.builder().codigo("CODE").descripcion("Desc").precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();
        Product existing = buildProduct(1L, "CODE", "Old", 10.0, 10.0, 20.0, 0L, true);

        when(repository.findSiblingsByFamily("CODE", null)).thenReturn(List.of(existing));
        when(repository.findAllByCodigo("CODE")).thenReturn(List.of(existing));

        assertThrows(BusinessRuleException.class, () -> service.create(p));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Create allows duplicate if code is '1' (Generic)")
    void create_Generic_AllowsDuplicates() {
        Product p = Product.builder().codigo("1").descripcion("Genérico").precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();

        // Even though existing matches perfectly, "1" bypasses the check purely in logic
        when(repository.save(p)).thenReturn(p);

        assertDoesNotThrow(() -> service.create(p));
        verify(repository).save(p);
    }

    // --- Wholesale Price Default Tests (Issue #15) ---

    @Test
    @DisplayName("Create defaults null wholesale price to retail price")
    void create_NullWholesale_DefaultsToRetail() {
        Product p = Product.builder().codigo("CODE").descripcion("Desc").precioCosto(10.0).precioMayorista(null).precioMinorista(25.0).build();
        when(repository.findAllByCodigo("CODE")).thenReturn(Collections.emptyList());
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = service.create(p);

        assertNotNull(created.getPrecioMayorista(), "Wholesale price should not be null after create");
        assertEquals(25.0, created.getPrecioMayorista(), "Wholesale price should default to retail price");
    }

    @Test
    @DisplayName("CreateWithStock defaults null wholesale price to retail price")
    void createWithStock_NullWholesale_DefaultsToRetail() {
        Product p = Product.builder().codigo("CODE").descripcion("Desc").precioCosto(10.0).precioMayorista(null).precioMinorista(30.0).build();
        Product saved = buildProduct(1L, "CODE", "Desc", 10.0, 30.0, 30.0, 0L, true);

        when(repository.findSiblingsByFamily("CODE", null)).thenReturn(Collections.emptyList());
        when(repository.findAllByCodigo("CODE")).thenReturn(Collections.emptyList());
        when(repository.save(any(Product.class))).thenReturn(saved);
        when(repository.findById(1L)).thenReturn(Optional.of(saved));

        Product created = service.createWithStock(p, 5L, 10L, 99L);

        assertEquals(30.0, p.getPrecioMayorista(), "Original product object should have wholesale defaulted");
        assertNotNull(created);
    }

    @Test
    @DisplayName("Update defaults null wholesale price to retail price")
    void update_NullWholesale_DefaultsToRetail() {
        Product updateReq = Product.builder().codigo("CODE").descripcion("Desc").precioCosto(10.0).precioMayorista(null).precioMinorista(40.0).build();
        Product existing = buildProduct(1L, "CODE", "Desc", 10.0, 10.0, 20.0, 0L, true);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findSiblingsByFamily("CODE", null)).thenReturn(List.of(existing));

        service.update(1L, updateReq, 99L);

        assertEquals(40.0, existing.getPrecioMayorista(), "Wholesale price should default to retail price on update");
        verify(repository).save(existing);
    }

    // --- Zero Trust Internal Create Tests ---

    @Test
    @DisplayName("internalCreate enforces Zero-Trust by overriding submitted prices with family prices")
    void internalCreate_ZeroTrust_OverridesPricesFromSiblings() {
        // User submits $99 for prices
        Product p = Product.builder().codigo("CODE").descripcion("Desc").precioCosto(10.0).precioMayorista(99.0).precioMinorista(99.0).build();
        // DB family has $50 wholesale, $60 retail
        Product sibling = buildProduct(1L, "CODE", "Desc", 15.0, 50.0, 60.0, 0L, true);

        when(repository.findSiblingsByFamily("CODE", null)).thenReturn(List.of(sibling));
        when(repository.findAllByCodigo("CODE")).thenReturn(List.of(sibling));
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = service.create(p);

        // Prices must match the sibling, NOT the user input
        assertEquals(50.0, created.getPrecioMayorista(), "Wholesale price should be overridden by family");
        assertEquals(60.0, created.getPrecioMinorista(), "Retail price should be overridden by family");
        assertEquals(10.0, created.getPrecioCosto(), "Cost should NOT be overridden");
    }

    @Test
    @DisplayName("internalCreate runs collision check AFTER zero-trust price assignment")
    void internalCreate_ZeroTrust_RunsCollisionCheckAfterPriceOverride() {
        // User submits $99 for prices, but $10 cost
        Product p = Product.builder().codigo("CODE").descripcion("Desc").precioCosto(10.0).precioMayorista(99.0).precioMinorista(99.0).build();
        // DB family has $50 wholesale, $60 retail. Cost is ALSO 10!
        Product sibling = buildProduct(1L, "CODE", "Desc", 10.0, 50.0, 60.0, 0L, true);

        when(repository.findSiblingsByFamily("CODE", null)).thenReturn(List.of(sibling));
        // the duplicate check inside checkVariantCollision will see the overriden prices
        when(repository.findAllByCodigo("CODE")).thenReturn(List.of(sibling));

        // Because internalCreate copies the $50/$60 prices, the new variant becomes
        // an exact duplicate of the sibling (CODE, $10 cost, $50 wholesale, $60 retail).
        assertThrows(BusinessRuleException.class, () -> service.create(p));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("internalCreate does NOT override prices for generic products (code '1')")
    void internalCreate_Generic_DoesNotOverridePrices() {
        Product p = Product.builder().codigo("1").descripcion("Manzanas").precioCosto(10.0).precioMayorista(15.0).precioMinorista(20.0).build();

        // '1' bypasses findSiblingsByFamily and findAllByCodigo entirely inside internalCreate
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = service.create(p);

        // Generic product keeps its own prices
        assertEquals(15.0, created.getPrecioMayorista(), "Generic product should keep its own wholesale price");
        assertEquals(20.0, created.getPrecioMinorista(), "Generic product should keep its own retail price");
    }

    // --- Update Tests (Merge Block & Cascade) ---

    @Test
    @DisplayName("Update throws BusinessRuleException when attempting to change code to an existing different family (Merge Block)")
    void update_MergeBlock_ThrowsWhenCodeBelongsToAnotherFamily() {
        Product existing = buildProduct(1L, "OLD-CODE", "Desc", 10.0, 10.0, 20.0, 0L, true);
        Product updateReq = Product.builder().codigo("NEW-CODE").descripcion("Desc").precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByCodigo("NEW-CODE")).thenReturn(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.update(1L, updateReq, 99L));
        assertTrue(ex.getMessage().contains("pertenece a otra familia"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Update allows code change when new code does not exist in DB")
    void update_MergeBlock_AllowsCodeChangeToNonExistentFamily() {
        Product existing = buildProduct(1L, "OLD-CODE", "Desc", 10.0, 10.0, 20.0, 0L, true);
        Product updateReq = Product.builder().codigo("NEW-CODE").descripcion("Desc").precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByCodigo("NEW-CODE")).thenReturn(false);
        when(repository.findSiblingsByFamily("OLD-CODE", null)).thenReturn(List.of(existing));

        service.update(1L, updateReq, 99L);

        verify(repository).save(existing);
        assertEquals("NEW-CODE", existing.getCodigo());
        assertEquals(10.0, existing.getPrecioCosto());
    }

    @Test
    @DisplayName("Update allows code change to generic bucket '1' without checking existsByCodigo")
    void update_MergeBlock_AllowsCodeChangeToGenericBucket() {
        Product existing = buildProduct(1L, "OLD-CODE", "Desc", 10.0, 10.0, 20.0, 0L, true);
        Product updateReq = Product.builder().codigo("1").descripcion("Desc").precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findSiblingsByFamily("OLD-CODE", null)).thenReturn(List.of(existing));

        service.update(1L, updateReq, 99L);

        verify(repository, never()).existsByCodigo("1");
        verify(repository).save(existing);
        assertEquals("1", existing.getCodigo());
    }

    @Test
    @DisplayName("Update cascades description and prices to all siblings, but ignores cost")
    void update_CascadeUpdate_AllSiblingsReceiveNewDescription() {
        Product existing = buildProduct(1L, "CODE", "Old Desc", 10.0, 20.0, 30.0, 0L, true);
        Product sibling1 = buildProduct(2L, "CODE", "Old Desc", 15.0, 20.0, 30.0, 0L, true);
        Product sibling2 = buildProduct(3L, "CODE", "Old Desc", 20.0, 20.0, 30.0, 0L, true);

        // Updating product 1
        Product updateReq = Product.builder().codigo("CODE").descripcion("New Desc").precioCosto(12.0).precioMayorista(25.0).precioMinorista(35.0).build();

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findSiblingsByFamily("CODE", null)).thenReturn(List.of(existing, sibling1, sibling2));

        service.update(1L, updateReq, 99L);

        // Verify target product (which is 'existing')
        verify(repository).save(existing);
        assertEquals(12.0, existing.getPrecioCosto()); // Cost changed on target
        assertEquals("New Desc", existing.getDescripcion());

        // Verify sibling 1 (id: 2L)
        verify(repository).save(argThat(p ->
                p.getId() == 2L &&
                        p.getDescripcion().equals("New Desc") &&
                        p.getPrecioMayorista() == 25.0 &&
                        p.getPrecioMinorista() == 35.0 &&
                        p.getPrecioCosto() == 15.0 // Cost is preserved!
        ));

        // Verify sibling 2 (id: 3L)
        verify(repository).save(argThat(p ->
                p.getId() == 3L &&
                        p.getDescripcion().equals("New Desc") &&
                        p.getPrecioMayorista() == 25.0 &&
                        p.getPrecioMinorista() == 35.0 &&
                        p.getPrecioCosto() == 20.0 // Cost is preserved!
        ));
    }

    @Test
    @DisplayName("Update stamps actualizadoPor on all siblings")
    void update_StampsActualizadoPorOnAllSiblings() {
        Product existing = buildProduct(1L, "CODE", "Desc", 10.0, 20.0, 30.0, 0L, true);
        Product sibling = buildProduct(2L, "CODE", "Desc", 15.0, 20.0, 30.0, 0L, true);
        Product updateReq = Product.builder().codigo("CODE").descripcion("Updated Desc").precioCosto(12.0).precioMayorista(25.0).precioMinorista(35.0).build();

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findSiblingsByFamily("CODE", null)).thenReturn(List.of(existing, sibling));

        service.update(1L, updateReq, 42L);

        // Both the target and the sibling must have actualizadoPor = 42
        assertEquals(42L, existing.getActualizadoPor());
        assertEquals(42L, sibling.getActualizadoPor());
    }

    // --- Delete Tests ---

    @Test
    @DisplayName("Delete calls audit service")
    void delete_Audits() {
        Product existing = buildProduct(1L, "C", "D", 1.0, 1.0, 1.0, 10L, true);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.deleteById(1L, 999L);

        verify(repository).deleteById(1L);
        verify(auditoriaService).registrarAccion(eq(999L), eq("DELETE_PRODUCT"), contains("D"));
    }

    @Test
    @DisplayName("Delete throws if product not found")
    void delete_NotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteById(99L, 1L));
        verify(auditoriaService, never()).registrarAccion(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Update throws if product not found")
    void update_NotFound() {
        Product p = Product.builder().codigo("C").descripcion("D").precioCosto(1.0).precioMayorista(1.0).precioMinorista(1.0).build();
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.update(99L, p, 1L));
    }
}
