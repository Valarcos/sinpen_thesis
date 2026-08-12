package com.centralizesys.controller;

import com.centralizesys.model.dto.PageResponse;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.product.ProductRequest;
import com.centralizesys.model.product.ProductResponse;
import com.centralizesys.security.SecurityUtils;
import com.centralizesys.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    // GET /api/productos?search=...&page=0&size=20
    // Combines "GetAll" (Paged) and "Search" (List/Limit 100)
    // Refactored to always return PageResponse to avoid wildcard types (Sonar)
    // Logic moved to Service
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> getAllOrSearch(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") Long page,
            @RequestParam(defaultValue = "20") Long size) {

        PageResponse<Product> pageData = service.getAllOrSearch(search, page, size);

        List<ProductResponse> contentDtos = pageData.content().stream()
                .map(ProductResponse::new)
                .toList();

        PageResponse<ProductResponse> response = new PageResponse<>(
                contentDtos,
                pageData.page(),
                pageData.size(),
                pageData.totalElements(),
                pageData.totalPages());

        return ResponseEntity.ok(response);
    }

    // GET /api/productos/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        Product product = service.getById(id);
        return ResponseEntity.ok(new ProductResponse(product));
    }

    // POST /api/productos
    @PostMapping
    public ResponseEntity<ProductResponse> create(@RequestBody ProductRequest request) {
        // Zero-Trust: Extract identity from the validated JWT, never from the request body.
        Long usuarioId = SecurityUtils.getAuthenticatedUserId();

        // Map DTO -> Model
        Product newProduct = Product.builder()
                .codigo(request.getCodigo())
                .descripcion(request.getDescripcion())
                .precioCosto(request.getPrecioCosto())
                .precioMayorista(request.getPrecioMayorista())
                .precioMinorista(request.getPrecioMinorista())
                .build();

        // Delegate creation and initial stock handling to Service
        Product saved = service.createWithStock(
                newProduct,
                request.getUbicacionId(),
                request.getCantidad() != null ? request.getCantidad().longValue() : null,
                usuarioId);

        return new ResponseEntity<>(new ProductResponse(saved), HttpStatus.CREATED);
    }

    // PUT /api/productos/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id,
                                       @RequestBody ProductRequest request) {
        // Zero-Trust: Extract identity from the validated JWT, never from the request body.
        Long usuarioId = SecurityUtils.getAuthenticatedUserId();

        // Map DTO -> Model
        Product updatedProduct = Product.builder()
                .codigo(request.getCodigo())
                .descripcion(request.getDescripcion())
                .precioCosto(request.getPrecioCosto())
                .precioMayorista(request.getPrecioMayorista())
                .precioMinorista(request.getPrecioMinorista())
                .build();

        service.update(id, updatedProduct, usuarioId);
        return ResponseEntity.noContent().build();
    }

    // DELETE /api/productos/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long usuarioId = SecurityUtils.getAuthenticatedUserId();
        service.deleteById(id, usuarioId); // Pass it down
        return ResponseEntity.noContent().build();
    }

    // GET /api/productos/alerts
    // Returns products with negative stock for Dashboard "Morning Warning"
    @GetMapping("/alerts")
    public ResponseEntity<List<ProductResponse>> getLowStockAlerts() {
        List<Product> alerts = service.getLowStockAlerts();
        return ResponseEntity.ok(alerts.stream()
                .map(ProductResponse::new)
                .toList());
    }

    // GET /api/productos/familia/{codigo}
    // Returns all active variants of a product family for the Smart Form code lookup.
    // Called by ProductFormModal on barcode blur to auto-fill family prices and lock fields.
    @GetMapping("/familia/{codigo}")
    public ResponseEntity<List<ProductResponse>> getVariantFamily(@PathVariable String codigo) {
        List<Product> family = service.getVariantFamily(codigo);
        return ResponseEntity.ok(family.stream()
                .map(ProductResponse::new)
                .toList());
    }
}