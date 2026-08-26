package com.centralizesys.repository;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.InfrastructureException;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.PagoVenta;
import com.centralizesys.model.sales.Venta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class VentaRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    private static final String VENTA_ID_COLUMN = "venta_id";
    private static final String VENTA_ID_PARAM = "ventaId";
    private static final String PARAM_ESTADO = "estado";
    private static final String PARAM_START_DATE = "startDate";
    private static final String PARAM_END_DATE = "endDate";
    private static final String FIELD_ANULADO = "anulado";
    private static final String MONTO = "monto";
    private static final String USUARIO_ID = "usuarioId";
    private static final String METODO_PAGO_ID = "metodoPagoId";

    public VentaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    // --- MAPPERS ---
    private final RowMapper<Venta> ventaMapper = (rs, rowNum) -> {
        Long usuarioIdVal = rs.getLong("usuario_id");
        Long usuarioId = rs.wasNull() ? null : usuarioIdVal;
        Venta v = new Venta();
        v.setId(rs.getLong("id"));
        v.setFecha(rs.getObject("fecha", LocalDateTime.class));
        v.setFechaCreacion(rs.getObject("fecha_creacion", LocalDateTime.class));
        v.setClienteNombre(rs.getString("cliente_nombre"));
        // clienteId is set below with null-safe helper
        v.setTotalVenta(rs.getDouble("total_venta"));
        v.setDescuentoGlobal(rs.getDouble("descuento_global"));
        v.setRecargoGlobal(getNullableDouble(rs, "recargo_global"));
        v.setSaldoGenerado(getNullableDouble(rs, "saldo_generado"));
        v.setTipoVenta(rs.getString("tipo_venta"));
        v.setUsuarioId(usuarioId);
        v.setEstado(rs.getString(PARAM_ESTADO));
        v.setCostoTotal(getNullableDouble(rs, "costo_total"));
        v.setCantidadProductos(getNullableLong(rs, "cantidad_productos"));
        v.setVersion(rs.getInt("version"));

        v.setClienteId(getNullableLong(rs, "cliente_id"));
        return v;
    };


    private Double getNullableDouble(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        double val = rs.getDouble(columnName);
        return rs.wasNull() ? null : val;
    }

    private Long getNullableLong(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long val = rs.getLong(columnName);
        return rs.wasNull() ? null : val;
    }

    private final RowMapper<DetalleVenta> detalleMapper = (rs, rowNum) -> {
        boolean anulado = rs.getBoolean(FIELD_ANULADO);
        if (rs.wasNull()) anulado = false;

        return new DetalleVenta(
                rs.getLong("id"),
                rs.getLong(VENTA_ID_COLUMN),
                rs.getLong("producto_id"),
                rs.getString("codigo_snapshot"),
                rs.getString("descripcion_snapshot"),
                rs.getDouble("costo_snapshot"),
                rs.getLong("cantidad"),
                rs.getDouble("precio_lista"),
                rs.getDouble("descuento_valor"),
                rs.getString("razon_descuento"),
                rs.getDouble("precio_unitario"),
                rs.getDouble("subtotal"),
                anulado,
                0L);
    };

    private final RowMapper<PagoVenta> pagoMapper = (rs, rowNum) -> {
        boolean anulado = rs.getBoolean(FIELD_ANULADO);
        if (rs.wasNull()) anulado = false;

        Long usuarioIdVal = rs.getLong("usuario_id");
        if (rs.wasNull()) usuarioIdVal = null;

        return new PagoVenta(
                rs.getLong("id"),
                rs.getLong(VENTA_ID_COLUMN),
                rs.getLong("metodo_pago_id"),
                rs.getDouble(MONTO),
                rs.getObject("fecha_pago", LocalDateTime.class),
                anulado,
                usuarioIdVal);
    };

    // --- WRITE OPERATIONS ---

    public Long saveVenta(Venta venta) {
        String sql = """
                    INSERT INTO ventas (fecha, fecha_creacion, cliente_nombre, cliente_id, total_venta, descuento_global, recargo_global, saldo_generado, tipo_venta, estado, usuario_id)
                    VALUES (:fecha, COALESCE(:fechaCreacion, :fecha), :clienteNombre, :clienteId, :totalVenta, :descuentoGlobal, :recargoGlobal, :saldoGenerado, :tipoVenta, COALESCE(:estado, 'ACTIVA'), :usuarioId)
                """;

        SqlParameterSource params = new BeanPropertySqlParameterSource(venta);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new InfrastructureException("La base de datos no devolvió el ID de la venta guardada.");
        }
        return key.longValue();
    }

    public void saveDetalles(List<DetalleVenta> detalles) {
        String sql = """
                    INSERT INTO detalles_venta
                    (venta_id, producto_id, codigo_snapshot, descripcion_snapshot, costo_snapshot, cantidad,
                     precio_lista, descuento_valor, razon_descuento, precio_unitario, subtotal, anulado)
                    VALUES (:ventaId,
                            :productoId,
                            :codigoSnapshot,
                            :descripcionSnapshot,
                            :costoSnapshot,
                            :cantidad,
                            :precioLista,
                            :descuentoValor,
                            :razonDescuento,
                            :precioUnitario,
                            :subtotal,
                            :anulado)
                """;

        List<MapSqlParameterSource> batchParams = detalles.stream()
                .map(d -> new MapSqlParameterSource()
                        .addValue(VENTA_ID_PARAM, d.getVentaId())
                        .addValue("productoId", d.getProductoId())
                        .addValue("codigoSnapshot", d.getCodigoSnapshot())
                        .addValue("descripcionSnapshot", d.getDescripcionSnapshot())
                        .addValue("costoSnapshot", d.getCostoSnapshot())
                        .addValue("cantidad", d.getCantidad())
                        .addValue("precioLista", d.getPrecioLista())
                        .addValue("descuentoValor", d.getDescuentoValor())
                        .addValue("razonDescuento", d.getRazonDescuento())
                        .addValue("precioUnitario", d.getPrecioUnitario())
                        .addValue("subtotal", d.getSubtotal())
                        .addValue(FIELD_ANULADO, Boolean.TRUE.equals(d.getAnulado())))
                .toList();

        namedJdbcTemplate.batchUpdate(sql, batchParams.toArray(new MapSqlParameterSource[0]));
    }

    public void savePagos(List<PagoVenta> pagos) {
        if (pagos.isEmpty()) return;
        String sql = """
                INSERT INTO pagos_venta (venta_id, metodo_pago_id, monto, fecha_pago, anulado, usuario_id) 
                VALUES (:ventaId, :metodoPagoId, :monto, COALESCE(:fechaPago, CURRENT_TIMESTAMP), COALESCE(:anulado, false), :usuarioId)
                """;
        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(pagos);
        namedJdbcTemplate.batchUpdate(sql, batch);
    }

    public void savePagoUnico(Long ventaId, Long metodoPagoId, Double monto, Long usuarioId) {
        String sql = """
                INSERT INTO pagos_venta (venta_id, metodo_pago_id, monto, fecha_pago, anulado, usuario_id)
                VALUES (:ventaId, :metodoPagoId, :monto, CURRENT_TIMESTAMP, false, :usuarioId)
                """;
        namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue(VENTA_ID_PARAM, ventaId)
                .addValue(METODO_PAGO_ID, metodoPagoId)
                .addValue(MONTO, monto)
                .addValue(USUARIO_ID, usuarioId));
    }

    public Long savePagoUnicoReturningId(Long ventaId, Long metodoPagoId, Double monto, Long usuarioId) {
        String sql = """
                INSERT INTO pagos_venta (venta_id, metodo_pago_id, monto, fecha_pago, anulado, usuario_id)
                VALUES (:ventaId, :metodoPagoId, :monto, CURRENT_TIMESTAMP, false, :usuarioId)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(VENTA_ID_PARAM, ventaId)
                .addValue(METODO_PAGO_ID, metodoPagoId)
                .addValue(MONTO, monto)
                .addValue(USUARIO_ID, usuarioId);

        namedJdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});

        if (keyHolder.getKey() != null) {
            return keyHolder.getKey().longValue();
        }
        return null;
    }

    public void anularPagoVentaById(Long pagoVentaId) {
        String sql = "UPDATE pagos_venta SET anulado = true WHERE id = :pagoVentaId";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource("pagoVentaId", pagoVentaId));
    }

    // --- READ OPERATIONS ---

    public List<Long> findPendientesOlderThan(java.time.LocalDateTime cutoffDate) {
        String sql = "SELECT id FROM ventas WHERE estado = 'PENDIENTE' AND fecha < :cutoffDate LIMIT 1000";
        return namedJdbcTemplate.queryForList(sql, new MapSqlParameterSource("cutoffDate", cutoffDate), Long.class);
    }

    public List<Venta> findVentasByClienteId(Long clienteId) {
        String sql = """
                SELECT v.id, v.fecha, v.fecha_creacion, COALESCE(c.nombre, v.cliente_nombre) AS cliente_nombre, v.cliente_id, v.total_venta, v.descuento_global, v.recargo_global, v.saldo_generado, v.tipo_venta, v.usuario_id, v.estado, v.version,
                       (SELECT COALESCE(SUM(costo_snapshot * cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as costo_total,
                       (SELECT COALESCE(SUM(cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as cantidad_productos
                FROM ventas v 
                LEFT JOIN clientes c ON v.cliente_id = c.id
                WHERE v.cliente_id = :clienteId
                  AND v.estado NOT IN ('PENDIENTE', 'CANCELADA_PENDIENTE')
                ORDER BY v.fecha DESC, v.id DESC
                LIMIT 100
                """;
        return namedJdbcTemplate.query(sql, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("clienteId", clienteId), ventaMapper);
    }

    public List<Venta> findAll() {
        String sql = """
                SELECT v.id, v.fecha, v.fecha_creacion, COALESCE(c.nombre, v.cliente_nombre) AS cliente_nombre, v.cliente_id, v.total_venta, v.descuento_global, v.recargo_global, v.saldo_generado, v.tipo_venta, v.usuario_id, v.estado, v.version,
                       (SELECT COALESCE(SUM(costo_snapshot * cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as costo_total,
                       (SELECT COALESCE(SUM(cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as cantidad_productos
                FROM ventas v 
                LEFT JOIN clientes c ON v.cliente_id = c.id
                WHERE v.estado NOT IN ('PENDIENTE', 'CANCELADA_PENDIENTE')
                ORDER BY v.fecha DESC, v.id DESC
                """;
        return jdbcTemplate.query(sql, ventaMapper);
    }



    public Optional<Venta> findById(Long id) {
        String sql = """
                SELECT v.id, v.fecha, v.fecha_creacion, COALESCE(c.nombre, v.cliente_nombre) AS cliente_nombre, v.cliente_id, v.total_venta, v.descuento_global, v.recargo_global, v.saldo_generado, v.tipo_venta, v.usuario_id, v.estado, v.version,
                       (SELECT COALESCE(SUM(costo_snapshot * cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as costo_total,
                       (SELECT COALESCE(SUM(cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as cantidad_productos
                FROM ventas v 
                LEFT JOIN clientes c ON v.cliente_id = c.id
                WHERE v.id = :id
                """;
        List<Venta> list = namedJdbcTemplate.query(sql, new MapSqlParameterSource("id", id), ventaMapper);
        return list.stream().findFirst();
    }

    public String findVendedorNombre(Long usuarioId) {
        if (usuarioId == null || usuarioId == 0L) return "Sistema";
        String sql = "SELECT nombre FROM usuarios WHERE id = :id";
        List<String> result = namedJdbcTemplate.queryForList(sql, new MapSqlParameterSource("id", usuarioId), String.class);
        return result.isEmpty() ? "Sistema" : result.getFirst();
    }

    public List<DetalleVenta> findDetallesByVentaId(Long ventaId) {
        String sql = "SELECT * FROM detalles_venta WHERE venta_id = :ventaId AND (anulado = false OR anulado IS NULL)";
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource(VENTA_ID_PARAM, ventaId), detalleMapper);
    }

    public List<PagoVenta> findPagosByVentaId(Long ventaId) {
        String sql = "SELECT * FROM pagos_venta WHERE venta_id = :ventaId";
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource(VENTA_ID_PARAM, ventaId), pagoMapper);
    }

    public List<PagoVenta> findPagosActivosByVentaId(Long ventaId) {
        String sql = "SELECT * FROM pagos_venta WHERE venta_id = :ventaId AND (anulado = false OR anulado IS NULL)";
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource(VENTA_ID_PARAM, ventaId), pagoMapper);
    }

    public Double sumPagosActivosByVentaId(Long ventaId) {
        String sql = """
                    SELECT COALESCE(SUM(monto), 0.0)
                    FROM pagos_venta
                    WHERE venta_id = :ventaId
                      AND (anulado = false OR anulado IS NULL)
                """;
        return namedJdbcTemplate.queryForObject(sql, new MapSqlParameterSource(VENTA_ID_PARAM, ventaId), Double.class);
    }

    public Double getMontoPagoActivo(Long pagoId, Long ventaId) {
        String sql = "SELECT monto FROM pagos_venta WHERE id = :pagoId AND venta_id = :ventaId AND (anulado = false OR anulado IS NULL)";
        try {
            return namedJdbcTemplate.queryForObject(sql, new MapSqlParameterSource()
                    .addValue("pagoId", pagoId)
                    .addValue(VENTA_ID_PARAM, ventaId), Double.class);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new BusinessRuleException("El pago no existe, no pertenece a este pedido o ya está anulado.");
        }
    }

    public List<Venta> findVentasByFechaBetween(java.time.LocalDateTime startDate, LocalDateTime endDate, Long searchId, int limit, int offset) {
        String sql = """
                    SELECT v.id, v.fecha, v.fecha_creacion, COALESCE(c.nombre, v.cliente_nombre) AS cliente_nombre, v.cliente_id, v.total_venta, v.descuento_global, v.recargo_global, v.saldo_generado, v.tipo_venta, v.usuario_id, v.estado, v.version,
                           (SELECT COALESCE(SUM(costo_snapshot * cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as costo_total,
                           (SELECT COALESCE(SUM(cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as cantidad_productos
                    FROM ventas v
                    LEFT JOIN clientes c ON v.cliente_id = c.id
                    WHERE v.estado NOT IN ('PENDIENTE', 'CANCELADA_PENDIENTE')
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);

        if (searchId != null) {
            sql += " AND CAST(v.id AS TEXT) LIKE CAST(:searchId AS TEXT) || '%' ";
            params.addValue("searchId", searchId);
        } else {
            sql += " AND fecha BETWEEN :startDate AND :endDate ";
            params.addValue(PARAM_START_DATE, startDate)
                    .addValue(PARAM_END_DATE, endDate);
        }

        sql += " ORDER BY v.fecha DESC, v.id DESC LIMIT :limit OFFSET :offset";

        return namedJdbcTemplate.query(sql, params, ventaMapper);
    }

    public List<Venta> findVentasPendientesByFechaBetween(java.time.LocalDateTime startDate, LocalDateTime endDate, int limit, int offset) {
        String sql = """
                    SELECT v.id, v.fecha, v.fecha_creacion, COALESCE(c.nombre, v.cliente_nombre) AS cliente_nombre, v.cliente_id, v.total_venta, v.descuento_global, v.recargo_global, v.saldo_generado, v.tipo_venta, v.usuario_id, v.estado, v.version,
                           (SELECT COALESCE(SUM(costo_snapshot * cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as costo_total,
                           (SELECT COALESCE(SUM(cantidad), 0) FROM detalles_venta WHERE venta_id = v.id AND (anulado = false OR anulado IS NULL)) as cantidad_productos
                    FROM ventas v
                    LEFT JOIN clientes c ON v.cliente_id = c.id
                    WHERE fecha_creacion BETWEEN :startDate AND :endDate
                      AND v.estado = 'PENDIENTE'
                    ORDER BY v.fecha DESC, v.id DESC
                    LIMIT :limit OFFSET :offset
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_START_DATE, startDate)
                .addValue(PARAM_END_DATE, endDate)
                .addValue("limit", limit)
                .addValue("offset", offset);

        return namedJdbcTemplate.query(sql, params, ventaMapper);
    }

    public long countVentasByFechaBetween(java.time.LocalDateTime startDate, LocalDateTime endDate, Long searchId) {
        String sql = "SELECT COUNT(*) FROM ventas WHERE estado NOT IN ('PENDIENTE', 'CANCELADA_PENDIENTE')";
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (searchId != null) {
            sql += " AND CAST(id AS TEXT) LIKE CAST(:searchId AS TEXT) || '%'";
            params.addValue("searchId", searchId);
        } else {
            sql += " AND fecha BETWEEN :startDate AND :endDate";
            params.addValue(PARAM_START_DATE, startDate)
                    .addValue(PARAM_END_DATE, endDate);
        }
        return Optional.ofNullable(namedJdbcTemplate.queryForObject(sql, params, Long.class)).orElse(0L);
    }

    public long countVentasPendientesByFechaBetween(java.time.LocalDateTime startDate, LocalDateTime endDate) {
        String sql = "SELECT COUNT(*) FROM ventas WHERE fecha_creacion BETWEEN :startDate AND :endDate AND estado = 'PENDIENTE'";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_START_DATE, startDate)
                .addValue(PARAM_END_DATE, endDate);
        return Optional.ofNullable(namedJdbcTemplate.queryForObject(sql, params, Long.class)).orElse(0L);
    }

    public List<String> findDistinctClientNames() {
        String sql = "SELECT DISTINCT COALESCE(c.nombre, v.cliente_nombre) AS cliente_nombre FROM ventas v LEFT JOIN clientes c ON v.cliente_id = c.id WHERE COALESCE(c.nombre, v.cliente_nombre) IS NOT NULL AND COALESCE(c.nombre, v.cliente_nombre) != '' AND v.estado NOT IN ('PENDIENTE', 'CANCELADA_PENDIENTE') ORDER BY 1";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    // --- UPDATE OPERATIONS ---

    public void updateEstado(Long ventaId, String estado) {
        String sql = "UPDATE ventas SET estado = :estado WHERE id = :id";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue(PARAM_ESTADO, estado)
                .addValue("id", ventaId));
    }

    public boolean lockVentaForUpdate(Long ventaId, String estadoEsperado) {
        String sql = "SELECT 1 FROM ventas WHERE id = :id AND estado = :estadoEsperado FOR UPDATE";
        try {
            List<Integer> result = namedJdbcTemplate.queryForList(sql, new MapSqlParameterSource()
                    .addValue("id", ventaId)
                    .addValue("estadoEsperado", estadoEsperado), Integer.class);
            return !result.isEmpty();
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return false;
        }
    }

    public boolean lockVentaForReturn(Long ventaId) {
        String sql = "SELECT 1 FROM ventas WHERE id = :id AND estado IN ('ACTIVA', 'DEVUELTA_PARCIAL') FOR UPDATE";
        try {
            List<Integer> result = namedJdbcTemplate.queryForList(sql, new MapSqlParameterSource("id", ventaId), Integer.class);
            return !result.isEmpty();
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return false;
        }
    }

    /**
     * ARCHITECTURAL NOTE: Atomic State Transition (Optimistic Locking)
     * ----------------------------------------------------------------
     * Used exclusively for Sales Returns. Updates the state only if the sale
     * is currently ACTIVA or DEVUELTA_PARCIAL.
     *
     * WHY THIS IS HERE:
     * Prevents a race condition where Cashier A attempts a return while Cashier B
     * is simultaneously voiding the sale. The database acts as the single source of truth.
     * If 0 rows are affected, it means the state changed concurrently, and the Service
     * layer must throw a BusinessRuleException.
     */
    public int updateEstadoSafeReturn(Long ventaId, String nuevoEstado) {
        String sql = "UPDATE ventas SET estado = :nuevoEstado WHERE id = :id AND estado IN ('ACTIVA', 'DEVUELTA_PARCIAL')";
        return namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("nuevoEstado", nuevoEstado)
                .addValue("id", ventaId));
    }

    /**
     * ARCHITECTURAL NOTE: Atomic State Transition (Optimistic Locking)
     * ----------------------------------------------------------------
     * Strict optimistic locking. Only updates if the state matches EXACTLY what Java read.
     * Use this whenever transitioning states (e.g. ACTIVA -> ANULADA) to prevent concurrent
     * double-voids or illegal transitions. If 0 rows are affected, throw an exception.
     */
    public int updateEstadoAtomic(Long ventaId, String nuevoEstado, String estadoEsperado) {
        String sql = "UPDATE ventas SET estado = :nuevoEstado WHERE id = :id AND estado = :estadoEsperado";
        return namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("nuevoEstado", nuevoEstado)
                .addValue("id", ventaId)
                .addValue("estadoEsperado", estadoEsperado));
    }

    public void updateFechaAndEstado(Long ventaId, LocalDateTime nuevaFecha, String estado) {
        String sql = "UPDATE ventas SET fecha = :fecha, estado = :estado WHERE id = :id";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("fecha", nuevaFecha)
                .addValue(PARAM_ESTADO, estado)
                .addValue("id", ventaId));
    }

    public int updateFechaAndEstadoAtomic(Long ventaId, LocalDateTime nuevaFecha, String estado, String estadoEsperado) {
        String sql = "UPDATE ventas SET fecha = :fecha, estado = :estado WHERE id = :id AND estado = :estadoEsperado";
        return namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("fecha", nuevaFecha)
                .addValue(PARAM_ESTADO, estado)
                .addValue("id", ventaId)
                .addValue("estadoEsperado", estadoEsperado));
    }

    public void marcarDetallesComoAnulados(Long ventaId) {
        String sql = "UPDATE detalles_venta SET anulado = true WHERE venta_id = :id";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource("id", ventaId));
    }

    public void updatePendingSaleHeader(Long id, Double nuevoTotalVenta, Double nuevoDescuentoGlobal, Double nuevoRecargoGlobal, Double saldoGenerado, Long clienteId, String clienteNombre, String tipoVenta) {
        String sql = """
                    UPDATE ventas
                    SET total_venta = :nuevoTotalVenta, 
                        descuento_global = :nuevoDescuentoGlobal, 
                        recargo_global = :nuevoRecargoGlobal,
                        saldo_generado = :saldoGenerado,
                        cliente_id = :clienteId,
                        cliente_nombre = :clienteNombre,
                        tipo_venta = :tipoVenta,
                        version = version + 1
                    WHERE id = :id AND estado = 'PENDIENTE'
                """;
        int rows = namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("nuevoTotalVenta", nuevoTotalVenta)
                .addValue("nuevoDescuentoGlobal", nuevoDescuentoGlobal)
                .addValue("nuevoRecargoGlobal", nuevoRecargoGlobal)
                .addValue("saldoGenerado", saldoGenerado != null ? saldoGenerado : 0.0)
                .addValue("clienteId", clienteId)
                .addValue("clienteNombre", clienteNombre)
                .addValue("tipoVenta", tipoVenta)
                .addValue("id", id));

        if (rows == 0) {
            throw new BusinessRuleException("No se pudo actualizar la cabecera (estado modificado concurrentemente).");
        }
    }

    public void updatePagoAnulado(Long pagoId) {
        String sql = "UPDATE pagos_venta SET anulado = true WHERE id = :pagoId";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource("pagoId", pagoId));
    }

    /**
     * Fetches a single detalles_venta row by its primary key.
     * Used to validate that the line item belongs to the claimed sale
     * and to calculate the refund value.
     */
    public java.util.Optional<DetalleVenta> findDetalleById(Long detalleId) {
        String sql = "SELECT * FROM detalles_venta WHERE id = :id AND (anulado = false OR anulado IS NULL)";
        List<DetalleVenta> list = namedJdbcTemplate.query(sql,
                new MapSqlParameterSource("id", detalleId), detalleMapper);
        return list.stream().findFirst();
    }

    /**
     * Finds a single detail row by its primary key with a pessimistic write lock.
     * Used to prevent race conditions during concurrent returns.
     */
    public java.util.Optional<DetalleVenta> findDetalleByIdForUpdate(Long detalleId) {
        String sql = "SELECT * FROM detalles_venta WHERE id = :id AND (anulado = false OR anulado IS NULL) FOR UPDATE";
        List<DetalleVenta> list = namedJdbcTemplate.query(sql,
                new MapSqlParameterSource("id", detalleId), detalleMapper);
        return list.stream().findFirst();
    }

    /**
     * Records a negative cash refund entry in pagos_venta.
     * This is how a direct (Efectivo) refund is represented in the ledger
     * without mutating historical rows: the negative amount offsets the original.
     */
    public void saveNegativoPago(Long ventaId, Long metodoPagoId, Double monto, Long usuarioId) {
        String sql = """
                    INSERT INTO pagos_venta (venta_id, metodo_pago_id, monto, fecha_pago, anulado, usuario_id)
                    VALUES (:ventaId, :metodoPagoId, :monto, CURRENT_TIMESTAMP, false, :usuarioId)
                """;
        namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue(VENTA_ID_PARAM, ventaId)
                .addValue(METODO_PAGO_ID, metodoPagoId)
                .addValue(MONTO, -Math.abs(monto))   // Always negative regardless of caller
                .addValue(USUARIO_ID, usuarioId));
    }
}