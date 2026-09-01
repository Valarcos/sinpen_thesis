package com.centralizesys.controller;

import com.centralizesys.service.LegacyFinancialImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LegacyFinancialImportService legacyService;

    @Test
    @DisplayName("GIVEN EMPLEADO WHEN importLegacyExcel THEN returns FORBIDDEN")
    @WithMockUser(roles = "EMPLEADO")
    void importLegacyExcel_EmpleadoForbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "data".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/import/legacy")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GIVEN ADMIN WHEN importLegacyExcel THEN returns OK")
    @WithMockUser(roles = "ADMIN")
    void importLegacyExcel_AdminSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "data".getBytes());

        when(legacyService.importLegacyFile(anyString())).thenReturn("Success");

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/import/legacy")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GIVEN OWNER WHEN importLegacyExcel THEN returns OK")
    @WithMockUser(roles = "OWNER")
    void importLegacyExcel_OwnerSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "data".getBytes());

        when(legacyService.importLegacyFile(anyString())).thenReturn("Success");

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/import/legacy")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GIVEN Unauthenticated WHEN importLegacyExcel THEN returns UNAUTHORIZED")
    void importLegacyExcel_Unauthenticated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "data".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/import/legacy")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
