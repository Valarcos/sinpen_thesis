package com.centralizesys.controller;

import com.centralizesys.service.AuditoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditoriaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditoriaService auditoriaService;

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void testGetLogs_AsAdmin_Returns200() throws Exception {
        given(auditoriaService.findByDateRange(any(), any())).willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/auditoria"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "owner", roles = { "OWNER" })
    void testGetLogs_AsOwner_Returns200() throws Exception {
        given(auditoriaService.findByDateRange(any(), any())).willReturn(Collections.emptyList());

        mockMvc.perform(get("/api/auditoria"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "employee", roles = { "EMPLEADO" })
    void testGetLogs_AsEmployee_Returns403() throws Exception {
        mockMvc.perform(get("/api/auditoria"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetLogs_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/auditoria"))
                .andExpect(status().isUnauthorized());
    }
}
