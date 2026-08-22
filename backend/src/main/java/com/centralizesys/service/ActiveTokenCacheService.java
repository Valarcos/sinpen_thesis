package com.centralizesys.service;

import com.centralizesys.config.CacheConfig;
import com.centralizesys.model.auth.ActiveToken;
import com.centralizesys.repository.ActiveTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * In-memory cache service for active JWT sessions (jti → userId mapping).
 *
 * <p>This service is a thin layer over the Caffeine-backed {@link CacheManager}. It provides
 * a semantically clear API to the rest of the application, hiding the cache key/value structure.
 *
 * <p><b>Cache Warm-Up:</b> On {@link ApplicationReadyEvent}, all non-expired tokens are loaded
 * from the database into the cache. This prevents a "cold cache" scenario after a server restart,
 * which would otherwise force all currently-logged-in users to re-authenticate.
 *
 * <p><b>Cache Miss Fallback:</b> If a JTI is not found in the cache (e.g., during the warm-up
 * period or for tokens issued before a restart), callers should fall back to the DB via
 * {@link ActiveTokenRepository#findByJti(String)}. This fallback logic lives in
 * {@link com.centralizesys.security.JwtAuthenticationFilter}.
 */
@Service
public class ActiveTokenCacheService {

    private static final Logger log = LoggerFactory.getLogger(ActiveTokenCacheService.class);

    private final CacheManager cacheManager;
    private final ActiveTokenRepository activeTokenRepository;

    public ActiveTokenCacheService(CacheManager cacheManager,
                                   ActiveTokenRepository activeTokenRepository) {
        this.cacheManager = cacheManager;
        this.activeTokenRepository = activeTokenRepository;
    }

    // -------------------------------------------------------------------------
    // Public Cache Operations
    // -------------------------------------------------------------------------

    /**
     * Stores the jti → userId mapping in the in-memory cache.
     *
     * @param jti      the JWT ID claim.
     * @param usuarioId the ID of the user who owns this session.
     */
    public void put(String jti, Long usuarioId) {
        getCache().put(jti, usuarioId);
    }

    /**
     * Returns {@code true} if the given JTI is present in the cache (session is active).
     * A {@code false} result means the session is either expired or was never cached —
     * the caller should perform a DB fallback.
     *
     * @param jti the JWT ID claim to validate.
     * @return {@code true} if the jti exists in the cache.
     */
    public boolean isValid(String jti) {
        return getCache().get(jti) != null;
    }

    /**
     * Evicts the cache entry for the given JTI. Called on logout.
     *
     * @param jti the JWT ID claim to evict.
     */
    public void invalidateByJti(String jti) {
        getCache().evict(jti);
    }

    /**
     * Evicts all cached JTIs that belong to the given user.
     * Called when a user logs in on a new device to invalidate any prior session.
     *
     * <p>This operation iterates the native Caffeine cache to find JTIs by value (userId).
     * It is an O(n) scan but is acceptable given the {@value CacheConfig#CACHE_MAX_SIZE} cap
     * and the infrequency of this operation (only on login).
     *
     * @param usuarioId the user whose cached sessions should be invalidated.
     */
    @SuppressWarnings("unchecked") // Safe: all values are put as Long via put(jti, userId)
    public void invalidateByUsuarioId(Long usuarioId) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                (com.github.benmanes.caffeine.cache.Cache<Object, Object>) getCache().getNativeCache();

        nativeCache.asMap().entrySet().stream()
                .filter(e -> usuarioId.equals(e.getValue()))
                .map(e -> (String) e.getKey())
                .forEach(jti -> {
                    getCache().evict(jti);
                    log.debug("Evicted cached session for userId={}, jti={}.", usuarioId, jti);
                });
    }

    // -------------------------------------------------------------------------
    // Startup Warm-Up
    // -------------------------------------------------------------------------

    /**
     * Pre-populates the cache with all non-expired tokens from the database on startup.
     * This prevents a "cold cache" forcing users to re-login after a server restart.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        List<ActiveToken> validTokens = activeTokenRepository.findAllValid(now);
        validTokens.forEach(token -> put(token.getJti(), token.getUsuarioId()));
        log.info("Active token cache warmed up with {} valid session(s).", validTokens.size());
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private Cache getCache() {
        return Objects.requireNonNull(
                cacheManager.getCache(CacheConfig.ACTIVE_TOKENS_CACHE),
                "Cache '" + CacheConfig.ACTIVE_TOKENS_CACHE + "' was not initialized by CacheConfig.");
    }
}
