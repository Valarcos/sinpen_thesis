package com.centralizesys.controller;

import com.centralizesys.model.product.StockLocation;
import com.centralizesys.model.product.StockAdjustRequest;
import com.centralizesys.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for manual stock adjustments.
 * Exposes existing StockRepository methods as REST endpoints.
 * All stock changes are audited.
 */
@RestController
@RequestMapping("/api/stock")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO', 'OWNER')")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * Get stock per location for a specific product.
     */
    @GetMapping("/producto/{id}")
    public ResponseEntity<List<StockLocation>> getStockByProduct(@PathVariable Long id) {
        List<StockLocation> stockLocations = stockService.getStockByProduct(id);
        return ResponseEntity.ok(stockLocations);
    }

    /**
     * Add stock to a specific product at a specific location.
     */
    @PostMapping("/add")
    public ResponseEntity<Void> addStock(@RequestBody StockAdjustRequest request) {
        stockService.addStock(request.productoId(), request.ubicacionId(), request.cantidad());
        return ResponseEntity.ok().build();
    }

    /**
     * Subtract stock from a specific product at a specific location.
     */
    @PostMapping("/subtract")
    public ResponseEntity<Void> subtractStock(@RequestBody StockAdjustRequest request) {
        stockService.subtractStock(request.productoId(), request.ubicacionId(), request.cantidad());
        return ResponseEntity.ok().build();
    }
}
