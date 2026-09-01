package com.centralizesys.controller;

import com.centralizesys.model.auth.Usuario;
import com.centralizesys.repository.UsuarioRepository;
import com.centralizesys.security.CustomUserDetails;
import com.centralizesys.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReportControllerTest {

    @Mock
    private ReportService reportService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ReportController reportController;

    private AutoCloseable mocks;
    private final Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        CustomUserDetails userDetails = new CustomUserDetails(
                testUserId, "admin@test.com", "pass", "Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    @DisplayName("GIVEN null security PIN WHEN getGananciasMensuales THEN throws FORBIDDEN")
    void testValidatePin_NullPin() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            reportController.getGananciasMensuales(null, 2026, 8);
        });
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("PIN de seguridad requerido para reportes.", ex.getReason());
        verify(reportService, never()).getGananciasMensuales(anyInt(), anyInt());
    }

    @Test
    @DisplayName("GIVEN blank security PIN WHEN getEstadisticas THEN throws FORBIDDEN")
    void testValidatePin_BlankPin() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            reportController.getEstadisticas("   ", 2026, 8, null);
        });
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("PIN de seguridad requerido para reportes.", ex.getReason());
    }

    @Test
    @DisplayName("GIVEN user with no PIN configured WHEN getGananciasMensuales THEN throws FORBIDDEN")
    void testValidatePin_NoPinConfigured() {
        Usuario mockUser = new Usuario();
        mockUser.setSecurityPin(null); // No PIN in DB

        when(usuarioRepository.findById(testUserId)).thenReturn(Optional.of(mockUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            reportController.getGananciasMensuales("1234", 2026, 8);
        });
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("PIN de seguridad no configurado para este usuario. Contacte al Administrador.", ex.getReason());
    }

    @Test
    @DisplayName("GIVEN incorrect PIN WHEN getGananciasMensuales THEN throws FORBIDDEN")
    void testValidatePin_IncorrectPin() {
        Usuario mockUser = new Usuario();
        mockUser.setSecurityPin("hashed_correct_pin");

        when(usuarioRepository.findById(testUserId)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrong_pin", "hashed_correct_pin")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            reportController.getGananciasMensuales("wrong_pin", 2026, 8);
        });
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("PIN de seguridad incorrecto.", ex.getReason());
    }

    @Test
    @DisplayName("GIVEN correct PIN WHEN getGananciasMensuales THEN calls service and returns OK")
    void testValidatePin_CorrectPin() {
        Usuario mockUser = new Usuario();
        mockUser.setSecurityPin("hashed_correct_pin");

        when(usuarioRepository.findById(testUserId)).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("1234", "hashed_correct_pin")).thenReturn(true);
        when(reportService.getGananciasMensuales(2026, 8)).thenReturn(java.util.Collections.emptyMap());

        var response = reportController.getGananciasMensuales("1234", 2026, 8);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(reportService, times(1)).getGananciasMensuales(2026, 8);
    }
}
