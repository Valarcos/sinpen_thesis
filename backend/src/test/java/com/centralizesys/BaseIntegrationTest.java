package com.centralizesys;

import com.centralizesys.model.auth.Usuario;
import com.centralizesys.model.auth.UsuarioRole;
import com.centralizesys.model.product.Location;
import com.centralizesys.model.product.Product;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.StockRepository;
import com.centralizesys.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
// 1. Load our Application Config automatically
// 2. Prevent Spring from replacing the DataSource with an in-memory DB
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// 3. Rollback changes after every test method
@Transactional
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    // Common Repositories needed by most tests
    // Useful for quick assertions (e.g. "SELECT count(*) FROM...")
    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected StockRepository stockRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /**
     * Helper to create a default Admin user.
     * Most flows (Venta, Compra) require a valid user ID.
     *
     * @return The ID of the created user.
     */
    protected Long createTestUser() {
        // Check if exists to avoid unique constraint errors if tests share context
        return usuarioRepository.findByEmail("test@admin.com")
                .map(Usuario::getId)
                .orElseGet(() -> {
                    Usuario u = new Usuario();
                    u.setNombre("Test Admin");
                    u.setEmail("test@admin.com");
                    u.setPasswordHash(passwordEncoder.encode("123456"));
                    u.setRol(UsuarioRole.ADMIN);
                    usuarioRepository.save(u);

                    // Retrieve ID
                    return usuarioRepository.findByEmail("test@admin.com")
                            .orElseThrow(() -> new RuntimeException("User creation failed"))
                            .getId();
                });
    }

    /**
     * Helper to authenticate a user context for tests relying on SecurityUtils.
     */
    protected void authenticateUser(Long userId, String role) {
        com.centralizesys.security.CustomUserDetails userDetails = new com.centralizesys.security.CustomUserDetails(
                userId, "test" + userId + "@test.com", "pass", "Test User",
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(role))
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    /**
     * Helper to create a generic product with stock.
     *
     * @param code  The ART code (e.g. "A-100")
     * @param price Retail price
     * @param stock Qty to add
     * @return The ID of the created product.
     */
    protected Long createTestProduct(String code, Double price, Long stock) {
        // 1. Create Location if not exists (Idempotent check)
        Long locId = stockRepository.findAllLocations().stream()
                .filter(l -> l.getNombre().equals("1"))
                .findFirst()
                .map(Location::getId)
                .orElseGet(() -> stockRepository.createLocation("1"));

        // 2. Check if Product exists by Code AND Price
        List<Product> candidates = productRepository.findAllByCodigo(code);

        Product existingProduct = candidates.stream()
                .filter(p -> p.getPrecioMinorista().equals(price)) // Match strictly by price
                .findFirst()
                .orElse(null);

        Long prodId;
        if (existingProduct != null) {
            prodId = existingProduct.getId();
            // Reset stock for the test
            stockRepository.updateQuantity(prodId, locId, stock);
        } else {
            // The 5-arg constructor sets safe defaults: stock=0, activo=true, creadoPor=0, actualizadoPor=0
            Product p = Product.builder()
                    .codigo(code)
                    .descripcion("Test Desc")
                    .precioCosto(price * 0.5)
                    .precioMayorista(price * 0.8)
                    .precioMinorista(price)
                    .build();
            Product saved = productRepository.save(p);
            prodId = saved.getId();
        }

        // 3. Update/Add Stock
        if (stock > 0) {
            stockRepository.updateQuantity(prodId, locId, stock);
        }

        return prodId;
    }

    // Use a cleaner check for data to ensure isolation
    @SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
    @BeforeEach
    protected void cleanTransactionalData() {
        // Schema is already updated in schema.sql

        // 1. Delete dependent tables first (Foreign Key Order)
        // We MUST empty 'auditoria' because it references Users
        // In PostgreSQL, 'auditoria' has a BEFORE DELETE trigger that prevents DELETES.
        // TRUNCATE bypasses row-level DELETE triggers and is faster.
        jdbcTemplate.execute("TRUNCATE TABLE auditoria");

        // Return Ledger (depends on detalles_venta and ventas)
        jdbcTemplate.execute("DELETE FROM devoluciones_venta");

        // Sales Cycle
        jdbcTemplate.execute("DELETE FROM pagos_deuda");
        jdbcTemplate.execute("DELETE FROM deudores");
        jdbcTemplate.execute("DELETE FROM pagos_venta");
        jdbcTemplate.execute("DELETE FROM detalles_venta");
        jdbcTemplate.execute("DELETE FROM alertas_cheques"); // Must delete before ventas due to FK
        jdbcTemplate.execute("DELETE FROM ventas");

        // Purchase Cycle (Missing in your previous code)
        jdbcTemplate.execute("DELETE FROM detalles_compra");
        jdbcTemplate.execute("DELETE FROM compras");

        // Caja Cycle
        jdbcTemplate.execute("DELETE FROM gastos_caja");

        // Cheques Cycle
        jdbcTemplate.execute("ALTER TABLE alertas_cheques DROP CONSTRAINT IF EXISTS alertas_cheques_estado_check");
        jdbcTemplate.execute("ALTER TABLE alertas_cheques ADD CONSTRAINT alertas_cheques_estado_check CHECK(estado IN ('PENDIENTE', 'COBRADO', 'ANULADA'))");

        // Inventory
        jdbcTemplate.execute("DELETE FROM stock_por_ubicacion");

        // 2. Clear Products (Safe to delete as long as stock/sales are gone)
        jdbcTemplate.execute("DELETE FROM productos");

        // Clients (ventas and deudores referencing clientes are already deleted above)
        jdbcTemplate.execute("DELETE FROM clientes");

        // 3. DO NOT DELETE 'usuarios' or 'ubicaciones'
        // schema.sql inserts 'Administrador'. If we delete it, we might break
        // assumptions or future tests. Since we deleted 'auditoria' and 'ventas'.

        // 3. Delete UBICACIONES
        // We must delete this because LocationServiceIntegrationTest relies on the
        // table being empty to assert that it can create location "1".
        // Other tests (like createTestProduct) might silently create "1".
        jdbcTemplate.execute("DELETE FROM ubicaciones");

        // 4. Delete test users but KEEP system/admin users to avoid UNIQUE constraint failures
        // Clear security session data first (active_tokens has FK to usuarios)
        jdbcTemplate.execute("DELETE FROM active_tokens");
        jdbcTemplate.execute("DELETE FROM login_attempts");
        jdbcTemplate.execute("DELETE FROM usuarios WHERE email NOT IN ('sistema@centralizesys.internal', 'marcosachavalmbaj@gmail.com')");
    }
}