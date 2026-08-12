package com.centralizesys.repository;

import com.centralizesys.model.sales.MetodoPago;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MetodoPagoRepository {

    private final JdbcTemplate jdbcTemplate;

    public MetodoPagoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MetodoPago> rowMapper = (rs, rowNum) -> new MetodoPago(
            rs.getLong("id"),
            rs.getString("acronimo"),
            rs.getString("descripcion"),
            rs.getBoolean("activo")
    );

    // Used to populate the "Payment Method" dropdown in the frontend.
    public List<MetodoPago> findAll() {
        return jdbcTemplate.query("SELECT * FROM metodos_pago WHERE activo = true ORDER BY id", rowMapper);
    }

    public java.util.Optional<MetodoPago> findById(Long id) {
        List<MetodoPago> results = jdbcTemplate.query("SELECT * FROM metodos_pago WHERE id = ?", rowMapper, id);
        return results.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(results.getFirst());
    }

    /**
     * Looks up a payment method by its acronym string.
     * The service layer uses this (e.g., 'SALDO') so it never relies
     * on a hardcoded auto-incremented ID that can differ between environments.
     */
    public java.util.Optional<MetodoPago> findByAcronimo(String acronimo) {
        List<MetodoPago> results = jdbcTemplate.query(
                "SELECT * FROM metodos_pago WHERE acronimo = ?", rowMapper, acronimo);
        return results.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(results.getFirst());
    }
}