package com.centralizesys.aspect;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.security.CustomUserDetails;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencyAspectTest {

    private IdempotencyAspect idempotencyAspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        idempotencyAspect = new IdempotencyAspect();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/ventas/pendientes");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        CustomUserDetails user = new CustomUserDetails(1L, "Test", "test@test.com", "pass", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    void preventDoubleClick_allowsFirstRequest() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"dummyPayload1"});
        when(joinPoint.proceed()).thenReturn("Success");

        Object result = idempotencyAspect.preventDoubleClick(joinPoint);
        assertEquals("Success", result);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void preventDoubleClick_blocksIdenticalConsecutiveRequest() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"duplicatePayload"});
        when(joinPoint.proceed()).thenReturn("Success");

        // First request should succeed
        Object result1 = idempotencyAspect.preventDoubleClick(joinPoint);
        assertEquals("Success", result1);

        // Second identical request within TTL should throw BusinessRuleException
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> {
            idempotencyAspect.preventDoubleClick(joinPoint);
        });

        assertTrue(ex.getMessage().contains("solicitud duplicada"));
        verify(joinPoint, times(1)).proceed(); // Proceed was only called ONCE
    }

    @Test
    void preventDoubleClick_allowsDifferentPayloads() throws Throwable {
        when(joinPoint.proceed()).thenReturn("Success");

        // Request 1
        when(joinPoint.getArgs()).thenReturn(new Object[]{"payloadA"});
        idempotencyAspect.preventDoubleClick(joinPoint);

        // Request 2 (different payload)
        when(joinPoint.getArgs()).thenReturn(new Object[]{"payloadB"});
        idempotencyAspect.preventDoubleClick(joinPoint);

        verify(joinPoint, times(2)).proceed();
    }
}
