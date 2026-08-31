package com.centralizesys.controller;

import com.centralizesys.model.audit.Auditoria;
import com.centralizesys.service.AuditoriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /**
     * Retrieves audit logs.
     * Defaults to the last 15 days if no dates are provided.
     * Format: YYYY-MM-DDTHH:mm:ss (ISO 8601)
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @GetMapping
    public ResponseEntity<List<Auditoria>> getLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {

        LocalDateTime startParam = (start != null) ? start : LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).minusDays(15);
        LocalDateTime endParam = (end != null) ? end : LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));

        List<Auditoria> logs = auditoriaService.findByDateRange(startParam, endParam);
        return ResponseEntity.ok(logs);
    }
}