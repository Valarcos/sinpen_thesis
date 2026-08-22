package com.centralizesys.controller;

import com.centralizesys.model.purchase.CompraRequest;
import com.centralizesys.model.purchase.CompraResponse;
import com.centralizesys.service.CompraService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraService compraService;

    public CompraController(CompraService compraService) {
        this.compraService = compraService;
    }

    /**
     * Registers a new incoming stock purchase.
     * Expects all products to already exist (variants created if necessary).
     */
    @com.centralizesys.aspect.Idempotent
    @PostMapping
    public ResponseEntity<CompraResponse> registrarCompra(@RequestBody CompraRequest request) {
        request.setUsuarioId(com.centralizesys.security.SecurityUtils.getAuthenticatedUserId());
        CompraResponse response = compraService.registrarCompra(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}