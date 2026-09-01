package com.centralizesys.repository;

import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class UsuarioRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public static final String EMAIL = "email";
    public static final String NOMBRE = "nombre";
    private static final String PARAM_ID = "id";

    public UsuarioRepository(JdbcTemplate jdbcTemplate) {
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    private final RowMapper<Usuario> rowMapper = (rs, rowNum) -> new Usuario(
            rs.getLong("id"),
            rs.getString(NOMBRE),
            rs.getString(EMAIL),
            rs.getString("password_hash"),
            rs.getString("security_pin"),
            UsuarioRole.valueOf(rs.getString("rol")),
            rs.getObject("fecha_creacion", LocalDateTime.class),
            rs.getBoolean("activo"));

    public Optional<Usuario> findByEmail(String email) {
        String sql = "SELECT * FROM usuarios WHERE email = :email AND activo = true";
        List<Usuario> users = namedJdbcTemplate.query(
                sql,
                new MapSqlParameterSource(EMAIL, email),
                rowMapper);
        return users.stream().findFirst();
    }

    public Optional<Usuario> findById(Long id) {
        String sql = "SELECT * FROM usuarios WHERE id = :id AND activo = true";
        List<Usuario> users = namedJdbcTemplate.query(
                sql,
                new MapSqlParameterSource(PARAM_ID, id),
                rowMapper);
        return users.stream().findFirst();
    }

    public void save(Usuario usuario) {
        String sql = """
                    INSERT INTO usuarios (nombre, email, password_hash, security_pin, rol)
                    VALUES (:nombre, :email, :passwordHash, :securityPin, :rol)
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(NOMBRE, usuario.getNombre())
                .addValue(EMAIL, usuario.getEmail())
                .addValue("passwordHash", usuario.getPasswordHash())
                .addValue("securityPin", usuario.getSecurityPin())
                .addValue("rol", usuario.getRol().name());

        namedJdbcTemplate.update(sql, params);
    }

    public List<Usuario> findAll() {
        String sql = "SELECT * FROM usuarios WHERE activo = true";
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource(), rowMapper);
    }

    /**
     * Returns the count of currently active ADMIN users.
     * Used by the delete guard to prevent a full system lockout.
     */
    public Long countActiveAdmins() {
        String sql = "SELECT COUNT(*) FROM usuarios WHERE rol = 'ADMIN' AND activo = true";
        return namedJdbcTemplate.getJdbcTemplate().queryForObject(sql, Long.class);
    }

    public void deleteById(Long id) {
        // Logical Deletion: preserves all audit trail references tied to this user.
        // Sets activo = false, making the user invisible to all application queries
        // and blocking their ability to authenticate.
        String sql = "UPDATE usuarios SET activo = false WHERE id = :id";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource(PARAM_ID, id));
    }

    public void update(Usuario usuario) {
        String sql = """
                    UPDATE usuarios
                    SET nombre = :nombre, email = :email, password_hash = :passwordHash, security_pin = :securityPin, rol = :rol
                    WHERE id = :id
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_ID, usuario.getId())
                .addValue(NOMBRE, usuario.getNombre())
                .addValue(EMAIL, usuario.getEmail())
                .addValue("passwordHash", usuario.getPasswordHash())
                .addValue("securityPin", usuario.getSecurityPin())
                .addValue("rol", usuario.getRol().name());

        namedJdbcTemplate.update(sql, params);
    }
}