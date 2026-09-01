package com.centralizesys.controller;

import com.centralizesys.model.auth.RegisterRequest;
import com.centralizesys.service.UsuarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import java.util.Arrays;
import java.util.List;
import com.centralizesys.model.auth.Usuario;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UsuarioService usuarioService;

    @Test
    @WithMockUser(username = "admin@test.com", roles = { "ADMIN" })
    void register_AsAdmin_Success() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("New User");
        request.setEmail("new@test.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(usuarioService).registrarUsuario("New User", "new@test.com", "password123", null, null);
    }

    @Test
    @WithMockUser(username = "emp@test.com", roles = { "EMPLEADO" })
    void register_AsEmployee_Forbidden() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Hacker");
        request.setEmail("hacker@test.com");
        request.setPassword("123456");

        mockMvc.perform(post("/api/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden()); // Expect 403
    }

    @Test
    void register_Unauthenticated_Unauthorized() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Anon");
        request.setEmail("anon@test.com");
        request.setPassword("123");

        mockMvc.perform(post("/api/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // SecurityConfig.authenticationEntryPoint returns 401 for unauthenticated requests.
                // 403 Forbidden is for authenticated users lacking the required role.
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = { "ADMIN" })
    void getAllUsers_AsAdmin_Success() throws Exception {
        Usuario u1 = new Usuario();
        u1.setId(1L);
        u1.setNombre("User 1");
        u1.setRol(com.centralizesys.model.auth.UsuarioRole.EMPLEADO);

        Usuario u2 = new Usuario();
        u2.setId(2L);
        u2.setNombre("User 2");
        u2.setRol(com.centralizesys.model.auth.UsuarioRole.ADMIN);

        List<Usuario> users = Arrays.asList(u1, u2);

        given(usuarioService.getAll()).willReturn(users);

        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(username = "emp@test.com", roles = { "EMPLEADO" })
    void getAllUsers_AsEmployee_Forbidden() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = { "OWNER" })
    void getAllUsers_AsOwner_Success() throws Exception {
        Usuario u1 = new Usuario();
        u1.setId(1L);
        u1.setNombre("User 1");
        u1.setRol(com.centralizesys.model.auth.UsuarioRole.EMPLEADO);

        List<Usuario> users = Arrays.asList(u1);

        given(usuarioService.getAll()).willReturn(users);

        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = { "ADMIN" })
    void deleteUser_AsAdmin_Success() throws Exception {
        mockMvc.perform(delete("/api/usuarios/5"))
                .andExpect(status().isNoContent());

        verify(usuarioService).delete(5L);
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = { "OWNER" })
    void deleteUser_AsOwner_Success() throws Exception {
        mockMvc.perform(delete("/api/usuarios/5"))
                .andExpect(status().isNoContent());

        verify(usuarioService).delete(5L);
    }

    @Test
    @WithMockUser(username = "emp@test.com", roles = { "EMPLEADO" })
    void deleteUser_AsEmployee_Forbidden() throws Exception {
        mockMvc.perform(delete("/api/usuarios/5"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = { "ADMIN" })
    void register_WithRoles_AsAdmin_Success() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Owner User");
        request.setEmail("owner@test.com");
        request.setPassword("password123");
        request.setRol("OWNER");
        request.setSecurityPin("1234");

        mockMvc.perform(post("/api/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(usuarioService).registrarUsuario("Owner User", "owner@test.com", "password123", "OWNER", "1234");
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = { "OWNER" })
    void register_WithRoles_AsOwner_Success() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Owner User");
        request.setEmail("owner@test.com");
        request.setPassword("password123");
        request.setRol("EMPLEADO");
        request.setSecurityPin("1234");

        mockMvc.perform(post("/api/usuarios/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(usuarioService).registrarUsuario("Owner User", "owner@test.com", "password123", "EMPLEADO", "1234");
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = { "ADMIN" })
    void updateUser_AsAdmin_Success() throws Exception {
        com.centralizesys.model.auth.UpdateUserRequest request = new com.centralizesys.model.auth.UpdateUserRequest(
                "Updated Name", "updated@test.com", "newpass", "EMPLEADO", "5678"
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/usuarios/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(usuarioService).update(5L, request);
    }

    @Test
    @WithMockUser(username = "owner@test.com", roles = { "OWNER" })
    void updateUser_AsOwner_Success() throws Exception {
        com.centralizesys.model.auth.UpdateUserRequest request = new com.centralizesys.model.auth.UpdateUserRequest(
                "Updated Name", "updated@test.com", "newpass", "EMPLEADO", "5678"
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/usuarios/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(usuarioService).update(5L, request);
    }

    @Test
    @WithMockUser(username = "emp@test.com", roles = { "EMPLEADO" })
    void updateUser_AsEmployee_Forbidden() throws Exception {
        com.centralizesys.model.auth.UpdateUserRequest request = new com.centralizesys.model.auth.UpdateUserRequest(
                "Updated Name", "updated@test.com", "newpass", "EMPLEADO", "5678"
        );

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/usuarios/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
