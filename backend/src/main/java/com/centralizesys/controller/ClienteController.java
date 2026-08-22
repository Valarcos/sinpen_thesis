package com.centralizesys.controller;

import com.centralizesys.model.client.ClienteResponse;
import com.centralizesys.service.ClienteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final com.centralizesys.service.VentaService ventaService;

    public ClienteController(ClienteService clienteService, com.centralizesys.service.VentaService ventaService) {
        this.clienteService = clienteService;
        this.ventaService = ventaService;
    }

    /**
     * Returns all active clients.
     * Includes saldo_a_favor so the frontend can conditionally render
     * the "Saldo a Favor" payment option when a client is selected.
     */
    @GetMapping
    public ResponseEntity<List<ClienteResponse>> getAll() {
        return ResponseEntity.ok(clienteService.getAll());
    }

    /**
     * Looks up a single client by name.
     * Used by the sales cart to resolve a typed name into a structured
     * ClienteResponse (including their saldo_a_favor).
     */
    @GetMapping("/buscar")
    public ResponseEntity<ClienteResponse> findByNombre(@RequestParam String nombre) {
        return clienteService.findByNombre(nombre)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/{id}/ventas")
    public ResponseEntity<List<com.centralizesys.model.sales.VentaResponse>> getVentasByClienteId(@PathVariable Long id) {
        return ResponseEntity.ok(ventaService.getVentasByClienteId(id));
    }

    @PutMapping("/{id}/nombre")
    public ResponseEntity<Void> updateNombre(@PathVariable Long id, @RequestBody java.util.Map<String, String> payload) {
        String nuevoNombre = payload.get("nombre");
        if (nuevoNombre == null || nuevoNombre.trim().isEmpty()) {
            throw new com.centralizesys.exception.BusinessRuleException("El nombre no puede estar vacío.");
        }
        Long authenticatedUserId = com.centralizesys.security.SecurityUtils.getAuthenticatedUserId();
        clienteService.updateClienteNombre(id, nuevoNombre.trim(), authenticatedUserId);
        return ResponseEntity.ok().build();
    }
}
