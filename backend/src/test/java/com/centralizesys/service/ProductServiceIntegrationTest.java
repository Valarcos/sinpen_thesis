package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.product.Product;
import com.centralizesys.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("IT-01: Search limits results to 100 items")
    void search_LimitConstraint() {
        // 1. Insert 105 products with similar names

        // Batch insert using Repository to ensure validity
        for (int i = 0; i < 105; i++) {
            Product p = Product.builder().codigo("LIMIT-TEST-" + i).descripcion("Common Description Item " + i).precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();
            productRepository.save(p);
        }

        // 2. Search for "Common Description"
        List<Product> results = productService.search("Common Description");

        // 3. Assert
        assertEquals(100, results.size(), "Search should be strictly capped at 100");
    }

    @Test
    @DisplayName("IT-02: Search matches Code OR Description")
    void search_MatchesBoth() {
        createTestProduct("FIND-ME-CODE", 100.0, 0L); // Helper from BaseIntegrationTest

        Product p2 = Product.builder().codigo("HIDDEN-CODE").descripcion("FIND-ME-DESC").precioCosto(10.0).precioMayorista(10.0).precioMinorista(10.0).build();
        productService.create(p2);

        List<Product> byCode = productService.search("FIND-ME-CODE");
        assertEquals(1, byCode.size());

        List<Product> byDesc = productService.search("FIND-ME-DESC");
        assertEquals(1, byDesc.size());
    }

    @Test
    @DisplayName("IT-03: Delete sets activo=false and leaves Stock intact (Logical Delete)")
    void delete_SetsActivoFalseAndLeavesStockIntact() {
        // 1. Create Product with Stock
        Long prodId = createTestProduct("CASCADE-TEST", 200.0, 50L);
        Long userId = createTestUser();

        // Check stock exists
        Long stockCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_por_ubicacion WHERE producto_id = ?", Long.class, prodId);
        assertEquals(1, stockCount, "Stock should exist before delete");

        // 2. Delete Product
        productService.deleteById(prodId, userId);

        // 3. Verify Stock remains intact (Logical Deletion)
        Long stockAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_por_ubicacion WHERE producto_id = ?", Long.class, prodId);
        assertEquals(1, stockAfter, "Stock should NOT be deleted physically with Logical Deletion");
    }

    @Test
    @DisplayName("IT-04: Product code is enforced to uppercase on save")
    void save_EnforcesUppercaseCode() {
        Product p = Product.builder().codigo("prod-lower-123").descripcion("Lowercase Test").precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();
        Product saved = productService.create(p);

        assertEquals("PROD-LOWER-123", saved.getCodigo(), "Product code should be saved as uppercase");
    }

    @Test
    @DisplayName("IT-05: Search is case-insensitive for description")
    void search_CaseInsensitiveDescription() {
        Product p = Product.builder().codigo("DESC-CASE-TEST").descripcion("Galletas de Chocolate").precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();
        productService.create(p);

        List<Product> results = productService.search("galletas de chocolate");
        assertEquals(1, results.size(), "Should find product regardless of case");
        assertEquals("Galletas de Chocolate", results.getFirst().getDescripcion(), "Original case should be preserved in the DB");
    }

    @Test
    @DisplayName("IT-06: getVariantFamily works with lowercase queries")
    void getVariantFamily_LowercaseQuery() {
        Product p1 = Product.builder().codigo("FAMILY-UPPER").descripcion("Family Test 1").precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();
        Product p2 = Product.builder().codigo("FAMILY-UPPER").descripcion("Family Test 2").precioCosto(15.0).precioMayorista(15.0).precioMinorista(25.0).build();
        productService.create(p1);
        productService.create(p2);

        // Query with lowercase
        List<Product> family = productService.getVariantFamily("family-upper");
        assertEquals(2, family.size(), "Should find all family members using lowercase code");
        assertEquals("FAMILY-UPPER", family.getFirst().getCodigo(), "Database codes should remain uppercase");
    }

    @Test
    @DisplayName("IT-07: findAllById retrieves active products and omits deleted ones")
    void findAllById_OmitsDeleted() {
        Product p1 = Product.builder().codigo("BULK-1").descripcion("Bulk 1").precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0).build();
        Product p2 = Product.builder().codigo("BULK-2").descripcion("Bulk 2").precioCosto(15.0).precioMayorista(15.0).precioMinorista(25.0).build();
        Product p3 = Product.builder().codigo("BULK-3").descripcion("Bulk 3").precioCosto(20.0).precioMayorista(20.0).precioMinorista(30.0).build();

        Product saved1 = productService.create(p1);
        Product saved2 = productService.create(p2);
        Product saved3 = productService.create(p3);

        // Delete one product logically
        productService.deleteById(saved2.getId(), 0L);

        List<Product> results = productService.findAllById(List.of(saved1.getId(), saved2.getId(), saved3.getId()));

        assertEquals(2, results.size(), "Should only return active products");
        assertTrue(results.stream().anyMatch(p -> p.getId().equals(saved1.getId())));
        assertTrue(results.stream().anyMatch(p -> p.getId().equals(saved3.getId())));
        assertFalse(results.stream().anyMatch(p -> p.getId().equals(saved2.getId())));
    }
}
