package com.centralizesys.controller;

import com.centralizesys.model.auth.LoginRequest;
import com.centralizesys.model.auth.RegisterRequest;
import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioResponse;
import com.centralizesys.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Endpoint for user login.
     * Returns User Details on success, or 400 Bad Request on failure.
     */
    @PostMapping("/login")
    public ResponseEntity<UsuarioResponse> login(@RequestBody LoginRequest request) {
        // Service throws BusinessRuleException if invalid, handled by
        // GlobalExceptionHandler
        Usuario user = usuarioService.login(request.getEmail(), request.getPassword());

        // Map Entity -> Response DTO
        UsuarioResponse response = new UsuarioResponse(
                user.getId(),
                user.getNombre(),
                user.getEmail(),
                user.getRol().name());

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to register new admin users.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {
        usuarioService.registrarUsuario(
                request.getNombre(),
                request.getEmail(),
                request.getPassword(),
                request.getRol(),
                request.getSecurityPin());
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    /**
     * Endpoint to list all users.
     * Admin Only.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> getAll() {
        List<Usuario> users = usuarioService.getAll();

        List<UsuarioResponse> response = users.stream()
                .map(u -> new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(), u.getRol().name()))
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to delete a user.
     * Admin Only.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        usuarioService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint to update a user's details.
     * Admin Only.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id,
                                       @RequestBody com.centralizesys.model.auth.UpdateUserRequest request) {
        usuarioService.update(id, request);
        return ResponseEntity.noContent().build();
    }
}