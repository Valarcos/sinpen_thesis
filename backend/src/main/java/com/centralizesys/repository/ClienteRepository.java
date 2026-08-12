package com.centralizesys.repository;

import com.centralizesys.model.client.Cliente;
import com.centralizesys.model.client.ClienteResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ClienteRepository {

    private static final String SALDO_A_FAVOR = "saldoAFavor";
    private static final String CLIENTE_ID    = "clienteId";
    private static final String ACTIVO        = "activo";
    private static final String TELEFONO      = "telefono";
    private static final String NOMBRE        = "nombre";

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public ClienteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate    = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    // --- MAPPERS ---

    private final RowMapper<ClienteResponse> responseMapper = (rs, rowNum) -> new ClienteResponse(
            rs.getLong("id"),
            rs.getString(NOMBRE),
            rs.getString(TELEFONO),
            rs.getString("dni"),
            rs.getDouble("saldo_a_favor"),
            rs.getBoolean(ACTIVO)
    );

    private final RowMapper<Cliente> fullMapper = (rs, rowNum) -> Cliente.builder()
            .id(rs.getLong("id"))
            .nombre(rs.getString(NOMBRE))
            .telefono(rs.getString(TELEFONO))
            .dni(rs.getString("dni"))
            .saldoAFavor(rs.getDouble("saldo_a_favor"))
            .activo(rs.getBoolean(ACTIVO))
            .fechaCreacion(rs.getObject("fecha_creacion", LocalDateTime.class))
            .fechaActualizacion(rs.getObject("fecha_actualizacion", LocalDateTime.class))
            .creadoPor(rs.getLong("creado_por"))
            .actualizadoPor(rs.getLong("actualizado_por"))
            .build();

    // --- READ OPERATIONS ---

    public List<ClienteResponse> findAll() {
        String sql = "SELECT id, nombre, telefono, dni, saldo_a_favor, activo FROM clientes WHERE activo = true ORDER BY nombre ASC";
        return jdbcTemplate.query(sql, responseMapper);
    }

    public Optional<Cliente> findById(Long id) {
        String sql = "SELECT * FROM clientes WHERE id = :id";
        List<Cliente> list = namedJdbcTemplate.query(sql, new MapSqlParameterSource("id", id), fullMapper);
        return list.stream().findFirst();
    }

    /**
     * Finds a client by name for autocomplete / exact-match lookups.
     * Case-insensitive ILIKE for resilience.
     */
    public Optional<ClienteResponse> findByNombre(String nombre) {
        String sql = "SELECT id, nombre, telefono, dni, saldo_a_favor, activo FROM clientes WHERE LOWER(nombre) = LOWER(:nombre) AND activo = true";
        List<ClienteResponse> list = namedJdbcTemplate.query(sql, new MapSqlParameterSource(NOMBRE, nombre), responseMapper);
        return list.stream().findFirst();
    }

    // --- WRITE OPERATIONS ---

    public Cliente save(Cliente cliente, Long usuarioId) {
        String sql = """
                    INSERT INTO clientes (nombre, telefono, dni, saldo_a_favor, activo, creado_por, actualizado_por)
                    VALUES (:nombre, :telefono, :dni, :saldoAFavor, :activo, :usuarioId, :usuarioId)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(NOMBRE,       cliente.getNombre())
                .addValue(TELEFONO,     cliente.getTelefono())
                .addValue("dni",          cliente.getDni())
                .addValue(SALDO_A_FAVOR, cliente.getSaldoAFavor() != null ? cliente.getSaldoAFavor() : 0.0)
                .addValue(ACTIVO,       Boolean.TRUE.equals(cliente.getActivo()))
                .addValue("usuarioId",    usuarioId);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});

        Number key = keyHolder.getKey();
        if (key == null) throw new com.centralizesys.exception.InfrastructureException("La base de datos no devolvió el ID del cliente creado.");
        cliente.setId(key.longValue());
        return cliente;
    }

    /**
     * Atomically deducts `monto` from a client's saldo_a_favor.
     * Uses a single UPDATE with a WHERE guard to prevent race conditions.
     * Returns the number of rows affected: 0 means insufficient funds.
     */
    public int deductSaldo(Long clienteId, Double monto) {
        String sql = """
                    UPDATE clientes
                    SET saldo_a_favor = saldo_a_favor - :monto,
                        actualizado_por = 0
                    WHERE id = :clienteId
                      AND saldo_a_favor >= :monto
                """;
        return namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue(CLIENTE_ID, clienteId)
                .addValue("monto", monto));
    }

    /**
     * Atomically adds `monto` to a client's saldo_a_favor.
     * This is safe to call concurrently — DB handles the increment.
     */
    public void addSaldo(Long clienteId, Double monto) {
        String sql = """
                    UPDATE clientes
                    SET saldo_a_favor = saldo_a_favor + :monto,
                        actualizado_por = 0
                    WHERE id = :clienteId
                """;
        namedJdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue(CLIENTE_ID, clienteId)
                .addValue("monto", monto));
    }
}
