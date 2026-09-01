package com.centralizesys.controller;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.auth.UpdateUserRequest;
import com.centralizesys.security.JwtTokenProvider;
import com.centralizesys.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleMutationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UsuarioService usuarioService;

    private MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("IT-ROLE-MUTATION-01: Verifies that updating a user's role dynamically changes permissions on the same active session JWT")
    void testRoleMutationUpdatesPermissionsDynamically() throws Exception {
        // 1. Create a base EMPLEADO
        jdbcTemplate.update("INSERT INTO usuarios (nombre, email, password_hash, rol, activo) VALUES ('Mutant Emp', 'mutant@uvs.com', 'hash', 'EMPLEADO', true) ON CONFLICT DO NOTHING");
        Long empleadoId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE email = 'mutant@uvs.com'", Long.class);
        assertNotNull(empleadoId);

        // 2. Generate a valid JWT token for this user
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("mutant@uvs.com", null);
        String jwtToken = jwtTokenProvider.generateToken(auth);

        // Force session active cache/DB entry so JwtAuthenticationFilter accepts it
        String jti = jwtTokenProvider.getJtiFromToken(jwtToken);
        jdbcTemplate.update("INSERT INTO active_tokens (jti, usuario_id, expires_at) VALUES (?, ?, ?)",
                jti, empleadoId, java.sql.Timestamp.valueOf(jwtTokenProvider.getExpirationFromToken(jwtToken)));

        // 3. Empleado tries to hit the Admin-only /api/usuarios endpoint
        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isForbidden()); // EMPLEADO lacks ROLE_ADMIN

        // 4. Admin updates the Empleado's role to ADMIN via the service
        // We simulate the admin context just to satisfy Auditoria/Update permissions if needed
        authenticateUser(createTestUser(), "ROLE_ADMIN");
        UpdateUserRequest updateReq = new UpdateUserRequest(null, null, null, "ADMIN", null);
        usuarioService.update(empleadoId, updateReq);
        SecurityContextHolder.clearContext();

        // 5. The formerly Empleado user uses the EXACT SAME JWT to hit the endpoint again
        // Because JwtAuthenticationFilter reloads CustomUserDetails from the DB, they should now be authorized!
        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isOk()); // Now authorized as ADMIN!

        // 6. Downgrade back to EMPLEADO to test revocation of privileges
        authenticateUser(createTestUser(), "ROLE_ADMIN");
        UpdateUserRequest downgradeReq = new UpdateUserRequest(null, null, null, "EMPLEADO", null);
        usuarioService.update(empleadoId, downgradeReq);
        SecurityContextHolder.clearContext();

        // 7. Verify the same token is now Forbidden again
        mockMvc.perform(get("/api/usuarios")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken))
                .andExpect(status().isForbidden());
    }
}
