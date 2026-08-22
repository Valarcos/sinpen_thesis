package com.centralizesys.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class ReportRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    private static final String FECHA_FIELD = ":fechaField";
    private static final String INGRESOS_TOTALES = "ingresos_totales";
    private static final String GANANCIA_NETA = "ganancia_neta";
    private static final String FECHA_CREACION = "vp.fecha_creacion";


    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    // --- LEGACY REPORT METHODS (preserved, still used by existing endpoints) ---

    public List<Map<String, Object>> getGananciasMensuales(int year) {
        String sql = """
            WITH DetallesAgrupados AS (
                SELECT
                    d.venta_id,
                    COALESCE(SUM(d.costo_snapshot * (d.cantidad - COALESCE(
                        (SELECT SUM(cantidad_devuelta) FROM devoluciones_venta WHERE detalle_venta_id = d.id), 0
                    ))), 0.0) as cogs_venta
                FROM detalles_venta d
                WHERE d.anulado = false OR d.anulado IS NULL
                GROUP BY d.venta_id
            )
            SELECT
                DATE_TRUNC('month', v.fecha) as mes,
                SUM(v.total_venta) - COALESCE(SUM((
                     SELECT SUM(dv.monto_reembolsado) FROM devoluciones_venta dv WHERE dv.venta_id = v.id
                )), 0.0) as ingresos_totales,
                SUM(d.cogs_venta) as cogs,
                (SUM(v.total_venta) - COALESCE(SUM((SELECT SUM(dv.monto_reembolsado) FROM devoluciones_venta dv WHERE dv.venta_id = v.id)), 0.0) - SUM(d.cogs_venta)) as ganancia_neta,
                ((SUM(v.total_venta) - COALESCE(SUM((SELECT SUM(dv.monto_reembolsado) FROM devoluciones_venta dv WHERE dv.venta_id = v.id)), 0.0) - SUM(d.cogs_venta)) / NULLIF(SUM(v.total_venta) - COALESCE(SUM((SELECT SUM(dv.monto_reembolsado) FROM devoluciones_venta dv WHERE dv.venta_id = v.id)), 0.0), 0)) * 100 as margen_porcentaje
            FROM ventas v
            LEFT JOIN DetallesAgrupados d ON v.id = d.venta_id
            WHERE v.estado NOT IN ('ANULADA', 'PENDIENTE', 'CANCELADA_PENDIENTE')
              AND EXTRACT(YEAR FROM v.fecha) = :year
            GROUP BY DATE_TRUNC('month', v.fecha)
            ORDER BY mes ASC
        """;

        MapSqlParameterSource params = new MapSqlParameterSource("year", year);
        return namedJdbcTemplate.queryForList(sql, params);
    }

    public Map<String, Object> getGananciaPorMes(int year, int month) {
        String sql = """
            WITH DetallesAgrupados AS (
                SELECT
                    d.venta_id,
                    COALESCE(SUM(d.costo_snapshot * (d.cantidad - COALESCE(
                        (SELECT SUM(cantidad_devuelta) FROM devoluciones_venta WHERE detalle_venta_id = d.id), 0
                    ))), 0.0) as cogs_venta
                FROM detalles_venta d
                WHERE d.anulado = false OR d.anulado IS NULL
                GROUP BY d.venta_id
            )
            SELECT
                SUM(v.total_venta) - COALESCE(SUM((
                     SELECT SUM(dv.monto_reembolsado) FROM devoluciones_venta dv WHERE dv.venta_id = v.id
                )), 0.0) as ingresos_totales,
                SUM(d.cogs_venta) as cogs,
                (SUM(v.total_venta) - COALESCE(SUM((SELECT SUM(dv.monto_reembolsado) FROM devoluciones_venta dv WHERE dv.venta_id = v.id)), 0.0) - SUM(d.cogs_venta)) as ganancia_neta,
                ((SUM(v.total_venta) - COALESCE(SUM((SELECT SUM(dv.monto_reembolsado) FROM devoluciones_venta dv WHERE dv.venta_id = v.id)), 0.0) - SUM(d.cogs_venta)) / NULLIF(SUM(v.total_venta) - COALESCE(SUM((SELECT SUM(dv.monto_reembolsado) FROM devoluciones_venta dv WHERE dv.venta_id = v.id)), 0.0), 0)) * 100 as margen_porcentaje
            FROM ventas v
            LEFT JOIN DetallesAgrupados d ON v.id = d.venta_id
            WHERE v.estado NOT IN ('ANULADA', 'PENDIENTE', 'CANCELADA_PENDIENTE')
              AND EXTRACT(YEAR FROM v.fecha) = :year
              AND EXTRACT(MONTH FROM v.fecha) = :month
        """;

        MapSqlParameterSource params = new MapSqlParameterSource("year", year);
        params.addValue("month", month);

        List<Map<String, Object>> result = namedJdbcTemplate.queryForList(sql, params);
        if (result.isEmpty() || result.getFirst().get(INGRESOS_TOTALES) == null) {
            return Map.of(
                    INGRESOS_TOTALES, 0.0,
                    "cogs", 0.0,
                    GANANCIA_NETA, 0.0,
                    "margen_ganancia_pct", 0.0
            );
        }

        Map<String, Object> row = result.getFirst();
        return Map.of(
                INGRESOS_TOTALES, row.get(INGRESOS_TOTALES),
                "cogs", row.get("cogs"),
                GANANCIA_NETA, row.get(GANANCIA_NETA),
                "margen_ganancia_pct", row.get("margen_porcentaje") != null ? row.get("margen_porcentaje") : 0.0
        );
    }

    // --- PHASE 5: UNIFIED STATISTICS (Rendimiento Comercial + Flujo de Caja) ---

    /**
     * Calculates unified statistics for a given time period.
     *
     * Supports three granularities:
     *   - Yearly  (year only, month=null, day=null)
     *   - Monthly (year + month, day=null)
     *   - Daily   (year + month + day)
     *
     * Returns a strongly-typed DTO with two sub-objects:
     *   "rendimientoComercial" — Accrual-basis commercial performance.
     *   "flujoDeCaja"          — Cash-basis flow of actual money received/spent.
     *
     * @param year  The target year (required).
     * @param month The target month (1-12, nullable for yearly queries).
     * @param day   The target day (nullable for monthly/yearly queries).
     * @return ReportesEstadisticasDTO containing the two nested result objects.
     */
    public com.centralizesys.model.sales.ReportesEstadisticasDTO getEstadisticas(Integer year, Integer month, Integer day) {
        MapSqlParameterSource params = buildDateParams(year, month, day);
        String dateFilter = buildDateFilter(month, day);

        com.centralizesys.model.sales.ReportesEstadisticasDTO.RendimientoComercial rendimientoComercial = queryRendimientoComercial(dateFilter, params);
        com.centralizesys.model.sales.ReportesEstadisticasDTO.FlujoDeCaja flujoDeCaja = queryFlujoDeCaja(dateFilter, params);

        return new com.centralizesys.model.sales.ReportesEstadisticasDTO(rendimientoComercial, flujoDeCaja);
    }

    /**
     * Queries the accrual-basis commercial performance.
     * Revenue = full sale totals (regardless of how much was actually collected).
     * COGS    = costo_snapshot * cantidad summed from detalles_venta.
     * Debts   = outstanding monto_deuda for PENDIENTE/PARCIAL debts in the period.
     */
    @SuppressWarnings("java:S2077")
    private com.centralizesys.model.sales.ReportesEstadisticasDTO.RendimientoComercial queryRendimientoComercial(String dateFilter, MapSqlParameterSource params) {
        // Revenue and COGS from active sales
        String revenueSql = """
            WITH DetallesAgrupados AS (
                SELECT
                    d.venta_id,
                    COALESCE(SUM(d.costo_snapshot * (d.cantidad - COALESCE(
                        (SELECT SUM(cantidad_devuelta) FROM devoluciones_venta WHERE detalle_venta_id = d.id), 0
                    ))), 0.0) as cogs_venta,
                    COALESCE(SUM(d.cantidad - COALESCE(
                        (SELECT SUM(cantidad_devuelta) FROM devoluciones_venta WHERE detalle_venta_id = d.id), 0
                    )), 0) as qty_vendida
                FROM detalles_venta d
                WHERE d.anulado = false OR d.anulado IS NULL
                GROUP BY d.venta_id
            )
            SELECT
                COALESCE(SUM(v.total_venta), 0.0) - COALESCE(SUM((
                     SELECT SUM(dv.monto_reembolsado) FROM devoluciones_venta dv WHERE dv.venta_id = v.id
                )), 0.0) AS ingresos_ventas,
                COALESCE(SUM(d.cogs_venta), 0.0) AS costo_total_vendido,
                COALESCE(SUM(d.qty_vendida), 0)              AS productos_vendidos
            FROM ventas v
            LEFT JOIN DetallesAgrupados d ON v.id = d.venta_id
            WHERE v.estado NOT IN ('ANULADA', 'PENDIENTE', 'CANCELADA_PENDIENTE')
        """ + dateFilter.replace(FECHA_FIELD, "v.fecha");

        // Products purchased (compras) in the same period
        String purchaseSql = """
            SELECT COALESCE(SUM(dc.cantidad), 0) AS productos_comprados
            FROM compras c
            JOIN detalles_compra dc ON c.id = dc.compra_id
        """ + buildCompraDateFilter(dateFilter);

        // Outstanding debts — note: we report ALL currently pending debts regardless of period
        // to give an accurate current-state picture for the dashboard.
        String debtSql = """
            SELECT (
                COALESCE((SELECT SUM(monto_deuda) FROM deudores WHERE estado IN ('PENDIENTE', 'PARCIAL')), 0.0) +
                COALESCE((SELECT SUM(monto) FROM alertas_cheques WHERE estado = 'PENDIENTE'), 0.0)
            ) AS deudas_pendientes
        """;

        String pendingOrdersSql = """
            SELECT COALESCE(SUM(vp.total_venta), 0.0) AS ventas_pendientes
            FROM ventas vp
            WHERE vp.estado = 'PENDIENTE'
        """ + dateFilter.replace(FECHA_FIELD, FECHA_CREACION);

        // Payments on pending orders in the period
        String pagosPendingOrdersSql = """
            SELECT (
                COALESCE((
                    SELECT SUM(pv.monto) 
                    FROM pagos_venta pv 
                    JOIN ventas vp ON pv.venta_id = vp.id 
                    WHERE vp.estado = 'PENDIENTE' AND pv.anulado = false 
                    """ + dateFilter.replace(FECHA_FIELD, FECHA_CREACION) + """
                ), 0.0) +
                COALESCE((
                    SELECT SUM(ac.monto) 
                    FROM alertas_cheques ac 
                    JOIN ventas vp ON ac.venta_id = vp.id 
                    WHERE vp.estado = 'PENDIENTE' AND ac.estado != 'ANULADA' 
                    """ + dateFilter.replace(FECHA_FIELD, FECHA_CREACION) + """
                ), 0.0)
            ) AS pagos_pendientes
        """;

        List<Map<String, Object>> revenueResult   = namedJdbcTemplate.queryForList(revenueSql, params);
        List<Map<String, Object>> purchaseResult  = namedJdbcTemplate.queryForList(purchaseSql, params);
        Double deudas                             = namedJdbcTemplate.queryForObject(debtSql, new MapSqlParameterSource(), Double.class);
        Double pendingOrders                      = namedJdbcTemplate.queryForObject(pendingOrdersSql, params, Double.class);
        Double pagosPending                       = namedJdbcTemplate.queryForObject(pagosPendingOrdersSql, params, Double.class);

        Map<String, Object> revenueRow  = revenueResult.isEmpty()  ? Map.of() : revenueResult.getFirst();
        Map<String, Object> purchaseRow = purchaseResult.isEmpty() ? Map.of() : purchaseResult.getFirst();

        double pagosPendingVal       = pagosPending != null ? pagosPending : 0.0;
        double ingresosVentas        = safeDouble(revenueRow, "ingresos_ventas") + pagosPendingVal;
        double ventasPendientesVal   = (pendingOrders != null ? pendingOrders : 0.0) - pagosPendingVal;
        double ventasTotalesProyect  = Math.round((ingresosVentas + ventasPendientesVal) * 100.0) / 100.0;

        return new com.centralizesys.model.sales.ReportesEstadisticasDTO.RendimientoComercial(
                ingresosVentas,
                safeDouble(revenueRow, "costo_total_vendido"),
                safeLong(revenueRow, "productos_vendidos"),
                safeLong(purchaseRow, "productos_comprados"),
                deudas != null ? deudas : 0.0,
                ventasPendientesVal,
                ventasTotalesProyect
        );
    }

    /**
     * Queries the cash-basis flow of actual money.
     * <p>
     * Cash IN sources (combined via UNION ALL):
     *   1. pagos_venta   — payments on finalized regular sales.
     *   2. pagos_deuda   — payments on outstanding debts.
     *   3. pagos_venta_pendiente — deposits on pending orders.
     * <p>
     * Cash OUT:
     *   compras.total_compra — actual money spent purchasing inventory.
     * <p>
     * The date filter applies to the PAYMENT timestamp, not the sale date.
     * This ensures cash flow reflects WHEN money actually moved.
     */
    @SuppressWarnings("java:S2077")
    private com.centralizesys.model.sales.ReportesEstadisticasDTO.FlujoDeCaja queryFlujoDeCaja(String dateFilter, MapSqlParameterSource params) {
        // Cash In: payments on finalized sales + debt payments + pending sale deposits
        String cashInSql = """
            SELECT COALESCE(SUM(monto_total), 0.0) AS ingresos_efectivo
            FROM (
                SELECT pv.monto AS monto_total
                FROM pagos_venta pv
                JOIN ventas v ON pv.venta_id = v.id
                JOIN metodos_pago mp ON pv.metodo_pago_id = mp.id
                WHERE v.estado NOT IN ('ANULADA', 'CANCELADA_PENDIENTE') AND pv.anulado = false AND mp.acronimo != 'SALDO'
            """ + buildJoinDateFilter("pv.fecha_pago", dateFilter) + """
                UNION ALL
                SELECT pd.monto AS monto_total
                FROM pagos_deuda pd
                JOIN metodos_pago mp ON pd.metodo_pago_id = mp.id
                WHERE pd.anulado = false AND mp.acronimo != 'SALDO'
            """ + buildJoinDateFilter("pd.fecha_pago", dateFilter) + """
                UNION ALL
                SELECT ac.monto AS monto_total
                FROM alertas_cheques ac
                WHERE ac.estado = 'COBRADO'
            """ + buildJoinDateFilter("ac.fecha_cobro", dateFilter) + """
            ) AS all_cash_in
        """;

        // Cash Out: money paid to suppliers (Inventory)
        String cashOutSql = """
            SELECT COALESCE(SUM(c.total_compra), 0.0) AS egresos_efectivo
            FROM compras c
            WHERE 1=1
        """ + dateFilter.replace(FECHA_FIELD, "c.fecha");

        // Cash Out: gastos varios y retiros
        String gastosVariosSql = """
            SELECT COALESCE(SUM(g.monto), 0.0) AS gastos_varios
            FROM gastos_caja g
            WHERE g.anulado = false
        """ + buildJoinDateFilter("g.fecha_gasto", dateFilter);

        Double cashIn = namedJdbcTemplate.queryForObject(cashInSql, params, Double.class);
        Double cashOut = namedJdbcTemplate.queryForObject(cashOutSql, params, Double.class);
        Double gastosVarios = namedJdbcTemplate.queryForObject(gastosVariosSql, params, Double.class);

        double ingresosEfectivo = cashIn != null ? cashIn : 0.0;
        double egresosEfectivo = cashOut != null ? cashOut : 0.0;
        double gastosVariosEfectivo = gastosVarios != null ? gastosVarios : 0.0;
        double balanceNeto = Math.round((ingresosEfectivo - egresosEfectivo - gastosVariosEfectivo) * 100.0) / 100.0;

        return new com.centralizesys.model.sales.ReportesEstadisticasDTO.FlujoDeCaja(
                ingresosEfectivo,
                egresosEfectivo,
                gastosVariosEfectivo,
                balanceNeto
        );
    }

    // --- DATE FILTER BUILDERS ---

    /**
     * Builds the named parameter map for the given date granularity.
     */
    private MapSqlParameterSource buildDateParams(Integer year, Integer month, Integer day) {
        MapSqlParameterSource params = new MapSqlParameterSource("year", year);
        if (month != null) params.addValue("month", month);
        if (day != null)   params.addValue("day", day);
        return params;
    }

    /**
     * Builds a SQL WHERE clause fragment for ventas/pagos date filtering.
     * Uses a placeholder ":fechaField" that callers replace with the actual column alias.
     */
    private String buildDateFilter(Integer month, Integer day) {
        StringBuilder sb = new StringBuilder(" AND EXTRACT(YEAR FROM :fechaField) = :year");
        if (month != null) sb.append(" AND EXTRACT(MONTH FROM :fechaField) = :month");
        if (day   != null) sb.append(" AND EXTRACT(DAY FROM :fechaField) = :day");
        return sb.toString();
    }

    /**
     * Builds a JOIN-style "AND col = ..." clause by replacing the placeholder with a real column reference.
     * Used inside UNION ALL subqueries where the column name is fixed.
     */
    private String buildJoinDateFilter(String columnRef, String dateFilterTemplate) {
        return dateFilterTemplate.replace(FECHA_FIELD, columnRef);
    }

    /**
     * Builds the date filter for compras, which uses 'fecha' as the date column.
     * Always prepends WHERE 1=1 since the source SQL has no WHERE clause.
     */
    private String buildCompraDateFilter(String dateFilter) {
        return " WHERE 1=1" + dateFilter.replace(FECHA_FIELD, "fecha");
    }

    // --- SAFE NULL COERCION HELPERS ---

    private Double safeDouble(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return 0.0;
        return ((Number) val).doubleValue();
    }

    private Long safeLong(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return 0L;
        return ((Number) val).longValue();
    }
}
