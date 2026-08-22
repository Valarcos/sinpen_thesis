package com.centralizesys.repository;

import com.centralizesys.model.audit.Auditoria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AuditoriaRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public AuditoriaRepository(JdbcTemplate jdbcTemplate) {
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private final RowMapper<Auditoria> rowMapper = (rs, rowNum) -> {
        Long usuarioIdVal = rs.getLong("usuario_id");
        Long usuarioId = rs.wasNull() ? null : usuarioIdVal;
        return new Auditoria(
                rs.getLong("id"),
                rs.getObject("fecha_hora", LocalDateTime.class),
                usuarioId, // Handle nullable via wasNull() check
                rs.getString("accion"),
                rs.getString("detalles"));
    };

    public void save(Long usuarioId, String accion, String detalles) {
        String sql = """
                    INSERT INTO auditoria (usuario_id, accion, detalles, fecha_hora)
                    VALUES (:usuarioId, :accion, :detalles, :fechaHora)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("usuarioId", usuarioId)
                .addValue("accion", accion)
                .addValue("detalles", detalles)
                .addValue("fechaHora", LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")));

        namedJdbcTemplate.update(sql, params);
    }

    // Find logs within a range
    public List<Auditoria> findByDateRange(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        String sql = """
                    SELECT * FROM auditoria
                    WHERE fecha_hora BETWEEN :start AND :end
                    ORDER BY fecha_hora DESC, id DESC
                    LIMIT 1000
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("start", startDateTime)
                .addValue("end", endDateTime);

        return namedJdbcTemplate.query(sql, params, rowMapper);
    }

    public List<Auditoria> findAll() {
        String sql = "SELECT * FROM auditoria ORDER BY fecha_hora DESC, id DESC";
        return namedJdbcTemplate.query(sql, rowMapper);
    }
}