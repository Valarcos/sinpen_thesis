package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.dto.PageResponse;
import com.centralizesys.model.product.Product;
import com.centralizesys.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository repository;
    private final AuditoriaService auditoriaService;
    private final StockService stockService; // [NEW DEPENDENCY]

    private static final String PRODUCT = "Product";

    public ProductService(ProductRepository repository, AuditoriaService auditoriaService, StockService stockService) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
        this.stockService = stockService;
    }

    public List<Product> getAll() {
        return repository.findAll();
    }

    public PageResponse<Product> getAll(Long page, Long size) {
        // Defaults to avoid NPE / dumb values
        long p = (page == null || page < 0) ? 0 : page;
        long s = (size == null || size <= 0) ? 20 : Math.min(size, 100);

        long totalElements = repository.countAll();
        long totalPages = (long) Math.ceil((double) totalElements / s);

        // Optimization: Don't query if page is out of bounds (except page 0)
        if (p >= totalPages && totalPages > 0) {
            return new PageResponse<>(
                    java.util.List.of(), p, s, totalElements, totalPages);
        }

        List<Product> content = repository.findAll(s, p * s);
        return new PageResponse<>(
                content, p, s, totalElements, totalPages);
    }

    /**
     * Unified method for Search or Browse.
     * Always returns PageResponse for consistency.
     */
    public PageResponse<Product> getAllOrSearch(String search, Long page, Long size) {
        if (search != null && !search.isBlank()) {
            // Search Mode: List -> PageResponse (Page 0, Size 100)
            List<Product> products = search(search);
            return new PageResponse<>(
                    products,
                    0L,
                    100L,
                    (long) products.size(),
                    1L);
        } else {
            // Browse Mode: Standard Pagination
            return getAll(page, size);
        }
    }

    public Product getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, id));
    }

    // Specific method to retrieve strict matches for logic/validation
    public List<Product> getVariantsByCode(String codigo) {
        return repository.findAllByCodigo(codigo != null ? codigo.toUpperCase().trim() : null);
    }

    // Uses the "Smart Search" (Code OR Description)
    public List<Product> search(String query) {
        return repository.search(query);
    }

    // Package-private for testing
    void validate(Product product) {
        if (product.getCodigo() == null || product.getCodigo().isBlank()) {
            throw new BusinessRuleException("El código del producto (ART) es obligatorio.");
        }

        // Enforce uppercase product codes for case-insensitive standardization
        product.setCodigo(com.centralizesys.util.StringUtil.safeTruncate(product.getCodigo().toUpperCase().trim(), 100));

        if (product.getDescripcion() == null || product.getDescripcion().isBlank()) {
            throw new BusinessRuleException("La descripción es obligatoria.");
        }
        product.setDescripcion(com.centralizesys.util.StringUtil.safeTruncate(product.getDescripcion().trim(), 255));
        if (product.getPrecioMinorista() == null || product.getPrecioMinorista() < 0) {
            throw new BusinessRuleException("El precio de venta minorista debe ser 0 o mayor.");
        }

        if (product.getPrecioMayorista() != null && product.getPrecioMayorista() < 0) {
            throw new BusinessRuleException("El precio de venta mayorista debe ser 0 o mayor.");
        }
        if (product.getPrecioCosto() == null || product.getPrecioCosto() < 0) {
            throw new BusinessRuleException("El costo debe ser 0 o mayor.");
        }
    }

    @Transactional
    public Product create(Product product) {
        // Uses system user (0L) since this path is not triggered by a direct HTTP request.
        return internalCreate(product, 0L);
    }

    // Extended create method dealing with initial stock
    @Transactional
    public Product createWithStock(Product product, Long locationId, Long quantity, Long usuarioId) {
        // Use internal helper to avoid "self-invocation" of Transactional method
        // (Sonar rule: Call transactional methods via an injected dependency instead of
        // directly via 'this')
        Product saved = internalCreate(product, usuarioId);

        if (locationId != null && quantity != null && quantity > 0) {
            stockService.addStock(saved.getId(), locationId, quantity);
            return getById(saved.getId());
        }
        return saved;
    }

    // Helper to centralize creation logic and bypass self-invocation issues
    private Product internalCreate(Product product, Long usuarioId) {
        // 1. Validate fields (throws BusinessRuleException on invalid input)
        validate(product);

        // 2. Apply wholesale price default
        applyWholesalePriceDefault(product);

        // 3. Inject audit identity (Zero-Trust: ID comes from the server, never the client)
        long resolvedUserId = resolveUserId(usuarioId);
        product.setCreadoPor(resolvedUserId);
        product.setActualizadoPor(resolvedUserId);

        // 4. Zero-Trust Price Override for new variants of existing families.
        //    When a new variant is being added (not a generic codigo='1'), look up the
        //    existing family and enforce the family's current retail/wholesale prices.
        //    This prevents price drift between siblings caused by a cashier entering a
        //    slightly different price when registering a new purchase.
        if (!"1".equals(product.getCodigo())) {
            List<Product> existingSiblings = repository.findSiblingsByFamily(product.getCodigo(), null);
            if (!existingSiblings.isEmpty()) {
                // Use the last (newest) sibling as the authoritative source of family retail prices.
                Product familyRepresentative = existingSiblings.getLast();
                product.setPrecioMinorista(familyRepresentative.getPrecioMinorista());
                product.setPrecioMayorista(familyRepresentative.getPrecioMayorista());
            }
        }

        // 5. Check for variant collision (after price override, so the enforced prices are compared)
        checkVariantCollision(product, null);

        // 6. Persist
        return repository.save(product);
    }

    @Transactional
    public void update(Long id, Product product, Long usuarioId) {
        // 1. Basic Validation
        validate(product);
        applyWholesalePriceDefault(product);

        // Check for variant collisions BEFORE attempting the cascade update.
        // This ensures a clean BusinessRuleException instead of a 500 DB Constraint Error
        checkVariantCollision(product, id);

        // 2. Fetch the original state BEFORE any changes, using it as the source of truth
        //    for sibling lookup and change-detection.
        Product original = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, id));

        // 3. Merge-Block: Prevent reassigning a barcode to an already-existing different family.
        //    Example: Renaming "ART-001" to "ART-002" is blocked if "ART-002" already has variants,
        //    because that would incorrectly merge two separate product families in the DB.
        boolean codeChanged = !product.getCodigo().equals(original.getCodigo());
        if (codeChanged && !"1".equals(product.getCodigo()) && repository.existsByCodigo(product.getCodigo())) {
            throw new BusinessRuleException(
                    "El código '" + product.getCodigo() + "' ya pertenece a otra familia de productos. "
                            + "Cambiarlo causaría una fusión incorrecta de inventario.");
        }

        // 4. Determine the sibling lookup key using the ORIGINAL codigo and descripcion.
        //    Generic products (codigo='1') use both codigo AND descripcion as the family key.
        String siblingDescripcion = "1".equals(original.getCodigo()) ? original.getDescripcion() : null;
        List<Product> siblings = repository.findSiblingsByFamily(original.getCodigo(), siblingDescripcion);

        // 5. Cascade Update: Apply new descripcion, precioMinorista, precioMayorista (and optionally
        //    codigo) to ALL active siblings, including the product being edited itself.
        //    Note: precioCosto is intentionally NOT cascaded — each variant has its own purchase cost.
        long resolvedUserId = resolveUserId(usuarioId);
        for (Product sibling : siblings) {
            sibling.setDescripcion(product.getDescripcion());
            sibling.setPrecioMinorista(product.getPrecioMinorista());
            sibling.setPrecioMayorista(product.getPrecioMayorista());
            sibling.setActualizadoPor(resolvedUserId);
            if (codeChanged) {
                sibling.setCodigo(product.getCodigo());
            }
            // Only the explicitly edited product receives the new cost
            if (sibling.getId().equals(id)) {
                sibling.setPrecioCosto(product.getPrecioCosto());
            }
            repository.save(sibling);
        }

        // Edge case: If the edited product was not in the siblings list
        // (e.g., the product was re-activated while siblings were found under old state),
        // persist it directly to avoid losing the update.
        boolean editedProductSaved = siblings.stream().anyMatch(s -> s.getId().equals(id));
        if (!editedProductSaved) {
            product.setId(id);
            product.setActualizadoPor(resolvedUserId);
            repository.save(product);
        }
    }

    /**
     * Issue #15: If wholesale price is not provided, default to retail price.
     * Applied in both create and update paths.
     */
    private void applyWholesalePriceDefault(Product product) {
        if (product.getPrecioMayorista() == null || product.getPrecioMayorista() == 0.0) {
            product.setPrecioMayorista(product.getPrecioMinorista());
        }
    }

    // Extracted logic to avoid duplication
    // excludeId should be null for create, and the current ID for update
    private void checkVariantCollision(Product product, Long excludeId) {
        // Generic code "1" bypasses all uniqueness checks
        if ("1".equals(product.getCodigo())) {
            return;
        }

        List<Product> variants = repository.findAllByCodigo(product.getCodigo());

        boolean collision = variants.stream()
                .filter(p -> !p.getId().equals(excludeId)) // Exclude self if updating (safe if excludeId is null)
                .anyMatch(p -> compareDouble(p.getPrecioCosto(), product.getPrecioCosto()) &&
                        compareDouble(p.getPrecioMinorista(), product.getPrecioMinorista()));

        if (collision) {
            throw new BusinessRuleException("Ya existe otra variante exacta con Código: " + product.getCodigo()
                    + ", Costo: " + product.getPrecioCosto()
                    + ", Precio: " + product.getPrecioMinorista());
        }
    }

    // Helper to avoid floating point comparison issues for double comparison
    // (precision safety)
    // Package-private for testing
    boolean compareDouble(Double a, Double b) {
        if (a == null || b == null)
            return false;
        return Math.abs(a - b) < 0.001;
    }

    private long resolveUserId(Long usuarioId) {
        return usuarioId != null ? usuarioId : 0L;
    }

    // [CHANGED SIGNATURE] Added usuarioId
    public void deleteById(Long id, Long usuarioId) {
        // Note: The DB handles cascading delete of stock_por_ubicacion
        // But we check existence first to throw a proper 404 if needed
        Product p = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, id)); // Check existence to get name for log

        repository.deleteById(id);

        // Log the action with description for clarity
        auditoriaService.registrarAccion(usuarioId, "DELETE_PRODUCT",
                "Eliminado producto ID " + id + ": " + p.getDescripcion());
    }

    /**
     * Get products with negative stock for Dashboard "Morning Warning".
     */
    public List<Product> getLowStockAlerts() {
        return repository.findLowStock();
    }

    /**
     * Returns all active variants of a product family for the Smart Form code lookup.
     * For standard products (codigo != "1"), matches by codigo only.
     * Returns sorted oldest-first (id ASC) per findSiblingsByFamily contract.
     */
    public List<Product> getVariantFamily(String codigo) {
        return repository.findSiblingsByFamily(codigo != null ? codigo.toUpperCase().trim() : null, null);
    }
}