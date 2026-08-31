package com.centralizesys.security;

import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioRole;
import com.centralizesys.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void setUp() {
        customUserDetailsService = new CustomUserDetailsService(usuarioRepository);
    }

    @Test
    @DisplayName("loadUserByUsername returns UserDetails for an active user")
    void loadUserByUsername_Success() {
        Usuario mockUsuario = new Usuario(1L, "Admin", "admin@test.com", "hash123", null,
                UsuarioRole.ADMIN, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), true);

        when(usuarioRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(mockUsuario));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("admin@test.com");

        assertNotNull(userDetails);
        assertEquals("admin@test.com", userDetails.getUsername());
        assertEquals("hash123", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void loadUserByUsername_NotFound_ThrowsException() {
        when(usuarioRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("unknown@test.com"));
    }

    @Test
    void loadUserByUsername_InactiveUser_ThrowsException() {
        // Mock returning empty for inactive user because the query strictly uses findByEmail
        Usuario inactiveUser = new Usuario(5L, "Deleted User", "deleted@test.com", "hash", null,
                UsuarioRole.EMPLEADO, LocalDateTime.of(2023, java.time.Month.JANUARY, 1, 12, 0), false);

        when(usuarioRepository.findByEmail("deleted@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("deleted@test.com"));
    }
}
