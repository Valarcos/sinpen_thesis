package com.centralizesys.controller;

import com.centralizesys.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/api/reportes")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/ganancias")
    public ResponseEntity<Map<String, Object>> getGananciasMensuales(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

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
    @GetMapping("/estadisticas")
    public ResponseEntity<com.centralizesys.model.sales.ReportesEstadisticasDTO> getEstadisticas(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer day) {

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
}
