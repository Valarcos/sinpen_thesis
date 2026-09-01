package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.auth.UpdateUserRequest;
import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioRole;
import com.centralizesys.repository.UsuarioRepository;
import com.centralizesys.security.SecurityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Verifies credentials with EXPLICIT feedback for the elderly users.
     */
    public Usuario login(String email, String rawPassword) {
        // 1. Specific Check: Email Existence
        Usuario user = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessRuleException("El correo electrónico no existe en el sistema."));

        // 2. Specific Check: Password Match
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("La contraseña es incorrecta.");
        }

        // After successful check:
        auditoriaService.registrarAccion(user.getId(), "LOGIN", "Inicio de sesión exitoso.");

        return user;
    }

    /**
     * Registers a new user.
     * Handles password hashing and email duplication checks.
     */
    @Transactional
    public void registrarUsuario(String nombre, String email, String rawPassword, String rolStr, String securityPin) {
        // 1. Check if email already exists
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("El correo '" + email + "' ya está registrado.");
        }

        // 2. Create Object & Hash Password
        Usuario newUser = new Usuario();
        newUser.setNombre(nombre);
        newUser.setEmail(email);
        newUser.setPasswordHash(passwordEncoder.encode(rawPassword));

        if (rolStr != null && !rolStr.isBlank()) {
            String trimmedRol = rolStr.trim().toUpperCase();
            if (!trimmedRol.equals("ADMIN") && !trimmedRol.equals("EMPLEADO") && !trimmedRol.equals("OWNER")) {
                throw new BusinessRuleException("Rol debe ser ADMIN, EMPLEADO o OWNER.");
            }
            newUser.setRol(UsuarioRole.valueOf(trimmedRol));
        } else {
            newUser.setRol(UsuarioRole.EMPLEADO);
        }

        if (securityPin != null && !securityPin.isBlank()) {
            newUser.setSecurityPin(passwordEncoder.encode(securityPin.trim()));
        }

        // 3. Persist
        usuarioRepository.save(newUser);

    }

    public List<Usuario> getAll() {
        return usuarioRepository.findAll();
    }

    @Transactional
    public void delete(Long id) {
        if (id == 0L) {
            throw new BusinessRuleException("No se puede eliminar al Usuario del Sistema.");
        }

        Long currentUserId = SecurityUtils.getAuthenticatedUserId();
        if (id.equals(currentUserId)) {
            throw new BusinessRuleException("No puedes eliminar tu propia cuenta.");
        }

        // Check if exists (confirms target is active, since repository filters activo=true)
        Usuario toDelete = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        // Last-ADMIN lockout guard: prevent deleting the sole remaining active administrator.
        // Without this, the system would become permanently inaccessible.
        if (toDelete.getRol() == UsuarioRole.ADMIN) {
            Long activeAdminCount = usuarioRepository.countActiveAdmins();
            if (activeAdminCount <= 1L) {
                throw new BusinessRuleException(
                        "No se puede eliminar al único Administrador activo del sistema. Asigne otro Administrador primero.");
            }
        }

        usuarioRepository.deleteById(id);

        auditoriaService.registrarAccion(currentUserId, "DELETE_USER", "Usuario eliminado: " + toDelete.getEmail());
    }

    /**
     * Updates a user's details.
     * Only non-null fields in the request will be updated.
     */
    @Transactional
    public void update(Long id, UpdateUserRequest request) {
        if (id == 0L) {
            throw new BusinessRuleException("No se puede modificar al Usuario del Sistema.");
        }

        Usuario existing = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        updateNombre(existing, request.nombre());
        updateEmail(existing, request.email());
        updatePassword(existing, request.password());
        updateRol(existing, request.rol());
        updateSecurityPin(existing, request.securityPin());

        usuarioRepository.update(existing);

        Long currentUserId = SecurityUtils.getAuthenticatedUserId();
        auditoriaService.registrarAccion(currentUserId, "UPDATE_USER",
                "Usuario actualizado: " + existing.getEmail());
    }

    private void updateNombre(Usuario user, String nombre) {
        if (nombre != null && !nombre.isBlank()) {
            user.setNombre(nombre);
        }
    }

    private void updateEmail(Usuario user, String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        // Check email uniqueness if changing (merged if statements)
        if (!email.equals(user.getEmail()) && usuarioRepository.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("El correo '" + email + "' ya está registrado.");
        }
        user.setEmail(email);
    }

    private void updatePassword(Usuario user, String password) {
        if (password != null && !password.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(password));
        }
    }

    private void updateRol(Usuario user, String rol) {
        if (rol == null || rol.isBlank()) {
            return;
        }
        String trimmedRol = rol.trim().toUpperCase();
        if (!trimmedRol.equals("ADMIN") && !trimmedRol.equals("EMPLEADO") && !trimmedRol.equals("OWNER")) {
            throw new BusinessRuleException("Rol debe ser ADMIN, EMPLEADO o OWNER.");
        }
        user.setRol(UsuarioRole.valueOf(trimmedRol));
    }

    private void updateSecurityPin(Usuario user, String securityPin) {
        if (securityPin == null) {
            return;
        }
        if (securityPin.isBlank()) {
            user.setSecurityPin(null);
        } else {
            user.setSecurityPin(passwordEncoder.encode(securityPin.trim()));
        }
    }
}