package com.centralizesys.controller;

import com.centralizesys.service.BackupService;
import com.centralizesys.model.dto.BackupFileDTO;
import com.centralizesys.security.SecurityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import com.centralizesys.exception.InfrastructureException;

@RestController
@RequestMapping("/api/backups")
public class BackupController {

    private static final Logger log = LoggerFactory.getLogger(BackupController.class);
    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @com.centralizesys.aspect.Idempotent
    @PostMapping("/create")
    public ResponseEntity<String> triggerManualBackup() {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        backupService.performBackup(BackupService.BackupType.MANUAL, userId);
        return ResponseEntity.ok("Backup manual iniciado. Verifique el registro de auditoría.");
    }

    @GetMapping
    public ResponseEntity<List<BackupFileDTO>> listBackups() {
        return ResponseEntity.ok(backupService.listBackups());
    }

    @GetMapping("/last")
    public ResponseEntity<BackupFileDTO> getLastBackup() {
        List<BackupFileDTO> backups = backupService.listBackups();
        if (backups.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // List is already sorted DESC by date in service
        return ResponseEntity.ok(backups.get(0));
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadBackup(@PathVariable String filename) {
        try {
            File file = backupService.getBackupFile(filename);
            if (!file.exists()) {
                throw new FileNotFoundException("File not found: " + filename);
            }

            Resource resource = new FileSystemResource(file);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(file.length())
                    .body(resource);
        } catch (FileNotFoundException e) {
            log.warn("Download failed: File not found {}", filename);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Download failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @com.centralizesys.aspect.Idempotent
    @PostMapping("/restore/{filename}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> restoreDatabase(@PathVariable String filename) {
        try {
            File file = backupService.getBackupFile(filename);
            backupService.restoreDatabase(file);
            return ResponseEntity.ok("Restauración iniciada con éxito. El servidor se reiniciará en 1 segundo.");
        } catch (Exception e) {
            throw new InfrastructureException("Fallo en la restauración: " + e.getMessage(), e);
        }
    }

    // TODO: DB Growth Analysis - Address the rapidly increasing size of the database backups.
    // Investigate if data types are properly optimized and establish an industry-standard policy
    // on when it is acceptable to permanently delete data versus using logical (soft) deletes.
    @com.centralizesys.aspect.Idempotent
    @PostMapping(value = "/upload-restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> restoreFromUpload(
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".sql")) {
            return ResponseEntity.badRequest().body("Solo se permiten archivos .sql");
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("El archivo está vacío y no contiene instrucciones SQL");
        }
        try {
            java.nio.file.Path tempDir = com.centralizesys.config.DataPathConfig.resolve("temp");
            if (!java.nio.file.Files.exists(tempDir)) {
                java.nio.file.Files.createDirectories(tempDir);
            }
            java.nio.file.Path tempPath = java.nio.file.Files.createTempFile(tempDir, "restore_", ".sql");
            File tempFile = tempPath.toFile();
            file.transferTo(tempFile);

            try {
                backupService.restoreDatabase(tempFile);
            } finally {
                if (tempFile.exists() && !tempFile.delete()) {
                    System.err.println("Failed to delete temp file: " + tempFile.getAbsolutePath());
                }
            }
            return ResponseEntity.ok("Restauración iniciada con éxito. El servidor se reiniciará en 1 segundo.");
        } catch (Exception e) {
            throw new InfrastructureException("Fallo en la restauración: " + e.getMessage(), e);
        }
    }
}
