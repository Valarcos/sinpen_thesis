package com.centralizesys.controller;

import com.centralizesys.model.product .StockAdjustRequest;
import com.centralizesys.service.StockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockService stockService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getStockByProduct_ShouldReturnStockList() throws Exception {
        when(stockService.getStockByProduct(anyLong())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/stock/producto/1"))
                .andExpect(status().isOk());

        verify(stockService).getStockByProduct(1L);
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void addStock_ShouldCallService() throws Exception {
        StockAdjustRequest request = new StockAdjustRequest(1L, 10L, 5L);

        mockMvc.perform(post("/api/stock/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(stockService).addStock(1L, 10L, 5L);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void addStock_AsOwner_ShouldCallService() throws Exception {
        StockAdjustRequest request = new StockAdjustRequest(1L, 10L, 5L);

        mockMvc.perform(post("/api/stock/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(stockService).addStock(1L, 10L, 5L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void subtractStock_ShouldCallService() throws Exception {
        StockAdjustRequest request = new StockAdjustRequest(1L, 10L, 3L);

        mockMvc.perform(post("/api/stock/subtract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(stockService).subtractStock(1L, 10L, 3L);
    }

    @Test
    void addStock_WithoutAuth_ShouldReturnUnauthorized() throws Exception {
        StockAdjustRequest request = new StockAdjustRequest(1L, 10L, 5L);

        mockMvc.perform(post("/api/stock/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }
}
