package com.centralizesys.repository;

import com.centralizesys.model.product.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    // SQL parameter name constants (Sonar S1192 - avoids duplicated string literals)
    private static final String PARAM_ID = "id";
    private static final String PARAM_IDS = "ids";
    private static final String PARAM_CODIGO = "codigo";
    private static final String PARAM_DESCRIPCION = "descripcion";
    private static final String PARAM_TERMINO = "termino";
    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_OFFSET = "offset";

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    // Mapeo exacto de Columnas DB (Español) -> Objeto Java
    private final RowMapper<Product> rowMapper = (rs, rowNum) -> {
        // Safe nullable Double extraction - handles empty strings from old SQLite-era data
        Double precioMayorista = parseNullableDouble(rs.getString("precio_mayorista"));

        return new Product(
                rs.getLong("id"),
                rs.getString(PARAM_CODIGO),
                rs.getString(PARAM_DESCRIPCION),
                rs.getDouble("precio_costo"),
                precioMayorista,
                rs.getDouble("precio_minorista"),
                rs.getLong("cantidad_stock"),
                rs.getBoolean("activo"));
    };

    /**
     * Safely parses a nullable Double from a String.
     * PostgreSQL may return null for optional REAL columns (e.g., precio_mayorista).
     * Falls back to null on blank or unparseable input for defensive compatibility.
     */
    private Double parseNullableDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<Product> findAll() {
        String sql = "SELECT * FROM productos WHERE activo = true";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public Optional<Product> findById(Long id) {
        String sql = "SELECT * FROM productos WHERE id = :id AND activo = true";
        List<Product> results = namedJdbcTemplate.query(sql, new MapSqlParameterSource(PARAM_ID, id), rowMapper);
        return results.stream().findFirst();
    }

    // Fetch multiple IDs at once to avoid N+1 problem
    public List<Product> findAllById(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // Static SQL allows DB caching and satisfies Sonar
        String sql = "SELECT * FROM productos WHERE id IN (:ids) AND activo = true";
        MapSqlParameterSource parameters = new MapSqlParameterSource(PARAM_IDS, ids);
        return namedJdbcTemplate.query(sql, parameters, rowMapper);
    }

    // Buscador por Código ART (Fundamental para sync con Excel)
    // Uses List<Product> to support multiple variants (same code, different cost)
    // SAFETY: Filters activo = true, preventing barcode/search from surfacing deleted products.
    public List<Product> findAllByCodigo(String codigo) {
        String sql = "SELECT * FROM productos WHERE codigo = :codigo AND activo = true";
        return namedJdbcTemplate.query(sql, new MapSqlParameterSource(PARAM_CODIGO, codigo), rowMapper);
    }

    public Long countAll() {
        String sql = "SELECT COUNT(*) FROM productos WHERE activo = true";
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    public List<Product> findAll(Long limit, Long offset) {
        // ORDER BY groups variants of the same family contiguously so the frontend
        // teal left-border accent renders correctly across all pagination pages.
        String sql = """
                SELECT * FROM productos
                WHERE activo = true
                ORDER BY codigo ASC, LOWER(TRIM(descripcion)) ASC, id ASC
                LIMIT :limit OFFSET :offset
                """;
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue(PARAM_LIMIT, limit);
        params.addValue(PARAM_OFFSET, offset);
        return namedJdbcTemplate.query(sql, params, rowMapper);
    }

    // Buscador "Smart": Busca coincidencias en Código O Descripción
    // Útil para la UI "Super Intuitiva" donde el usuario escribe en un solo campo
    // SAFETY: Filters activo = true, guaranteeing the barcode scanner and typed
    // search can never surface logically-deleted products to the frontend.
    public List<Product> search(String query) {
        if (query == null || query.isBlank())
            return List.of();

        String trimmed = query.trim();

        // User requested: If the query is strictly numeric, filter to only show results
        // where the code starts with those digits to avoid hundreds of substring matches.
        if (trimmed.matches("\\d+")) {
            String termCodigo = trimmed + "%";
            String sql = """
                        SELECT * FROM productos
                        WHERE activo = true
                        AND UPPER(codigo) LIKE UPPER(:termCodigo)
                        ORDER BY cantidad_stock DESC, id DESC
                        LIMIT 100
                    """;
            return namedJdbcTemplate.query(sql, new MapSqlParameterSource("termCodigo", termCodigo), rowMapper);
        }

        String term = "%" + trimmed + "%";
        // STRICT: Limit to 100 to prevent UI performance issues.
        // ORDER BY: variants with stock are returned first; among ties, newest (highest ID) wins,
        // reflecting the most recent purchase cost for accurate price display.
        String sql = """
                    SELECT * FROM productos
                    WHERE activo = true
                    AND (UPPER(codigo) LIKE UPPER(:termino) OR LOWER(descripcion) LIKE LOWER(:termino))
                    ORDER BY cantidad_stock DESC, id DESC
                    LIMIT 100
                """;

        return namedJdbcTemplate.query(sql, new MapSqlParameterSource(PARAM_TERMINO, term), rowMapper);
    }

    public Product save(Product producto) {
        if (producto.getId() == null) {
            return insert(producto);
        } else {
            return update(producto);
        }
    }

    private Product insert(Product p) {
        // NOTA: No insertamos cantidad_stock explícitamente, dejamos el DEFAULT 0
        // [NOTE] Params (:codigo, :descripcion, etc) match DTO fields automatically via
        // BeanPropertySqlParameterSource
        String sql = """
                    INSERT INTO productos (codigo, descripcion, precio_costo, precio_mayorista, precio_minorista)
                    VALUES (:codigo, :descripcion, :precioCosto, :precioMayorista, :precioMinorista)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        SqlParameterSource params = new BeanPropertySqlParameterSource(p);

        namedJdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});

        Number key = keyHolder.getKey();
        if (key != null) {
            p.setId(key.longValue());
        }
        return p;
    }

    private Product update(Product p) {
        // NOTA: NO actualizamos cantidad_stock aquí.
        // El stock se modifica SOLO moviendo items en stock_por_ubicacion (Triggers).

        // TODO: update should be monitored. The article code shouldn't be updated, but
        // based on the possible case of low quality products with NO inherent art code,
        // it may be required.
        String sql = """
                    UPDATE productos
                    SET codigo = :codigo,
                        descripcion = :descripcion,
                        precio_costo = :precioCosto,
                        precio_mayorista = :precioMayorista,
                        precio_minorista = :precioMinorista
                    WHERE id = :id
                """;
        SqlParameterSource params = new BeanPropertySqlParameterSource(p);
        namedJdbcTemplate.update(sql, params);
        return p;
    }

    public void deleteById(Long id) {
        // Logical Deletion: preserves stock history and all transactional references.
        // Sets activo = false, making the product invisible to all application queries.
        String sql = "UPDATE productos SET activo = false WHERE id = :id";
        namedJdbcTemplate.update(sql, new MapSqlParameterSource(PARAM_ID, id));
    }

    /**
     * Find active products with negative stock (stock < 0).
     * Used by Dashboard "Morning Warning" modal.
     */
    public List<Product> findLowStock() {
        String sql = "SELECT * FROM productos WHERE activo = true AND cantidad_stock < 0";
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * Calculates the Weighted Average Cost (WAC) of all active variants sharing
     * the same product family (identified by codigo, and optionally descripcion
     * for generic products with codigo = "1").
     *
     * NOTE: The descripcion comparison uses LOWER(TRIM()) for case-insensitive
     * matching that mirrors the frontend grouping logic.
     *
     * Uses GREATEST(0, cantidad_stock) to zero-clamp negative stock contributions.
     * This prevents phantom-sold inventory (negative stock) from deflating the average
     * and producing artificially low cost-of-goods-sold figures.
     *
     * Returns Optional.empty() when all variants have zero or negative stock,
     * which signals the caller to use the newest variant's precio_costo as a fallback.
     */
    public Optional<Double> findWAC(String codigo, String descripcion) {
        // CASE WHEN is used instead of GREATEST() for cross-database compatibility (SQLite test env).
        // CAST(:descripcion AS TEXT) resolves PostgreSQL type ambiguity for nullable parameters.
        String sql = """
                SELECT SUM(CASE WHEN cantidad_stock > 0 THEN cantidad_stock ELSE 0 END * precio_costo)
                     / NULLIF(SUM(CASE WHEN cantidad_stock > 0 THEN cantidad_stock ELSE 0 END), 0)
                FROM productos
                WHERE codigo = :codigo
                  AND (CAST(:descripcion AS TEXT) IS NULL OR LOWER(TRIM(descripcion)) = LOWER(TRIM(CAST(:descripcion AS TEXT))))
                  AND activo = true
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_CODIGO, codigo)
                .addValue(PARAM_DESCRIPCION, descripcion);
        Double result = namedJdbcTemplate.queryForObject(sql, params, Double.class);
        return Optional.ofNullable(result);
    }

    /**
     * Returns all active variants belonging to a product family, sorted oldest-first.
     *
     * Family key rules (matching frontend grouping logic):
     *   - Standard products (codigo != "1"): family = all variants sharing the same codigo.
     *   - Generic products (codigo = "1"):  family = all variants sharing codigo AND descripcion,
     *     because "1" is a shared bucket for unrelated items.
     *
     * When descripcion is null, filters by codigo only (used for standard products).
     * When descripcion is non-null, filters by both (used for generic products), case-insensitive.
     *
     * ORDER BY id ASC ensures oldest (cheapest) variants are deducted first in FIFO logic.
     */
    public List<Product> findSiblingsByFamily(String codigo, String descripcion) {
        // CAST(:descripcion AS TEXT) resolves PostgreSQL type ambiguity for nullable parameters.
        String sql = """
                SELECT * FROM productos
                WHERE codigo = :codigo
                  AND (CAST(:descripcion AS TEXT) IS NULL OR LOWER(TRIM(descripcion)) = LOWER(TRIM(CAST(:descripcion AS TEXT))))
                  AND activo = true
                ORDER BY id ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_CODIGO, codigo)
                .addValue(PARAM_DESCRIPCION, descripcion);
        return namedJdbcTemplate.query(sql, params, rowMapper);
    }

    /**
     * Returns true if at least one active product with the given codigo exists.
     * Used by the update flow to detect and block cross-family barcode reassignments.
     */
    public boolean existsByCodigo(String codigo) {
        String sql = "SELECT COUNT(*) FROM productos WHERE codigo = :codigo AND activo = true";
        Long count = namedJdbcTemplate.queryForObject(sql, new MapSqlParameterSource(PARAM_CODIGO, codigo), Long.class);
        return count != null && count > 0;
    }
}