package com.centralizesys.controller;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.cheque.AlertaCheque;
import com.centralizesys.model.cheque.AlertaChequeRequest;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.repository.AlertaChequeRepository;
import com.centralizesys.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChequeApiIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertaChequeRepository alertaChequeRepository;

    private Long testProductId;
    private Long testUserId;

    @BeforeEach
    void setupData() {
        // Build MockMvc programmatically to satisfy IDE autowire inspections
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .apply(springSecurity())
                .build();

        this.testUserId = createTestUser();
        // Create product with Price = 100, Stock = 100
        this.testProductId = createTestProduct("TEST-CHQ-API", 100.0, 100L);
    }

    @Test
    @DisplayName("IT-CHQ-API-01: GIVEN a sale with cheques WHEN submitted to API THEN Venta and Alertas are created without FIADO")
    @WithMockUser(username = "test@admin.com", roles = { "ADMIN" })
    void testRegistrarVentaConChequesViaApi() throws Exception {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(2L); // Total $200

        AlertaChequeRequest cheque1 = new AlertaChequeRequest();
        cheque1.setMonto(100.0);
        cheque1.setFechaCobro(LocalDate.now().plusDays(10));

        AlertaChequeRequest cheque2 = new AlertaChequeRequest();
        cheque2.setMonto(100.0);
        cheque2.setFechaCobro(LocalDate.now().plusDays(20));

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Cliente Cheque API");
        request.setItems(List.of(item));
        request.setUsuarioId(testUserId);
        request.setCheques(List.of(cheque1, cheque2));
        request.setPagos(Collections.emptyList());

        // Act & Assert
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            mockedSecurityUtils.when(SecurityUtils::getAuthenticatedUserId).thenReturn(testUserId);

            MvcResult result = mockMvc.perform(post("/api/ventas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            VentaResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), VentaResponse.class);

            // Assert (Then)
            assertNotNull(response.getId(), "Venta ID must not be null");
            assertEquals("ACTIVA", response.getEstado(), "Venta should be active");

            // Verify Alertas Cheques in DB
            List<AlertaCheque> alertas = alertaChequeRepository.findByVentaId(response.getId());
            assertEquals(2, alertas.size(), "Two cheques should be persisted in DB");
            assertEquals("PENDIENTE", alertas.get(0).getEstado());
            assertEquals("PENDIENTE", alertas.get(1).getEstado());

            // Verify no FIADO debt was created
            Integer debtCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM deudores WHERE venta_id = ?", Integer.class, response.getId());
            assertEquals(0, debtCount, "No FIADO debt should be created when cheques cover the full amount");
        }
    }

    @Test
    @DisplayName("IT-CHQ-API-02: GIVEN an active cheque sale WHEN cancelled via API THEN cascade anula cheques")
    @WithMockUser(username = "test@admin.com", roles = { "ADMIN" })
    void testAnularVentaCascadesToChequesViaApi() throws Exception {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(testProductId);
        item.setCantidad(1L);

        AlertaChequeRequest cheque1 = new AlertaChequeRequest();
        cheque1.setMonto(100.0);
        cheque1.setFechaCobro(LocalDate.now().plusDays(5));

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Cancel Cheque API");
        request.setItems(List.of(item));
        request.setUsuarioId(testUserId);
        request.setCheques(List.of(cheque1));
        request.setPagos(Collections.emptyList());

        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            mockedSecurityUtils.when(SecurityUtils::getAuthenticatedUserId).thenReturn(testUserId);

            MvcResult createResult = mockMvc.perform(post("/api/ventas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            VentaResponse response = objectMapper.readValue(createResult.getResponse().getContentAsString(), VentaResponse.class);
            Long ventaId = response.getId();

            // Act (Anular)
            mockMvc.perform(post("/api/ventas/" + ventaId + "/anular"))
                    .andExpect(status().isOk());

            // Assert
            List<AlertaCheque> alertas = alertaChequeRepository.findByVentaId(ventaId);
            assertEquals(1, alertas.size());
            assertEquals("ANULADA", alertas.get(0).getEstado(), "Cheque should be cancelled when the sale is voided");
        }
    }
}
