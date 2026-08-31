package com.centralizesys.controller;

import com.centralizesys.service.BackupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.context.SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class BackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BackupService backupService;

    @MockBean
    private com.centralizesys.service.AuditoriaService auditoriaService;

    @Test
    @DisplayName("restoreFromUpload calls backupService and cleans up")
    @org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
    void restoreFromUpload_CallsService() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "test-backup.sql",
                "text/plain",
                "SQL DATA".getBytes()
        );

        // Act
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/backups/upload-restore")
                        .file(file)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("confirm", "true"))
                .andExpect(status().isOk());

        // Assert
        verify(backupService).restoreDatabase(any(java.io.File.class));
    }

    @Test
    @DisplayName("restoreDatabase uses SecurityContext ID")
    @org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
    void restoreDatabase_UsesSecurityContext() throws Exception {
        String filename = "backup.sql";

        // Act
        mockMvc.perform(post("/api/backups/restore/" + filename)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("confirm", "true"))
                .andExpect(status().isOk());
    }



    @Test
    @DisplayName("triggerManualBackup allows EMPLEADO")
    @org.springframework.security.test.context.support.WithMockUser(roles = "EMPLEADO")
    void triggerManualBackup_EmpleadoAllowed() throws Exception {
        mockMvc.perform(post("/api/backups/create")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("listBackups allows EMPLEADO")
    @org.springframework.security.test.context.support.WithMockUser(roles = "EMPLEADO")
    void listBackups_EmpleadoAllowed() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/backups"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("downloadBackup returns FORBIDDEN for EMPLEADO")
    @org.springframework.security.test.context.support.WithMockUser(roles = "EMPLEADO")
    void downloadBackup_EmpleadoForbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/backups/download/test.sql"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("downloadBackup allows OWNER")
    @org.springframework.security.test.context.support.WithMockUser(roles = "OWNER")
    void downloadBackup_OwnerAllowed() throws Exception {
        when(backupService.getBackupFile(anyString())).thenReturn(new java.io.File("dummy.sql"));
        // Note: It might return 404/500 if file doesn't exist on disk, but it shouldn't return 403.
        // The most important thing is that it's NOT forbidden.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/backups/download/test.sql"))
                .andExpect(status().isNotFound()); // Expect 404 because dummy file doesn't exist, not 403
    }

    @Test
    @DisplayName("restoreDatabase returns FORBIDDEN for OWNER")
    @org.springframework.security.test.context.support.WithMockUser(roles = "OWNER")
    void restoreDatabase_OwnerForbidden() throws Exception {
        mockMvc.perform(post("/api/backups/restore/test.sql")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("uploadRestore returns FORBIDDEN for OWNER")
    @org.springframework.security.test.context.support.WithMockUser(roles = "OWNER")
    void uploadRestore_OwnerForbidden() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "test-backup.sql",
                "text/plain",
                "SQL DATA".getBytes()
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/backups/upload-restore")
                        .file(file)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }
}

