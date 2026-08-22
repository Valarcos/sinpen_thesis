package com.centralizesys.repository;

import com.centralizesys.model.cheque.AlertaCheque;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class AlertaChequeRepository {

    private final JdbcTemplate jdbcTemplate;

    public AlertaChequeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AlertaCheque> rowMapper = (rs, rowNum) -> {
        AlertaCheque ac = new AlertaCheque();
        ac.setId(rs.getLong("id"));
        ac.setVentaId(rs.getLong("venta_id"));
        ac.setMonto(rs.getDouble("monto"));
        ac.setFechaCobro(rs.getDate("fecha_cobro").toLocalDate());
        ac.setEstado(rs.getString("estado"));
        ac.setPagoVentaId(rs.getObject("pago_venta_id") != null ? rs.getLong("pago_venta_id") : null);
        ac.setTipoOrigen(rs.getString("tipo_origen"));
        ac.setPagoDeudaId(rs.getObject("pago_deuda_id") != null ? rs.getLong("pago_deuda_id") : null);
        ac.setMetodoPagoNombre(rs.getString("metodo_pago_nombre"));
        return ac;
    };

    public Long save(AlertaCheque alerta) {
        String sql = "INSERT INTO alertas_cheques (venta_id, monto, fecha_cobro, estado, pago_venta_id, tipo_origen, pago_deuda_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, alerta.getVentaId());
            ps.setDouble(2, alerta.getMonto());
            ps.setDate(3, Date.valueOf(alerta.getFechaCobro()));
            ps.setString(4, alerta.getEstado());
            if (alerta.getPagoVentaId() != null) {
                ps.setLong(5, alerta.getPagoVentaId());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            if (alerta.getTipoOrigen() != null) {
                ps.setString(6, alerta.getTipoOrigen());
            } else {
                ps.setString(6, "VENTA");
            }
            if (alerta.getPagoDeudaId() != null) {
                ps.setLong(7, alerta.getPagoDeudaId());
            } else {
                ps.setNull(7, java.sql.Types.INTEGER);
            }
            return ps;
        }, keyHolder);

        if (keyHolder.getKeys() != null) {
            return ((Number) keyHolder.getKeys().get("id")).longValue();
        }
        return null;
    }

    public List<AlertaCheque> findByVentaId(Long ventaId) {
        String sql = """
            SELECT ac.*, mp.descripcion as metodo_pago_nombre
            FROM alertas_cheques ac
            LEFT JOIN pagos_venta pv ON ac.pago_venta_id = pv.id
            LEFT JOIN metodos_pago mp ON pv.metodo_pago_id = mp.id
            WHERE ac.venta_id = ? 
            ORDER BY ac.fecha_cobro ASC
        """;
        return jdbcTemplate.query(sql, rowMapper, ventaId);
    }

    public void updateEstadoByVentaId(Long ventaId, String nuevoEstado) {
        String sql = "UPDATE alertas_cheques SET estado = ? WHERE venta_id = ?";
        jdbcTemplate.update(sql, nuevoEstado, ventaId);
    }

    public List<AlertaCheque> findPendingExpiredOrToday() {
        String sql = "SELECT ac.*, NULL as metodo_pago_nombre FROM alertas_cheques ac WHERE ac.estado = 'PENDIENTE' AND ac.fecha_cobro <= CURRENT_DATE ORDER BY ac.fecha_cobro ASC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public Optional<AlertaCheque> findById(Long id) {
        String sql = "SELECT ac.*, NULL as metodo_pago_nombre FROM alertas_cheques ac WHERE ac.id = ?";
        List<AlertaCheque> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public void updateEstadoAndPagoVentaId(Long id, String nuevoEstado, Long pagoVentaId) {
        String sql = "UPDATE alertas_cheques SET estado = ?, pago_venta_id = ? WHERE id = ?";
        jdbcTemplate.update(sql, nuevoEstado, pagoVentaId, id);
    }

    public int updateEstadoAndPagoVentaIdAtomic(Long id, String nuevoEstado, Long pagoVentaId, String estadoEsperado) {
        String sql = "UPDATE alertas_cheques SET estado = ?, pago_venta_id = ? WHERE id = ? AND estado = ?";
        return jdbcTemplate.update(sql, nuevoEstado, pagoVentaId, id, estadoEsperado);
    }

    public void updateEstadoAndPagoDeudaId(Long id, String nuevoEstado, Long pagoDeudaId) {
        String sql = "UPDATE alertas_cheques SET estado = ?, pago_deuda_id = ? WHERE id = ?";
        jdbcTemplate.update(sql, nuevoEstado, pagoDeudaId, id);
    }

    public int updateEstadoAndPagoDeudaIdAtomic(Long id, String nuevoEstado, Long pagoDeudaId, String estadoEsperado) {
        String sql = "UPDATE alertas_cheques SET estado = ?, pago_deuda_id = ? WHERE id = ? AND estado = ?";
        return jdbcTemplate.update(sql, nuevoEstado, pagoDeudaId, id, estadoEsperado);
    }

    public int updateEstadoAtomic(Long id, String nuevoEstado, String estadoEsperado) {
        String sql = "UPDATE alertas_cheques SET estado = ? WHERE id = ? AND estado = ?";
        return jdbcTemplate.update(sql, nuevoEstado, id, estadoEsperado);
    }

    /**
     * Returns the sum of all cheque amounts for a given sale that are strictly PENDIENTE.
     * Used to calculate total paid accurately since COBRADO cheques are already represented in pagos_venta.
     */
    public double sumMontoPendienteByVentaId(Long ventaId) {
        String sql = "SELECT COALESCE(SUM(monto), 0) FROM alertas_cheques WHERE venta_id = ? AND estado = 'PENDIENTE'";
        Double result = jdbcTemplate.queryForObject(sql, Double.class, ventaId);
        return result != null ? result : 0.0;
    }

    public void cancelarChequesPendientesByVentaId(Long ventaId) {
        String sql = "UPDATE alertas_cheques SET estado = 'ANULADA' WHERE venta_id = ? AND estado = 'PENDIENTE'";
        jdbcTemplate.update(sql, ventaId);
    }
}
