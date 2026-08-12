package com.centralizesys.controller;

import com.centralizesys.model.dto.PageResponse;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.product.ProductRequest;
import com.centralizesys.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService service;

    @MockBean
    private com.centralizesys.service.AuditoriaService auditoriaService;

    // Security Mocks need to be present even if filters are off for context loading
    // usually,
    // or sometimes just having them mocked avoids bean creation issues if they are
    // injected elsewhere.
    // However, @WebMvcTest usually only loads the controller.
    // If GlobalExceptionHandler or others need them, we might need them.
    // Safe to mock them if previous tests had them.
    @MockBean
    private com.centralizesys.security.JwtTokenProvider jwtTokenProvider;
    @MockBean
    private com.centralizesys.security.CustomUserDetailsService customUserDetailsService;
    @MockBean
    private com.centralizesys.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("Get All Products (Browse) returns PageResponse")
    void getAll_ReturnsPage() throws Exception {
        Product p = Product.builder().id(1L).codigo("CODE").descripcion("Desc")
                .precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0)
                .cantidadStock(5L).activo(true).build();
        PageResponse<Product> pageResponse = new PageResponse<>(
                List.of(p), 0L, 20L, 1L, 1L);

        when(service.getAllOrSearch(null, 0L, 20L)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/productos")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].codigo").value("CODE"));
    }

    @Test
    @DisplayName("Search Products returns PageResponse")
    void search_ReturnsPage() throws Exception {
        Product p = Product.builder().id(1L).codigo("CODE").descripcion("Desc")
                .precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0)
                .cantidadStock(5L).activo(true).build();
        PageResponse<Product> pageResponse = new PageResponse<>(
                List.of(p), 0L, 100L, 1L, 1L);

        when(service.getAllOrSearch("query", 0L, 20L)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/productos")
                        .param("search", "query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].codigo").value("CODE"));
    }

    @Test
    @DisplayName("Create product delegates to createWithStock")
    void create_Success() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setCodigo("CODE");
        req.setDescripcion("Desc");
        req.setPrecioCosto(10.0);
        req.setPrecioMayorista(10.0);
        req.setPrecioMinorista(20.0);
        req.setCantidad(5);
        req.setUbicacionId(1L);

        Product saved = Product.builder().id(1L).codigo("CODE").descripcion("Desc")
                .precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0)
                .cantidadStock(5L).activo(true).build();

        when(service.createWithStock(any(Product.class), eq(1L), eq(5L), any())).thenReturn(saved);

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        verify(service).createWithStock(any(Product.class), eq(1L), eq(5L), any());
    }

    @Test
    @DisplayName("Update product delegates to service")
    void update_Success() throws Exception {
        ProductRequest req = new ProductRequest();
        req.setCodigo("CODE");
        req.setDescripcion("Updated");
        req.setPrecioCosto(10.0);
        req.setPrecioMayorista(10.0);
        req.setPrecioMinorista(25.0);

        mockMvc.perform(put("/api/productos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(service).update(eq(1L), any(Product.class), any());
    }

    @Test
    @DisplayName("Delete product delegates to service")
    void delete_Success() throws Exception {
        mockMvc.perform(delete("/api/productos/1"))
                .andExpect(status().isNoContent());

        verify(service).deleteById(eq(1L), any());
    }

    @Test
    @DisplayName("Create with invalid data returns 400")
    void create_InvalidData() throws Exception {
        // Assuming validation throws BusinessRuleException
        when(service.createWithStock(any(Product.class), any(), any(), any()))
                .thenThrow(new com.centralizesys.exception.BusinessRuleException("Invalid"));

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Delete non-existent returns 404")
    void delete_NotFound() throws Exception {
        doThrow(new com.centralizesys.exception.ResourceNotFoundException("Product", 999L))
                .when(service).deleteById(eq(999L), any());

        mockMvc.perform(delete("/api/productos/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Get Alerts returns list")
    void getAlerts_Success() throws Exception {
        Product p = Product.builder().id(1L).codigo("CODE").descripcion("Desc")
                .precioCosto(10.0).precioMayorista(10.0).precioMinorista(20.0)
                .cantidadStock(-5L).activo(true).build();
        when(service.getLowStockAlerts()).thenReturn(List.of(p));

        mockMvc.perform(get("/api/productos/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cantidadStock").value(-5));
    }
}

