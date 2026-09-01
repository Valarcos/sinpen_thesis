package com.centralizesys.controller;

import com.centralizesys.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.centralizesys.security.SecurityUtils;
import com.centralizesys.repository.UsuarioRepository;
import com.centralizesys.model.auth.Usuario;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/api/reportes")
public class ReportController {

    private final ReportService reportService;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioRepository usuarioRepository;

    public ReportController(ReportService reportService, PasswordEncoder passwordEncoder, UsuarioRepository usuarioRepository) {
        this.reportService = reportService;
        this.passwordEncoder = passwordEncoder;
        this.usuarioRepository = usuarioRepository;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @GetMapping("/ganancias")
    public ResponseEntity<Map<String, Object>> getGananciasMensuales(
            @RequestHeader(value = "X-Security-Pin", required = false) String securityPin,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        validateSecurityPin(securityPin);

        LocalDate now = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();

        return ResponseEntity.ok(reportService.getGananciasMensuales(y, m));
    }

    /**
     * Unified statistics endpoint supporting three time granularities:
     *   - Yearly:  GET /api/reportes/estadisticas?year=2026
     *   - Monthly: GET /api/reportes/estadisticas?year=2026&month=6
     *   - Daily:   GET /api/reportes/estadisticas?year=2026&month=6&day=24
     *
     * Returns both Commercial Revenue (accrual) and Cash Flow sections.
     * If no parameters are provided, defaults to the current month.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @GetMapping("/estadisticas")
    public ResponseEntity<com.centralizesys.model.sales.ReportesEstadisticasDTO> getEstadisticas(
            @RequestHeader(value = "X-Security-Pin", required = false) String securityPin,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day) {

        validateSecurityPin(securityPin);

        LocalDate now = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        Integer resolvedYear  = year  != null ? year  : now.getYear();
        Integer resolvedMonth = month; // null means "full year"
        Integer resolvedDay   = day;   // null means "full month"

        // If only year is provided with no month, we default to "full year" mode.
        // If neither is provided, default to current month for a meaningful dashboard view.
        if (year == null && month == null) {
            resolvedMonth = now.getMonthValue();
        }

        return ResponseEntity.ok(reportService.getEstadisticas(resolvedYear, resolvedMonth, resolvedDay));
    }

    private void validateSecurityPin(String providedPin) {
        if (providedPin == null || providedPin.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PIN de seguridad requerido para reportes.");
        }

        Long userId = SecurityUtils.getAuthenticatedUserId();
        Usuario user = usuarioRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado."));

        String hash = user.getSecurityPin();
        if (hash == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PIN de seguridad no configurado para este usuario. Contacte al Administrador.");
        }

        if (!passwordEncoder.matches(providedPin.trim(), hash)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PIN de seguridad incorrecto.");
        }
    }
}
