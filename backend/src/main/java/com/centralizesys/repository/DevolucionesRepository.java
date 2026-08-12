package com.centralizesys.repository;

import com.centralizesys.exception.InfrastructureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DevolucionesRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public DevolucionesRepository(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    /**
     * Inserts an immutable return ledger record.
     * Original detalles_venta rows are never mutated.
     *
     * @param ventaId         The parent sale ID.
     * @param detalleVentaId  The specific line item being returned.
     * @param cantidadDevuelta Number of units returned (must be > 0).
     * @param montoReembolsado Monetary value of the return.
     * @param tipoReembolso   'SALDO' or 'EFECTIVO'.
     * @param observaciones   Optional free-text note.
     * @param usuarioId       The ID of the cashier who processed the return.
     * @return The generated ID of the new devolucion_venta row.
     */
    public Long save(Long ventaId, Long detalleVentaId, Long cantidadDevuelta,
                     Double montoReembolsado, String tipoReembolso,
                     String observaciones, Long usuarioId) {
        String sql = """
                    INSERT INTO devoluciones_venta
                        (venta_id, detalle_venta_id, cantidad_devuelta, monto_reembolsado,
                         tipo_reembolso, observaciones, creado_por, actualizado_por)
                    VALUES
                        (:ventaId, :detalleVentaId, :cantidadDevuelta, :montoReembolsado,
                         :tipoReembolso, :observaciones, :usuarioId, :usuarioId)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ventaId",          ventaId)
                .addValue("detalleVentaId",   detalleVentaId)
                .addValue("cantidadDevuelta",  cantidadDevuelta)
                .addValue("montoReembolsado",  montoReembolsado)
                .addValue("tipoReembolso",     tipoReembolso)
                .addValue("observaciones",     observaciones)
                .addValue("usuarioId",         usuarioId);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});

        Number key = keyHolder.getKey();
        if (key == null) throw new InfrastructureException("La base de datos no devolvió el ID de la devolución registrada.");
        return key.longValue();
    }

    /**
     * Sums up all units already returned for a given detalles_venta row.
     * Used to validate that the new return does not exceed the original quantity.
     */
    public Long sumCantidadDevueltaByDetalleId(Long detalleVentaId) {
        String sql = """
                    SELECT COALESCE(SUM(cantidad_devuelta), 0)
                    FROM devoluciones_venta
                    WHERE detalle_venta_id = :detalleVentaId
                """;
        Long result = namedJdbcTemplate.queryForObject(sql,
                new MapSqlParameterSource("detalleVentaId", detalleVentaId), Long.class);
        return result != null ? result : 0L;
    }
}
