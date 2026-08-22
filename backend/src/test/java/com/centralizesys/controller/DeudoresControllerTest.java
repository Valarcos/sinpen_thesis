package com.centralizesys.controller;

import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.security.CustomUserDetails;
import com.centralizesys.service.DeudoresService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeudoresController.class)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.ActiveProfiles("test")
class DeudoresControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeudoresService deudoresService;

    @MockBean
    private com.centralizesys.service.AuditoriaService auditoriaService;

    // Security Mocks
    @MockBean
    private com.centralizesys.security.JwtTokenProvider jwtTokenProvider;
    @MockBean
    private com.centralizesys.security.CustomUserDetailsService customUserDetailsService;
    @MockBean
    private com.centralizesys.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("pagarDeuda uses SecurityContext ID instead of Request Body")
    void pagarDeuda_UsesSecurityContext() throws Exception {
        Long userId = 55L;
        Long deudaId = 1L;

        // Mock Security Context
        CustomUserDetails mockUser = mock(CustomUserDetails.class);
        when(mockUser.getId()).thenReturn(userId);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(mockUser);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        try {
            // Prepare Request (List)
            PagoDeudaRequest pago = new PagoDeudaRequest();
            pago.setMontoPago(50.0);
            pago.setMetodoPagoId(1L); // Mock ID
            pago.setObservaciones("Test Note");

            java.util.List<PagoDeudaRequest> requests = java.util.List.of(pago);

            // Mock Service Response
            DeudaResponse response = new DeudaResponse(); // Empty response fine for test
            when(deudoresService.registrarPago(eq(deudaId), anyList(), eq(userId))).thenReturn(response);

            // Act
            mockMvc.perform(post("/api/deudores/" + deudaId + "/pagar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests)))
                    .andExpect(status().isOk());

            // Assert
            verify(deudoresService).registrarPago(eq(deudaId), anyList(), eq(userId));

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("getByVentaId_Returns200_WhenServiceReturnsDebt")
    void getByVentaId_Returns200_WhenServiceReturnsDebt() throws Exception {
        Long ventaId = 1L;
        DeudaResponse response = new DeudaResponse();
        response.setId(10L);
        response.setVentaId(ventaId);
        response.setEstado("PENDIENTE");

        when(deudoresService.getByVentaId(ventaId)).thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/deudores/venta/" + ventaId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").value(10L))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.ventaId").value(ventaId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.estado").value("PENDIENTE"));

        verify(deudoresService).getByVentaId(ventaId);
    }

    @Test
    @DisplayName("getByVentaId_Returns404_WhenDebtNotFound")
    void getByVentaId_Returns404_WhenDebtNotFound() throws Exception {
        Long ventaId = 999L;
        when(deudoresService.getByVentaId(ventaId))
                .thenThrow(new com.centralizesys.exception.ResourceNotFoundException("Deuda de Venta", ventaId));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/deudores/venta/" + ventaId))
                .andExpect(status().isNotFound());

        verify(deudoresService).getByVentaId(ventaId);
    }
}

