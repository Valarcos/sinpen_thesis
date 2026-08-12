package com.centralizesys.repository;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("Save inserts a new product and returns Generated ID and Audit Fields")
    void save_InsertsNewProduct() {
        // Manually create object WITHOUT ID for save test
        // 5-arg constructor leaves audit fields as null/0L by default
        Product p = Product.builder().codigo("A-001").descripcion("Test Product").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();

        Product saved = productRepository.save(p);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCodigo()).isEqualTo("A-001");

        // Assert Audit Fields are populated correctly by the Repository mapping
        assertThat(saved.getFechaCreacion()).isNotNull();
        assertThat(saved.getFechaActualizacion()).isNotNull();
        assertThat(saved.getCreadoPor()).isNotNull();
        assertThat(saved.getActualizadoPor()).isNotNull();
    }

    @Test
    @DisplayName("FindAll with Limit/Offset returns correct page")
    void findAll_Paged_ReturnsCorrectSubset() {
        // Arrange: Create 25 products
        for (int i = 0; i < 25; i++) {
            createTestProduct("PAGE-" + i, 100.0 + i, 10L);
        }

        // Act: Request Page 2 (Limit 10, Offset 10) -> Should get 10 items (Indices
        // 10-19)
        List<Product> page2 = productRepository.findAll(10L, 10L);

        // Assert
        assertThat(page2).hasSize(10);
    }

    @Test
    @DisplayName("CountAll returns total number of products")
    void countAll_ReturnsCorrectCount() {
        createTestProduct("CNT-1", 100.0, 10L);
        createTestProduct("CNT-2", 100.0, 10L);
        createTestProduct("CNT-3", 100.0, 10L);

        Long count = productRepository.countAll();

        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("FindAll returns all products")
    void findAll_ReturnsAll() {
        createTestProduct("A-001", 100.0, 10L);
        createTestProduct("A-002", 200.0, 5L);

        List<Product> all = productRepository.findAll();

        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("FindById returns correct product")
    void findById_ReturnsCorrectProduct() {
        Long id = createTestProduct("B-001", 150.0, 20L);

        Optional<Product> found = productRepository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getCodigo()).isEqualTo("B-001");
    }

    @Test
    @DisplayName("Update modifies existing product")
    void update_ModifiesProduct() {
        Long id = createTestProduct("C-001", 300.0, 0L);
        Product saved = productRepository.findById(id).orElseThrow();

        saved.setDescripcion("Updated Description");
        productRepository.save(saved); // Should trigger update because ID is present

        Product updated = productRepository.findById(id).orElseThrow();
        assertThat(updated.getDescripcion()).isEqualTo("Updated Description");
    }

    @Test
    @DisplayName("SoftDelete sets activo=false and hides product from active queries")
    void softDelete_HidesProductFromActiveQueries() {
        Long id = createTestProduct("D-001", 50.0, 5L);

        // Act: soft-delete
        productRepository.deleteById(id);

        // Assert 1: The product is no longer visible via the active-filtered query
        Optional<Product> found = productRepository.findById(id);
        assertThat(found).isEmpty();

        // Assert 2: The physical row still exists in the DB with activo = false,
        // proving this is a true soft-delete and not a physical row removal.
        Boolean activo = jdbcTemplate.queryForObject(
                "SELECT activo FROM productos WHERE id = ?",
                Boolean.class, id);
        assertThat(activo).isFalse();
    }

    @Test
    @DisplayName("Search returns matching products")
    void search_ReturnsMatches() {
        // Create first product via helper
        Long id1 = createTestProduct("SEARCH-1", 10.0, 10L);

        // Create second product manually to specify description
        Product p2 = Product.builder().codigo("SEARCH-2").descripcion("Orange Juice").precioCosto(20.0).precioMayorista(null).precioMinorista(40.0).build();
        productRepository.save(p2);

        // Update the first one to have "Apple Juice"
        Product p1 = productRepository.findById(id1).orElseThrow();
        p1.setDescripcion("Apple Juice");
        productRepository.save(p1);

        List<Product> results = productRepository.search("Apple");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getCodigo()).isEqualTo("SEARCH-1");
    }

    @Test
    @DisplayName("Search with numeric query uses StartsWith for code and ignores description/contains")
    void search_WithNumericQuery_UsesStartsWith() {
        // Product 1: Code starts with "123"
        createTestProduct("1234", 10.0, 10L);

        // Product 2: Code contains "123" but does NOT start with it
        createTestProduct("9123", 10.0, 10L);

        // Product 3: Description contains "123" but code does not
        Product p3 = Product.builder().codigo("TEXT-CODE").descripcion("Product 123").precioCosto(20.0).precioMayorista(null).precioMinorista(40.0).build();
        productRepository.save(p3);

        List<Product> results = productRepository.search("123");

        // Should ONLY return "1234" because for numeric queries it strictly uses StartsWith on the code
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getCodigo()).isEqualTo("1234");
    }

    @Test
    @DisplayName("findLowStock - returns products with negative stock")
    void findLowStock_returnsNegativeStockProducts() {
        // Arrange - Create a product with negative stock
        // First create product with 0 stock
        Long productId = createTestProduct("LOW-001", 100.0, 0L);

        // Manually set negative stock by subtracting more than available
        jdbcTemplate.update(
                "UPDATE productos SET cantidad_stock = -5 WHERE id = ?",
                productId);

        // Act
        List<Product> lowStock = productRepository.findLowStock();

        // Assert
        assertThat(lowStock).hasSize(1);
        assertThat(lowStock.getFirst().getCantidadStock()).isNegative();
    }

    @Test
    @DisplayName("findLowStock - returns empty when no negative stock")
    void findLowStock_returnsEmptyWhenNoNegativeStock() {
        // Arrange - Create products with positive stock only
        createTestProduct("POS-001", 100.0, 10L);
        createTestProduct("POS-002", 200.0, 5L);

        // Act
        List<Product> lowStock = productRepository.findLowStock();

        // Assert
        assertThat(lowStock).isEmpty();
    }

    @Test
    @DisplayName("Partial Unique Index allows re-creating product with same code if previous is soft-deleted")
    void partialUniqueIndex_AllowsReuseOfCodeIfDeleted() {
        // 1. Create a product and soft delete it
        Product p1 = Product.builder().codigo("UNIQUE-CODE").descripcion("Desc 1").precioCosto(10.0).precioMayorista(15.0).precioMinorista(20.0).build();
        Product saved1 = productRepository.save(p1);
        productRepository.deleteById(saved1.getId());

        // 2. Create another product with the exact same unique fields
        Product p2 = Product.builder().codigo("UNIQUE-CODE").descripcion("Desc 2").precioCosto(10.0).precioMayorista(15.0).precioMinorista(20.0).build();

        // 3. Save should succeed without DataIntegrityViolationException
        Product saved2 = productRepository.save(p2);

        assertThat(saved2.getId()).isNotNull();
        assertThat(saved2.getId()).isNotEqualTo(saved1.getId());
    }

    // --- Portion 5: findWAC & Variants logic tests ---

    @Test
    @DisplayName("findWAC_ReturnsCorrectWeightedAverage_WithMultipleVariants")
    void findWAC_ReturnsCorrectWeightedAverage_WithMultipleVariants() {
        // createTestProduct(code, retailPrice, stock). Cost is 0.5 * retailPrice.
        // Variant 1: 10 units at $10 retail ($5 cost) -> Total cost $50
        createTestProduct("FAM-WAC", 10.0, 10L);
        // Variant 2: 5 units at $40 retail ($20 cost) -> Total cost $100
        createTestProduct("FAM-WAC", 40.0, 5L);

        // Total value = 150. Total items = 15. WAC = 150 / 15 = 10.0
        Optional<Double> wac = productRepository.findWAC("FAM-WAC", null);

        assertThat(wac).contains(10.0);
    }

    @Test
    @DisplayName("findWAC_ReturnsEmpty_WhenAllStockIsZeroOrNegative")
    void findWAC_ReturnsEmpty_WhenAllStockIsZeroOrNegative() {
        // Variant with 0 stock
        createTestProduct("FAM-EMPTY", 10.0, 0L);

        // Variant with negative stock
        Long id2 = createTestProduct("FAM-EMPTY", 20.0, 0L);
        Long locId = jdbcTemplate.queryForObject("SELECT id FROM ubicaciones LIMIT 1", Long.class);
        jdbcTemplate.update("INSERT INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad) VALUES (?, ?, -5)", id2, locId);

        Optional<Double> wac = productRepository.findWAC("FAM-EMPTY", null);

        assertThat(wac).isEmpty();
    }

    @Test
    @DisplayName("findWAC_ClampsNegativeStock_WhenSomeVariantsArePhantom")
    void findWAC_ClampsNegativeStock_WhenSomeVariantsArePhantom() {
        // Variant 1: 10 units at $10 retail (Cost = 5.0)
        createTestProduct("FAM-CLAMP", 10.0, 10L);

        // Variant 2: -5 units at $50 retail (Cost = 25.0) -> Should be treated as 0 stock
        Long id2 = createTestProduct("FAM-CLAMP", 50.0, 0L);
        Long locId = jdbcTemplate.queryForObject("SELECT id FROM ubicaciones LIMIT 1", Long.class);
        jdbcTemplate.update("INSERT INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad) VALUES (?, ?, -5)", id2, locId);

        // WAC should be exactly $5.0, entirely ignoring the -5 stock variant
        Optional<Double> wac = productRepository.findWAC("FAM-CLAMP", null);

        assertThat(wac).contains(5.0);
    }

    @Test
    @DisplayName("findWAC_FiltersGenericProductsByDescription")
    void findWAC_FiltersGenericProductsByDescription() {
        jdbcTemplate.update("INSERT INTO ubicaciones (id, nombre) VALUES (999, 'Test') ON CONFLICT(id) DO NOTHING");

        // Generic Apples: 10 units at $10
        Product p1 = Product.builder().codigo("1").descripcion("Apples").precioCosto(10.0).precioMayorista(15.0).precioMinorista(20.0).build();
        productRepository.save(p1);
        jdbcTemplate.update("INSERT INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad) VALUES (?, 999, 10)", p1.getId());

        // Generic Oranges: 10 units at $50
        Product p2 = Product.builder().codigo("1").descripcion("Oranges").precioCosto(50.0).precioMayorista(60.0).precioMinorista(70.0).build();
        productRepository.save(p2);
        jdbcTemplate.update("INSERT INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad) VALUES (?, 999, 10)", p2.getId());

        // Asking WAC for "Apples" generic bucket should only see $10 cost
        Optional<Double> wac = productRepository.findWAC("1", "Apples");

        assertThat(wac).contains(10.0); // If it included Oranges, it would be 30.0
    }

    @Test
    @DisplayName("findWAC_DescriptionFilterIsCaseInsensitive")
    void findWAC_DescriptionFilterIsCaseInsensitive() {
        jdbcTemplate.update("INSERT INTO ubicaciones (id, nombre) VALUES (999, 'Test') ON CONFLICT(id) DO NOTHING");

        Product p1 = Product.builder().codigo("1").descripcion("Manzanas").precioCosto(10.0).precioMayorista(15.0).precioMinorista(20.0).build();
        productRepository.save(p1);
        jdbcTemplate.update("INSERT INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad) VALUES (?, 999, 10)", p1.getId());

        Product p2 = Product.builder().codigo("1").descripcion(" manzanas ").precioCosto(50.0).precioMayorista(60.0).precioMinorista(70.0).build();
        productRepository.save(p2);
        jdbcTemplate.update("INSERT INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad) VALUES (?, 999, 10)", p2.getId());

        // WAC should average both (10*10 + 10*50) / 20 = 30.0
        Optional<Double> wac = productRepository.findWAC("1", "MANZANAS");

        assertThat(wac).contains(30.0);
    }

    @Test
    @DisplayName("findSiblingsByFamily_ReturnsSortedByIdAsc")
    void findSiblingsByFamily_ReturnsSortedByIdAsc() {
        // Insert oldest first (Id will be lowest)
        Product p1 = productRepository.save(Product.builder().codigo("FAM-SORT").descripcion("Desc").precioCosto(10.0).precioMayorista(20.0).precioMinorista(30.0).build());
        Product p2 = productRepository.save(Product.builder().codigo("FAM-SORT").descripcion("Desc").precioCosto(15.0).precioMayorista(20.0).precioMinorista(30.0).build());
        Product p3 = productRepository.save(Product.builder().codigo("FAM-SORT").descripcion("Desc").precioCosto(12.0).precioMayorista(20.0).precioMinorista(30.0).build());

        List<Product> siblings = productRepository.findSiblingsByFamily("FAM-SORT", null);

        assertThat(siblings).hasSize(3);
        assertThat(siblings.get(0).getId()).isEqualTo(p1.getId());
        assertThat(siblings.get(1).getId()).isEqualTo(p2.getId());
        assertThat(siblings.get(2).getId()).isEqualTo(p3.getId());
    }

    @Test
    @DisplayName("existsByCodigo_ReturnsTrueForActiveCode")
    void existsByCodigo_ReturnsTrueForActiveCode() {
        createTestProduct("EXIST-1", 10.0, 10L);

        // Active code
        assertThat(productRepository.existsByCodigo("EXIST-1")).isTrue();

        // Non-existent code
        assertThat(productRepository.existsByCodigo("EXIST-999")).isFalse();

        // Soft-deleted code
        Product p2 = productRepository.save(Product.builder().codigo("EXIST-DEL").descripcion("Desc").precioCosto(10.0).precioMayorista(20.0).precioMinorista(30.0).build());
        productRepository.deleteById(p2.getId());
        assertThat(productRepository.existsByCodigo("EXIST-DEL")).isFalse();
    }

    @Test
    @DisplayName("search_OrdersByStockDescThenIdDesc")
    void search_OrdersByStockDescThenIdDesc() {
        jdbcTemplate.update("INSERT INTO ubicaciones (id, nombre) VALUES (999, 'Test') ON CONFLICT(id) DO NOTHING");

        // Create 3 products matching "SEARCH-ORD"
        // P1: Stock 0, ID lowest
        Product p1 = productRepository.save(Product.builder().codigo("SEARCH-ORD-1").descripcion("Desc").precioCosto(10.0).precioMayorista(20.0).precioMinorista(30.0).build());

        // P2: Stock 10
        Product p2 = productRepository.save(Product.builder().codigo("SEARCH-ORD-2").descripcion("Desc").precioCosto(10.0).precioMayorista(20.0).precioMinorista(30.0).build());
        jdbcTemplate.update("INSERT INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad) VALUES (?, 999, 10)", p2.getId());

        // P3: Stock 0, ID highest
        Product p3 = productRepository.save(Product.builder().codigo("SEARCH-ORD-3").descripcion("Desc").precioCosto(10.0).precioMayorista(20.0).precioMinorista(30.0).build());

        List<Product> results = productRepository.search("SEARCH-ORD");

        assertThat(results).hasSize(3);
        // P2 has highest stock (10)
        assertThat(results.get(0).getId()).isEqualTo(p2.getId());
        // P3 and P1 both have 0 stock. P3 is newer (higher ID), so it wins the tie-breaker
        assertThat(results.get(1).getId()).isEqualTo(p3.getId());
        assertThat(results.get(2).getId()).isEqualTo(p1.getId());
    }
}
