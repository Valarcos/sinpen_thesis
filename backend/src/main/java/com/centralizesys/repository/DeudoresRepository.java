package com.centralizesys.repository;

import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeuda;
import com.centralizesys.model.enums.DebtStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DeudoresRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private static final String MONTO = "monto";
    private static final String OBSERVACIONES = "observaciones";
    private static final String DEUDA_ID = "deudaId";
    private static final String NUEVO_ESTADO = "nuevoEstado";

    public DeudoresRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private final RowMapper<DeudaResponse> rowMapper = (rs, rowNum) -> new DeudaResponse(
            rs.getLong("id"),
            rs.getLong("venta_id"),
            rs.getString("cliente_nombre"),
            rs.getObject("cliente_id") != null ? rs.getLong("cliente_id") : null,
            Math.round(rs.getDouble("monto_deuda") * 100.0) / 100.0,
            rs.getObject("fecha_deuda", LocalDateTime.class),
            rs.getString("estado"),
            Math.round(rs.getDouble("monto_original") * 100.0) / 100.0,
            rs.getObject("fecha_ultimo_pago", LocalDateTime.class));

    public void save(Long ventaId, String clienteNombre, Long clienteId, Double montoDeuda) {
        // Use native ISO format for SQLite compatibility

        String sql = """
                    INSERT INTO deudores (venta_id, cliente_nombre, cliente_id, monto_deuda, fecha_deuda, estado)
                    VALUES (:ventaId, :clienteNombre, :clienteId, :montoDeuda, :fechaDeuda, :estado)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ventaId", ventaId)
                .addValue("clienteNombre", clienteNombre)
                .addValue("clienteId", clienteId)
                .addValue("montoDeuda", montoDeuda)
                .addValue("fechaDeuda", LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")))
                .addValue("estado", DebtStatus.PENDIENTE.name());
        namedJdbcTemplate.update(sql, params);
    }

    public List<DeudaResponse> findAll() {
        String sql = """
                    SELECT d.id, d.venta_id, COALESCE(c.nombre, d.cliente_nombre) AS cliente_nombre, d.monto_deuda, d.estado, d.fecha_deuda, d.fecha_pago, d.cliente_id, v.total_venta as monto_original, d.fecha_pago as fecha_ultimo_pago
                    FROM deudores d
                    JOIN ventas v ON d.venta_id = v.id
                    LEFT JOIN clientes c ON d.cliente_id = c.id
                    ORDER BY d.id DESC
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<DeudaResponse> findPage(int limit, int offset) {
        String sql = """
                    SELECT d.id, d.venta_id, COALESCE(c.nombre, d.cliente_nombre) AS cliente_nombre, d.monto_deuda, d.estado, d.fecha_deuda, d.fecha_pago, d.cliente_id, v.total_venta as monto_original, d.fecha_pago as fecha_ultimo_pago
                    FROM deudores d
                    JOIN ventas v ON d.venta_id = v.id
                    LEFT JOIN clientes c ON d.cliente_id = c.id
                    ORDER BY d.id DESC
                    LIMIT :limit OFFSET :offset
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
        return namedJdbcTemplate.query(sql, params, rowMapper);
    }

    public long countAll() {
        String sql = "SELECT COUNT(*) FROM deudores";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }

    // --- OPTIONAL USAGE EXPLANATION ---
    // Returns Optional<DeudaResponse> because a query by ID might result in 0 rows.
    // This forces the Service layer to explicitly handle the "Not Found" scenario.
    public Optional<DeudaResponse> findById(Long id) {
        String sql = """
                    SELECT d.id, d.venta_id, COALESCE(c.nombre, d.cliente_nombre) AS cliente_nombre, d.monto_deuda, d.estado, d.fecha_deuda, d.fecha_pago, d.cliente_id, v.total_venta as monto_original, d.fecha_pago as fecha_ultimo_pago
                    FROM deudores d
                    JOIN ventas v ON d.venta_id = v.id
                    LEFT JOIN clientes c ON d.cliente_id = c.id
                    WHERE d.id = :id
                """;
        List<DeudaResponse> list = namedJdbcTemplate.query(sql, new MapSqlParameterSource("id", id), rowMapper);
        return list.stream().findFirst();
    }

    public Optional<DeudaResponse> findByVentaId(Long ventaId) {
        String sql = """
                    SELECT d.id, d.venta_id, COALESCE(c.nombre, d.cliente_nombre) AS cliente_nombre, d.monto_deuda, d.estado, d.fecha_deuda, d.fecha_pago, d.cliente_id, v.total_venta as monto_original, d.fecha_pago as fecha_ultimo_pago
                    FROM deudores d
                    JOIN ventas v ON d.venta_id = v.id
                    LEFT JOIN clientes c ON d.cliente_id = c.id
                    WHERE d.venta_id = :ventaId
                """;
        List<DeudaResponse> list = namedJdbcTemplate.query(sql, new MapSqlParameterSource("ventaId", ventaId), rowMapper);
        return list.stream().findFirst();
    }

    public void updateMontoAndEstado(Long id, Double nuevoMonto, String nuevoEstado) {
        // We update the Payment Date to NOW whenever the state changes/payment is made
        String sql = "UPDATE deudores SET monto_deuda = :nuevoMonto, estado = :nuevoEstado WHERE id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("nuevoMonto", nuevoMonto)
                .addValue(NUEVO_ESTADO, nuevoEstado)
                .addValue("id", id);
        namedJdbcTemplate.update(sql, params);
    }

    /**
     * ARCHITECTURAL NOTE: Atomic Ledger Transition
     * --------------------------------------------
     * This method calculates the new debt balance and state (PENDIENTE/PARCIAL/PAGADO)
     * DIRECTLY in the database using an atomic SQL UPDATE, rather than in Java.
     *
     * WHY THIS IS HERE:
     * If multiple cashiers attempt to pay the same debt simultaneously, calculating
     * the balance in Java (Read-Modify-Write) causes race conditions and double-payments.
     * By doing it here, PostgreSQL's transaction engine guarantees absolute ACID isolation.
     *
     * MAINTENANCE WARNING:
     * - Do NOT move this calculation back to the Java Service layer without implementing
     *   strict distributed locking.
     * - The `<= 0.01` floating point epsilon bounds are critically required to prevent
     *   PostgreSQL 'deudores_estado_check' constraint violations caused by numeric drift.
     */
    public int deductDeudaAtomic(Long id, Double appliedAmount, Double montoOriginal) {
        String sql = """
            UPDATE deudores 
            SET monto_deuda = CASE 
                                 WHEN (monto_deuda - :appliedAmount) <= 0.01 THEN 0
                                 ELSE (monto_deuda - :appliedAmount)
                              END,
                estado = CASE 
                            WHEN (monto_deuda - :appliedAmount) <= 0.01 THEN 'PAGADO'
                            WHEN (monto_deuda - :appliedAmount) >= (:montoOriginal - 0.01) THEN 'PENDIENTE'
                            ELSE 'PARCIAL'
                         END,
                fecha_pago = CURRENT_TIMESTAMP
            WHERE id = :id AND monto_deuda >= (:appliedAmount - 0.01)
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("appliedAmount", appliedAmount)
                .addValue("montoOriginal", montoOriginal);
        return namedJdbcTemplate.update(sql, params);
    }

    /**
     * ARCHITECTURAL NOTE: Atomic Ledger Transition (Restoration)
     * ----------------------------------------------------------
     * Counterpart to deductDeudaAtomic. Used when a payment is voided or a cheque bounces,
     * requiring the debt to be restored safely without race conditions.
     *
     * MAINTENANCE WARNING:
     * - The `>= (:montoOriginal - 0.01)` epsilon bounds are required to prevent float drift
     *   from exceeding the original debt total, which would violate DB constraints.
     */
    public int addDeudaAtomic(Long id, Double addedAmount, Double montoOriginal) {
        String sql = """
            UPDATE deudores 
            SET monto_deuda = CASE 
                                 WHEN (monto_deuda + :addedAmount) >= (:montoOriginal - 0.01) THEN :montoOriginal
                                 ELSE (monto_deuda + :addedAmount)
                              END,
                estado = CASE 
                            WHEN (monto_deuda + :addedAmount) <= 0.01 THEN 'PAGADO'
                            WHEN (monto_deuda + :addedAmount) >= (:montoOriginal - 0.01) THEN 'PENDIENTE'
                            ELSE 'PARCIAL'
                         END
            WHERE id = :id
        """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("addedAmount", addedAmount)
                .addValue("montoOriginal", montoOriginal);
        return namedJdbcTemplate.update(sql, params);
    }

    /**
     * Check if there are any active (non-PAGADO, non-ANULADA) debts OR any open pending sales.
     * Used by the frontend dashboard reminder badge, which covers both the deudores
     * and ventas_pendientes sections of the CobrosYPedidos page.
     */
    public boolean hasActiveDebts() {
        String sql = """
                    SELECT (
                        EXISTS (SELECT 1 FROM deudores WHERE estado IN ('PENDIENTE', 'PARCIAL'))
                        OR
                        EXISTS (SELECT 1 FROM ventas WHERE estado = 'PENDIENTE')
                    )
                """;
        Boolean result = jdbcTemplate.queryForObject(sql, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public List<DeudaResponse> findExpiredDebts(int days) {
        // SQLite syntax: date('now', '-X days')
        String sql = """
                    SELECT d.id, d.venta_id, COALESCE(c.nombre, d.cliente_nombre) AS cliente_nombre, d.monto_deuda, d.estado, d.fecha_deuda, d.fecha_pago, d.cliente_id, v.total_venta as monto_original, d.fecha_pago as fecha_ultimo_pago
                    FROM deudores d
                    JOIN ventas v ON d.venta_id = v.id
                    LEFT JOIN clientes c ON d.cliente_id = c.id
                    WHERE d.estado IN ('PENDIENTE', 'PARCIAL')
                    AND d.fecha_deuda <= :thresholdDate
                    ORDER BY d.fecha_deuda ASC
                    LIMIT 100
                """;

        return namedJdbcTemplate.query(sql,
                new MapSqlParameterSource("thresholdDate", LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).minusDays(days)),
                rowMapper);
    }

    public List<PagoDeuda> getPagosByDeudaId(Long deudaId) {
        String sql = """
                    SELECT p.*, m.descripcion as metodo_pago_nombre, u.nombre as usuario_nombre
                    FROM pagos_deuda p
                    JOIN metodos_pago m ON p.metodo_pago_id = m.id
                    LEFT JOIN usuarios u ON p.usuario_id = u.id
                    WHERE p.deuda_id = :deudaId
                    ORDER BY p.fecha_pago DESC, p.id DESC
                    LIMIT 100
                """;

        return namedJdbcTemplate.query(sql,
                new MapSqlParameterSource(DEUDA_ID, deudaId),
                (rs, rowNum) -> new PagoDeuda(
                        rs.getLong("id"),
                        rs.getLong("deuda_id"),
                        rs.getLong("metodo_pago_id"),
                        rs.getDouble(MONTO),
                        rs.getObject("fecha_pago", LocalDateTime.class),
                        rs.getString(OBSERVACIONES),
                        rs.getLong("usuario_id"),
                        rs.getBoolean("anulado"),
                        rs.getString("metodo_pago_nombre"),
                        rs.getString("usuario_nombre")));
    }

    public Optional<PagoDeuda> findPagoById(Long pagoId) {
        String sql = """
                    SELECT p.*, m.descripcion as metodo_pago_nombre, u.nombre as usuario_nombre
                    FROM pagos_deuda p
                    JOIN metodos_pago m ON p.metodo_pago_id = m.id
                    LEFT JOIN usuarios u ON p.usuario_id = u.id
                    WHERE p.id = :pagoId
                """;

        List<PagoDeuda> list = namedJdbcTemplate.query(sql,
                new MapSqlParameterSource("pagoId", pagoId),
                (rs, rowNum) -> new PagoDeuda(
                        rs.getLong("id"),
                        rs.getLong("deuda_id"),
                        rs.getLong("metodo_pago_id"),
                        rs.getDouble(MONTO),
                        rs.getObject("fecha_pago", LocalDateTime.class),
                        rs.getString(OBSERVACIONES),
                        rs.getLong("usuario_id"),
                        rs.getBoolean("anulado"),
                        rs.getString("metodo_pago_nombre"),
                        rs.getString("usuario_nombre")));
        return list.stream().findFirst();
    }

    public int updatePagoAnulado(Long pagoId) {
        String sql = "UPDATE pagos_deuda SET anulado = true WHERE id = :pagoId AND (anulado = false OR anulado IS NULL)";
        return namedJdbcTemplate.update(sql, new MapSqlParameterSource("pagoId", pagoId));
    }

    public void insertarPagoDeuda(Long deudaId, Long metodoPagoId, Double monto, String observaciones, Long usuarioId) {
        String sql = """
                    INSERT INTO pagos_deuda (deuda_id, metodo_pago_id, monto, fecha_pago, observaciones, usuario_id)
                    VALUES (:deudaId, :metodoPagoId, :monto, CURRENT_TIMESTAMP, :observaciones, :usuarioId)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(DEUDA_ID, deudaId)
                .addValue("metodoPagoId", metodoPagoId)
                .addValue(MONTO, monto)
                // fecha_pago handled by sql
                .addValue(OBSERVACIONES, observaciones)
                .addValue("usuarioId", usuarioId);

        namedJdbcTemplate.update(sql, params);
    }

    public Long insertarPagoDeudaReturningId(Long deudaId, Long metodoPagoId, Double monto, String observaciones, Long usuarioId) {
        String sql = """
                    INSERT INTO pagos_deuda (deuda_id, metodo_pago_id, monto, fecha_pago, observaciones, usuario_id)
                    VALUES (:deudaId, :metodoPagoId, :monto, CURRENT_TIMESTAMP, :observaciones, :usuarioId)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(DEUDA_ID, deudaId)
                .addValue("metodoPagoId", metodoPagoId)
                .addValue(MONTO, monto)
                .addValue(OBSERVACIONES, observaciones)
                .addValue("usuarioId", usuarioId);

        org.springframework.jdbc.support.KeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        namedJdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});

        if (keyHolder.getKey() != null) {
            return keyHolder.getKey().longValue();
        }
        return null;
    }
}