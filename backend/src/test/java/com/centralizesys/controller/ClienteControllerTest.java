package com.centralizesys.controller;

import com.centralizesys.service.ClienteService;
import com.centralizesys.service.VentaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClienteController.class)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.ActiveProfiles("test")
class ClienteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClienteService clienteService;

    @MockBean
    private VentaService ventaService;

    @MockBean
    private com.centralizesys.service.AuditoriaService auditoriaService;

    // Security Mocks
    @MockBean
    private com.centralizesys.security.JwtTokenProvider jwtTokenProvider;
    @MockBean
    private com.centralizesys.security.CustomUserDetailsService customUserDetailsService;
    @MockBean
    private com.centralizesys.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("updateNombre - Calls Service and Returns OK")
    void updateNombre_CallsServiceAndReturnsOk() throws Exception {
        // Arrange
        Long clientId = 1L;
        String nuevoNombre = "Updated Name";
        Long usuarioId = 10L;

        // Act
        // Mocking static methods is complex, so we might get a default 0L or it fails.
        // For this test, we verify the interaction with the service assuming it passes.
        // Actually, if we just run it, SecurityUtils might just return a default value or throw.
        // Since we are not strictly testing SecurityUtils here, we'll let it execute.
        // We'll use any() for userId since it depends on the context.
        mockMvc.perform(put("/api/clientes/{id}/nombre", clientId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nombre", nuevoNombre))))
                .andExpect(status().isOk());

        // Assert
        verify(clienteService).updateClienteNombre(eq(clientId), eq(nuevoNombre), org.mockito.ArgumentMatchers.any());
    }
}
