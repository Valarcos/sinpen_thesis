package com.centralizesys.controller;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerSecurityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private Long empleadoId;
    private Long adminId;
    private Long ownerId;

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
                .apply(springSecurity())
                .build();

        // Seed users
        jdbcTemplate.update("INSERT INTO usuarios (nombre, email, password_hash, security_pin, rol, activo) VALUES ('Emp', 'emp_report@test.com', 'hash', ?, 'EMPLEADO', true) ON CONFLICT DO NOTHING", passwordEncoder.encode("1111"));
        jdbcTemplate.update("INSERT INTO usuarios (nombre, email, password_hash, security_pin, rol, activo) VALUES ('Admin', 'admin_report@test.com', 'hash', ?, 'ADMIN', true) ON CONFLICT DO NOTHING", passwordEncoder.encode("2222"));
        jdbcTemplate.update("INSERT INTO usuarios (nombre, email, password_hash, security_pin, rol, activo) VALUES ('Owner', 'owner_report@test.com', 'hash', ?, 'OWNER', true) ON CONFLICT DO NOTHING", passwordEncoder.encode("3333"));

        empleadoId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE email = 'emp_report@test.com'", Long.class);
        adminId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE email = 'admin_report@test.com'", Long.class);
        ownerId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE email = 'owner_report@test.com'", Long.class);
    }

    @Test
    @DisplayName("GIVEN unauthenticated request WHEN getGanancias THEN returns UNAUTHORIZED")
    void testUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/reportes/ganancias")
                        .header("X-Security-Pin", "2222"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GIVEN EMPLEADO user with valid PIN WHEN getGanancias THEN returns FORBIDDEN due to PreAuthorize")
    void testEmpleadoWithValidPin_IsForbidden() throws Exception {
        // Even with a valid PIN, Empleado should be blocked by @PreAuthorize
        authenticateUser(empleadoId, "ROLE_EMPLEADO");
        mockMvc.perform(get("/api/reportes/ganancias")
                        .header("X-Security-Pin", "1111"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GIVEN ADMIN user with missing PIN WHEN getGanancias THEN returns FORBIDDEN due to validateSecurityPin")
    void testAdminWithMissingPin_IsForbidden() throws Exception {
        authenticateUser(adminId, "ROLE_ADMIN");
        mockMvc.perform(get("/api/reportes/ganancias"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GIVEN ADMIN user with INVALID PIN WHEN getGanancias THEN returns FORBIDDEN due to validateSecurityPin")
    void testAdminWithInvalidPin_IsForbidden() throws Exception {
        authenticateUser(adminId, "ROLE_ADMIN");
        mockMvc.perform(get("/api/reportes/ganancias")
                        .header("X-Security-Pin", "wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GIVEN ADMIN user with VALID PIN WHEN getGanancias THEN returns OK")
    void testAdminWithValidPin_IsOk() throws Exception {
        authenticateUser(adminId, "ROLE_ADMIN");

        mockMvc.perform(get("/api/reportes/ganancias")
                        .header("X-Security-Pin", "2222"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GIVEN OWNER user with VALID PIN WHEN getGanancias THEN returns OK")
    void testOwnerWithValidPin_IsOk() throws Exception {
        authenticateUser(ownerId, "ROLE_OWNER");

        mockMvc.perform(get("/api/reportes/ganancias")
                        .header("X-Security-Pin", "3333"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GIVEN EMPLEADO user with valid PIN WHEN getEstadisticas THEN returns FORBIDDEN")
    void testEstadisticasEmpleado_IsForbidden() throws Exception {
        authenticateUser(empleadoId, "ROLE_EMPLEADO");
        mockMvc.perform(get("/api/reportes/estadisticas")
                        .header("X-Security-Pin", "1111"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GIVEN OWNER user with VALID PIN WHEN getEstadisticas THEN returns OK")
    void testEstadisticasOwner_IsOk() throws Exception {
        authenticateUser(ownerId, "ROLE_OWNER");

        mockMvc.perform(get("/api/reportes/estadisticas")
                        .header("X-Security-Pin", "3333"))
                .andExpect(status().isOk());
    }
}
