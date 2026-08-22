package com.centralizesys.service;

import com.centralizesys.model.audit.Auditoria;
import com.centralizesys.repository.AuditoriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditoriaService {

    private final AuditoriaRepository repository;

    private static final Logger logger = LoggerFactory.getLogger(AuditoriaService.class);

    public AuditoriaService(AuditoriaRepository repository) {
        this.repository = repository;
    }

    /**
     * Records an audit log.
     * Use of Propagation.SUPPORTS allows it to participate in existing transactions
     * but the try-catch ensures it doesn't crash the main business logic if it
     * fails.
     */
    private String safeTruncate(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) return str;
        if (Character.isHighSurrogate(str.charAt(maxLen - 1))) {
            return str.substring(0, maxLen - 1);
        }
        return str.substring(0, maxLen);
    }

    @Transactional
    public void registrarAccion(Long usuarioId, String accion, String detalles) {
        try {
            repository.save(usuarioId, accion, safeTruncate(detalles, 255));
        } catch (Exception e) {
            logger.error("FALLO AUDITORIA: No se pudo registrar la acción '{}'. Error: {}", accion, e.getMessage());
        }
    }

    // Needed for the Controller to display logs
    public List<Auditoria> findByDateRange(java.time.LocalDateTime startDateTime, LocalDateTime endDateTime) {
        return repository.findByDateRange(startDateTime, endDateTime);
    }
}