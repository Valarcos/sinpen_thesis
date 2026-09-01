package com.centralizesys.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GIVEN no auth context WHEN getAuthenticatedUserId THEN returns 0")
    void testGetAuthenticatedUserId_NoAuth() {
        assertEquals(0L, SecurityUtils.getAuthenticatedUserId());
    }

    @Test
    @DisplayName("GIVEN active auth context WHEN getAuthenticatedUserId THEN returns ID")
    void testGetAuthenticatedUserId_WithAuth() {
        CustomUserDetails user = new CustomUserDetails(5L, "test@test.com", "pass", "Test", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
        assertEquals(5L, SecurityUtils.getAuthenticatedUserId());
    }

    @Test
    @DisplayName("GIVEN Empleado context WHEN isCurrentUserEmpleado THEN returns true")
    void testIsCurrentUserEmpleado_True() {
        CustomUserDetails user = new CustomUserDetails(5L, "test@test.com", "pass", "Test", List.of(new SimpleGrantedAuthority("ROLE_EMPLEADO")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
        assertTrue(SecurityUtils.isCurrentUserEmpleado());
    }

    @Test
    @DisplayName("GIVEN Admin context WHEN isCurrentUserEmpleado THEN returns false")
    void testIsCurrentUserEmpleado_False() {
        CustomUserDetails user = new CustomUserDetails(5L, "test@test.com", "pass", "Test", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
        assertFalse(SecurityUtils.isCurrentUserEmpleado());
    }

    @Test
    @DisplayName("GIVEN no context WHEN isCurrentUserEmpleado THEN returns false")
    void testIsCurrentUserEmpleado_NoContext() {
        assertFalse(SecurityUtils.isCurrentUserEmpleado());
    }
}
