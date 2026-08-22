package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.purchase.Compra;
import com.centralizesys.model.purchase.CompraItemRequest;
import com.centralizesys.model.purchase.CompraRequest;
import com.centralizesys.model.purchase.CompraResponse;
import com.centralizesys.model.purchase.DetalleCompra;
import com.centralizesys.model.purchase.DetalleCompraResponse;
import com.centralizesys.repository.CompraRepository;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.StockRepository;
import lombok.Data;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompraService {

    private final CompraRepository compraRepository;
    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final AuditoriaService auditoriaService;

    public CompraService(CompraRepository compraRepository,
                         StockRepository stockRepository,
                         ProductRepository productRepository,
                         AuditoriaService auditoriaService) {
        this.compraRepository = compraRepository;
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Orchestrates the purchase entry process.
     */
    @Transactional
    public CompraResponse registrarCompra(CompraRequest request) {
        // 1. Validate Payload
        validateRequest(request);

        // 2. Fetch Products (Batch)
        // [EXTRACTED] Fetching logic moved to keep main method clean
        Map<Long, Product> productMap = fetchProducts(request.getItems());

        // 3. Process Items (Logic + Calculations)
        // [EXTRACTED] Loops through items, validates costs, and prepares
        // entities/response
        ProcessedPurchaseResult result = processItems(request.getItems(), productMap);

        // 4. Persist (DB Inserts)
        Long compraId = saveTransaction(request, result);

        // 5. Update Stock (DB Writes)
        // [MOVED] Stock update is now a distinct step after persistence preparation
        updateStockFromDetails(request.getItems());

        // 6. Audit
        auditoriaService.registrarAccion(
                request.getUsuarioId(),
                "COMPRA",
                "Registrada Compra ID " + compraId + " (Prov: " + request.getProveedor() + ") - Total: $"
                        + result.getTotalCompra());

        // 7. Return Response
        return new CompraResponse(
                compraId,
                LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")),
                request.getProveedor(),
                request.getNroComprobante(),
                result.getTotalCompra(),
                result.getItemsResponse());
    }

    // --- HELPER CLASSES (Internal DTO) ---

    @Data
    static class ProcessedPurchaseResult {
        private List<DetalleCompra> detallesToSave;
        private List<DetalleCompraResponse> itemsResponse;
        private Double totalCompra;
    }

    // --- HELPER METHODS ---

    private void validateRequest(CompraRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessRuleException("La compra debe tener al menos un producto.");
        }
    }

    private Map<Long, Product> fetchProducts(List<CompraItemRequest> items) {
        List<Long> productIds = items.stream()
                .map(CompraItemRequest::getProductoId)
                .distinct()
                .toList();

        return productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    /**
     * Loops through items to validate business rules and calculate totals.
     * Package-private for Unit Testing.
     */
    ProcessedPurchaseResult processItems(List<CompraItemRequest> items, Map<Long, Product> productMap) {
        ProcessedPurchaseResult result = new ProcessedPurchaseResult();
        result.setDetallesToSave(new ArrayList<>());
        result.setItemsResponse(new ArrayList<>());
        Double totalAcumulado = 0.0;

        for (CompraItemRequest itemReq : items) {
            // Validate Input Data
            validateItemInput(itemReq);

            Product product = productMap.get(itemReq.getProductoId());

            // A. Existence Check (product absent from map means soft-deleted or never existed)
            if (product == null) {
                throw new ResourceNotFoundException("Producto", itemReq.getProductoId());
            }

            // B. Active-product guard: defence-in-depth to produce a precise error message.
            // A deleted product should never be restocked — it is archived, not missing.
            if (!product.isActivo()) {
                throw new BusinessRuleException(
                        "El producto '" + product.getDescripcion() + "' (ID: " + product.getId()
                                + ") está eliminado y no puede ser incluido en una compra.");
            }

            // C. Cost Consistency Check (Business Rule)
            // [EXTRACTED] Specific rule for variants
            validateCostConsistency(product, itemReq.getCostoUnitario());

            // D. Calculations
            Double subtotal = itemReq.getCantidad() * itemReq.getCostoUnitario();
            totalAcumulado += subtotal;

            // E. Prepare Detail Entity (For DB)
            DetalleCompra detalle = new DetalleCompra();
            detalle.setProductoId(product.getId());
            detalle.setCantidad(itemReq.getCantidad());
            detalle.setCostoUnitario(itemReq.getCostoUnitario());
            detalle.setSubtotal(subtotal);
            result.getDetallesToSave().add(detalle);

            // F. Prepare Response Item (For UI)
            result.getItemsResponse().add(new DetalleCompraResponse(
                    product.getCodigo(),
                    product.getDescripcion(),
                    itemReq.getCantidad(),
                    itemReq.getCostoUnitario(),
                    subtotal));
        }

        result.setTotalCompra(totalAcumulado);
        return result;
    }

    /**
     * Validates that quantity is positive and cost is non-negative.
     * Package-private for Unit Testing if needed.
     */
    void validateItemInput(CompraItemRequest item) {
        if (item.getCantidad() == null || item.getCantidad() <= 0) {
            throw new BusinessRuleException(
                    "La cantidad debe ser mayor a cero. Producto ID: " + item.getProductoId());
        }
        if (item.getCostoUnitario() <= 0) {
            throw new BusinessRuleException(
                    "El costo unitario debe ser mayor a cero. Producto ID: " + item.getProductoId());
        }
    }

    /**
     * Ensures the incoming cost matches the DB variant cost.
     * Package-private for Unit Testing.
     */
    void validateCostConsistency(Product product, Double incomingCost) {
        // Uses the helper to check equality.
        // Since compareDouble returns TRUE if they match, we negate it (!) to find the
        // mismatch.
        if (!compareDouble(incomingCost, product.getPrecioCosto())) {
            throw new BusinessRuleException(String.format(
                    "Inconsistencia de Costos: El producto '%s' (ID: %d) tiene un costo registrado de $%.2f, pero se intentó comprar a $%.2f. Seleccione la variante correcta.",
                    product.getDescripcion(), product.getId(), product.getPrecioCosto(), incomingCost));
        }
    }

    /**
     * Helper to safely compare doubles for currency equality.
     * Returns true if difference is less than 0.001.
     */
    private boolean compareDouble(Double a, Double b) {
        if (a == null || b == null)
            return false;
        return Math.abs(a - b) < 0.001;
    }

    private String safeTruncate(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) return str;
        if (Character.isHighSurrogate(str.charAt(maxLen - 1))) {
            return str.substring(0, maxLen - 1);
        }
        return str.substring(0, maxLen);
    }

    private Long saveTransaction(CompraRequest request, ProcessedPurchaseResult result) {
        Compra compra = new Compra();
        compra.setFecha(LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")));

        compra.setProveedor(safeTruncate(request.getProveedor(), 255));
        compra.setNroComprobante(safeTruncate(request.getNroComprobante(), 255));

        compra.setUsuarioId(request.getUsuarioId());
        compra.setTotalCompra(result.getTotalCompra());

        // Returns the ID from the GeneratedKeyHolder
        Long compraId = compraRepository.saveCompra(compra);

        // Link details to parent ID
        result.getDetallesToSave().forEach(d -> d.setCompraId(compraId));
        compraRepository.saveDetalles(result.getDetallesToSave());

        return compraId;
    }

    /**
     * Increments stock.
     * Package-private for Unit Testing.
     */
    void updateStockFromDetails(List<CompraItemRequest> items) {
        for (CompraItemRequest item : items) {
            try {
                stockRepository.addStock(item.getProductoId(), item.getUbicacionId(), item.getCantidad());
            } catch (DataAccessException e) {
                // [KEPT] This is a critical check for Location Existence
                throw new BusinessRuleException(
                        "Error al agregar stock: Verifique que la Ubicación ID " + item.getUbicacionId() + " exista.");
            }
        }
    }
}