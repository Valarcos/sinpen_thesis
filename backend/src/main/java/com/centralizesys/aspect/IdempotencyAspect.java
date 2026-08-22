package com.centralizesys.aspect;

import com.centralizesys.exception.BusinessRuleException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Global Idempotency Guard (Double-Click Preventer)
 * This aspect intercepts all POST, PUT, PATCH, and DELETE requests and hashes their payloads.
 * If the exact same user submits the exact same payload to the exact same URI within 5 seconds,
 * it blocks it to prevent double-billing, duplicate cart creation, and race conditions.
 */
@Aspect
@Component
public class IdempotencyAspect {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyAspect.class);

    // Reduced TTL to 2 seconds to mathematically block double-clicks (usually < 500ms)
    // while preventing false positives for humans legitimately repeating actions.
    private final Cache<String, Boolean> idempotencyCache = Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.SECONDS)
            .maximumSize(10_000)
            .build();

    @Around("@annotation(com.centralizesys.aspect.Idempotent)")
    public Object preventDoubleClick(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return joinPoint.proceed();

        HttpServletRequest request = attributes.getRequest();

        if ("GET".equalsIgnoreCase(request.getMethod())) return joinPoint.proceed();

        String userId = String.valueOf(com.centralizesys.security.SecurityUtils.getAuthenticatedUserId());

        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Deep hash of the controller method arguments (the deserialized JSON body DTOs)
        String argsHash = String.valueOf(Arrays.deepHashCode(joinPoint.getArgs()));

        String rawKey = userId + ":" + method + ":" + uri + ":" + argsHash;
        String hashKey = generateHash(rawKey);

        // Atomic check-and-set
        if (idempotencyCache.asMap().putIfAbsent(hashKey, Boolean.TRUE) != null) {
            logger.warn("Idempotency Guard triggered! Blocked duplicate {} request to {} from user {}", method, uri, userId);
            throw new BusinessRuleException("Se ha detectado una solicitud duplicada. Por favor, espere un momento antes de volver a intentar.");
        }

        return joinPoint.proceed();
    }

    private String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return input;
        }
    }
}
