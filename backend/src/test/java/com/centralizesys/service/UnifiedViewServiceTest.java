package com.centralizesys.service;

import com.centralizesys.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UnifiedViewServiceTest {

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @InjectMocks
    private UnifiedViewService unifiedViewService;

    @BeforeEach
    void setUp() {
        when(namedParameterJdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCobrosYPedidos_AdminRole_NoUserFilterInjected() {
        // Arrange
        CustomUserDetails userDetails = new CustomUserDetails(
                1L, "admin@test.com", "pass", "Admin",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        // Act
        unifiedViewService.getCobrosYPedidos();

        // Assert
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(namedParameterJdbcTemplate).queryForList(sqlCaptor.capture(), paramsCaptor.capture());

        String executedSql = sqlCaptor.getValue();
        MapSqlParameterSource executedParams = paramsCaptor.getValue();

        assertFalse(executedSql.contains("AND v.usuario_id = :userId"), "Admin no debe tener filtro de usuario para v");
        assertFalse(executedSql.contains("AND p.usuario_id = :userId"), "Admin no debe tener filtro de usuario para p");
        assertFalse(executedParams.hasValue("userId"), "Los parametros no deben contener userId para Admin");
    }

    @Test
    void getCobrosYPedidos_EmpleadoRole_InjectsUserFilter() {
        // Arrange
        Long empleadoId = 5L;
        CustomUserDetails userDetails = new CustomUserDetails(
                empleadoId, "emp@test.com", "pass", "Empleado",
                List.of(new SimpleGrantedAuthority("ROLE_EMPLEADO"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );

        // Act
        unifiedViewService.getCobrosYPedidos();

        // Assert
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(namedParameterJdbcTemplate).queryForList(sqlCaptor.capture(), paramsCaptor.capture());

        String executedSql = sqlCaptor.getValue();
        MapSqlParameterSource executedParams = paramsCaptor.getValue();

        assertTrue(executedSql.contains("AND v.usuario_id = :userId"), "Empleado debe tener filtro de usuario para v");
        assertTrue(executedSql.contains("AND p.usuario_id = :userId"), "Empleado debe tener filtro de usuario para p");
        assertTrue(executedParams.hasValue("userId"), "Los parametros deben contener userId para Empleado");
        assertEquals(empleadoId, executedParams.getValue("userId"), "El userId filtrado debe ser el ID del empleado actual");
    }
}
