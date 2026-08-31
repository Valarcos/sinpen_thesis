package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.auth.UpdateUserRequest;
import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioRole;
import com.centralizesys.repository.UsuarioRepository;
import com.centralizesys.security.SecurityUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private UsuarioService usuarioService;

    // --- LOGIN TESTS ---

    @Test
    @DisplayName("Login Success: Returns user and audits action")
    void login_Success() {
        // Arrange
        Usuario user = new Usuario(1L, "Admin", "admin@test.com", "hashed123", null, UsuarioRole.ADMIN, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("raw123", "hashed123")).thenReturn(true);

        // Act
        Usuario result = usuarioService.login("admin@test.com", "raw123");

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(auditoriaService).registrarAccion(1L, "LOGIN", "Inicio de sesión exitoso.");
    }

    @Test
    @DisplayName("Login Failure: Email not found throws Friendly Exception")
    void login_EmailNotFound() {
        when(usuarioRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.login("unknown@test.com", "pass"));

        assertEquals("El correo electrónico no existe en el sistema.", ex.getMessage());
        verify(auditoriaService, never()).registrarAccion(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Login Failure: Wrong Password throws Friendly Exception")
    void login_WrongPassword() {
        Usuario user = new Usuario(1L, "Admin", "admin@test.com", "hashed123", null, UsuarioRole.ADMIN, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);
        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed123")).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.login("admin@test.com", "wrong"));

        assertEquals("La contraseña es incorrecta.", ex.getMessage());
        verify(auditoriaService, never()).registrarAccion(anyLong(), anyString(), anyString());
    }

    // --- REGISTRATION TESTS ---

    @Test
    @DisplayName("Register Success: Hashes password and securityPin and saves")
    void register_Success() {
        when(usuarioRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("rawPass")).thenReturn("hashedPass");
        when(passwordEncoder.encode("1234")).thenReturn("hashedPin");

        usuarioService.registrarUsuario("New User", "new@test.com", "rawPass", "OWNER", "1234");

        verify(usuarioRepository).save(argThat(u -> u.getEmail().equals("new@test.com") &&
                u.getPasswordHash().equals("hashedPass") &&
                u.getSecurityPin().equals("hashedPin") &&
                u.getRol() == UsuarioRole.OWNER &&
                u.getNombre().equals("New User")));
    }

    @Test
    @DisplayName("Register Failure: Duplicate Email throws Exception")
    void register_DuplicateEmail() {
        when(usuarioRepository.findByEmail("prop@test.com")).thenReturn(Optional.of(new Usuario()));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.registrarUsuario("User", "prop@test.com", "pass", null, "EMPLEADO"));

        assertTrue(ex.getMessage().contains("ya está registrado"));
        verify(usuarioRepository, never()).save(any());
    }

    // --- UPDATE TESTS ---

    @Test
    @DisplayName("Update Success: Updates all provided fields including securityPin")
    void update_Success_AllFields() {
        // Arrange
        Usuario existing = new Usuario(1L, "Old Name", "old@test.com", "oldHash", null, UsuarioRole.EMPLEADO, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(usuarioRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("newPassword")).thenReturn("newHash");
        when(passwordEncoder.encode("5678")).thenReturn("newPinHash");

        UpdateUserRequest request = new UpdateUserRequest("New Name", "new@test.com", "newPassword", "ADMIN", "5678");

        try (var mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            mockedSecurityUtils.when(SecurityUtils::getAuthenticatedUserId).thenReturn(99L);

            // Act
            usuarioService.update(1L, request);

            // Assert
            verify(usuarioRepository).update(argThat(u -> u.getNombre().equals("New Name") &&
                    u.getEmail().equals("new@test.com") &&
                    u.getPasswordHash().equals("newHash") &&
                    u.getSecurityPin().equals("newPinHash") &&
                    u.getRol() == UsuarioRole.ADMIN));
            verify(auditoriaService).registrarAccion(99L, "UPDATE_USER", "Usuario actualizado: new@test.com");
        }
    }

    @Test
    @DisplayName("Update Failure: Cannot modify system user (id=0)")
    void update_CannotModifySystemUser() {
        UpdateUserRequest request = new UpdateUserRequest("Name", null, null, null, null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.update(0L, request));

        assertEquals("No se puede modificar al Usuario del Sistema.", ex.getMessage());
        verify(usuarioRepository, never()).update(any());
    }

    @Test
    @DisplayName("Update Failure: User not found throws ResourceNotFoundException")
    void update_UserNotFound() {
        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateUserRequest request = new UpdateUserRequest("Name", null, null, null, null);

        assertThrows(ResourceNotFoundException.class,
                () -> usuarioService.update(999L, request));
        verify(usuarioRepository, never()).update(any());
    }

    @Test
    @DisplayName("Update Failure: Duplicate email throws Exception")
    void update_DuplicateEmail() {
        Usuario existing = new Usuario(1L, "Name", "old@test.com", "hash", null, UsuarioRole.EMPLEADO, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);
        Usuario other = new Usuario(2L, "Other", "taken@test.com", "hash", null, UsuarioRole.EMPLEADO, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(usuarioRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(other));

        UpdateUserRequest request = new UpdateUserRequest("Juan", "juan@test.com", "pass123", "EMPLEADO", null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.update(1L, request));

        assertTrue(ex.getMessage().contains("ya está registrado"));
        verify(usuarioRepository, never()).update(any());
    }

    @Test
    @DisplayName("Update Failure: Invalid role throws Exception")
    void update_InvalidRole() {
        Usuario existing = new Usuario(1L, "Name", "test@test.com", "hash", null, UsuarioRole.EMPLEADO, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existing));

        UpdateUserRequest request = new UpdateUserRequest(null, null, null, "INVALID_ROLE", null);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.update(1L, request));

        assertEquals("Rol debe ser ADMIN, EMPLEADO o OWNER.", ex.getMessage());
        verify(usuarioRepository, never()).update(any());
    }

    @Test
    @DisplayName("Update Success: Same email does not trigger duplicate check")
    void update_SameEmail_NoConflict() {
        Usuario existing = new Usuario(1L, "Name", "same@test.com", "hash", null, UsuarioRole.EMPLEADO, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existing));

        UpdateUserRequest request = new UpdateUserRequest(null, "same@test.com", null, null, null);

        try (var mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            mockedSecurityUtils.when(SecurityUtils::getAuthenticatedUserId).thenReturn(99L);

            usuarioService.update(1L, request);

            // findByEmail should NOT be called when email is unchanged
            verify(usuarioRepository, never()).findByEmail(anyString());
            verify(usuarioRepository).update(existing);
        }
    }

    @Test
    @DisplayName("Update Success: Null/blank fields are ignored")
    void update_NullFields_Ignored() {
        Usuario existing = new Usuario(1L, "Original", "orig@test.com", "origHash", null, UsuarioRole.EMPLEADO, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(existing));

        UpdateUserRequest request = new UpdateUserRequest(null, "", "   ", null, null);

        try (var mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            mockedSecurityUtils.when(SecurityUtils::getAuthenticatedUserId).thenReturn(99L);

            usuarioService.update(1L, request);

            // Verify original values unchanged
            verify(usuarioRepository).update(argThat(u -> u.getNombre().equals("Original") &&
                    u.getEmail().equals("orig@test.com") &&
                    u.getPasswordHash().equals("origHash") &&
                    u.getRol() == UsuarioRole.EMPLEADO));
        }
    }

    // --- DELETE TESTS ---

    @Test
    @DisplayName("Delete Failure: Cannot delete Sistema user (id=0)")
    void delete_CannotDeleteSistemaUser() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> usuarioService.delete(0L));

        assertEquals("No se puede eliminar al Usuario del Sistema.", ex.getMessage());
        verify(usuarioRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("Delete Failure: Last active ADMIN cannot be deleted (lockout guard)")
    void delete_LastAdmin_ThrowsLockoutException() {
        // Arrange: target is an ADMIN, and they are the only one left
        Usuario lastAdmin = new Usuario(2L, "Sole Admin", "admin@test.com", "hash", null,
                UsuarioRole.ADMIN, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);

        try (var mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            mockedSecurityUtils.when(SecurityUtils::getAuthenticatedUserId).thenReturn(99L);

            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(lastAdmin));
            when(usuarioRepository.countActiveAdmins()).thenReturn(1L);

            // Act & Assert
            BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                    () -> usuarioService.delete(2L));

            assertTrue(ex.getMessage().contains("único Administrador activo"),
                    "Error message must mention the sole-admin restriction");
            verify(usuarioRepository, never()).deleteById(anyLong());
            verify(auditoriaService, never()).registrarAccion(anyLong(), anyString(), anyString());
        }
    }

    @Test
    @DisplayName("Delete Success: Non-last ADMIN can be deleted when others exist")
    void delete_NonLastAdmin_SucceedsWhenOthersExist() {
        // Arrange: 2 active ADMINs exist — deletion of one is permitted
        Usuario adminToDelete = new Usuario(2L, "Admin2", "admin2@test.com", "hash", null,
                UsuarioRole.ADMIN, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);

        try (var mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            mockedSecurityUtils.when(SecurityUtils::getAuthenticatedUserId).thenReturn(99L);

            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(adminToDelete));
            when(usuarioRepository.countActiveAdmins()).thenReturn(2L);

            // Act
            usuarioService.delete(2L);

            // Assert
            verify(usuarioRepository).deleteById(2L);
            verify(auditoriaService).registrarAccion(99L, "DELETE_USER", "Usuario eliminado: admin2@test.com");
        }
    }

    @Test
    @DisplayName("Delete Success: EMPLEADO can always be deleted without ADMIN count check")
    void delete_Empleado_DoesNotCheckAdminCount() {
        // Arrange: target is EMPLEADO — the admin count guard must NOT be invoked
        Usuario empleado = new Usuario(3L, "Emp", "emp@test.com", "hash", null,
                UsuarioRole.EMPLEADO, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);

        try (var mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            mockedSecurityUtils.when(SecurityUtils::getAuthenticatedUserId).thenReturn(99L);

            when(usuarioRepository.findById(3L)).thenReturn(Optional.of(empleado));

            // Act
            usuarioService.delete(3L);

            // Assert
            verify(usuarioRepository).deleteById(3L);
            // countActiveAdmins must NEVER be called for a non-ADMIN target
            verify(usuarioRepository, never()).countActiveAdmins();
        }
    }
}
