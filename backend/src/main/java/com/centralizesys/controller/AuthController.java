package com.centralizesys.controller;

import com.centralizesys.model.auth.AuthRequest;
import com.centralizesys.model.auth.AuthResponse;
import com.centralizesys.model.auth.Usuario;
import com.centralizesys.repository.ActiveTokenRepository;
import com.centralizesys.repository.UsuarioRepository;
import com.centralizesys.security.JwtTokenProvider;
import com.centralizesys.service.AuditoriaService;
import com.centralizesys.service.ActiveTokenCacheService;
import com.centralizesys.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final AuditoriaService auditoriaService;
    private final UsuarioRepository usuarioRepository;
    private final LoginAttemptService loginAttemptService;
    private final ActiveTokenRepository activeTokenRepository;
    private final ActiveTokenCacheService activeTokenCacheService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider tokenProvider,
                          AuditoriaService auditoriaService,
                          UsuarioRepository usuarioRepository,
                          LoginAttemptService loginAttemptService,
                          ActiveTokenRepository activeTokenRepository,
                          ActiveTokenCacheService activeTokenCacheService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.auditoriaService = auditoriaService;
        this.usuarioRepository = usuarioRepository;
        this.loginAttemptService = loginAttemptService;
        this.activeTokenRepository = activeTokenRepository;
        this.activeTokenCacheService = activeTokenCacheService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request,
                                              HttpServletRequest httpRequest) {
        String email = request.getEmail();
        String ipAddress = httpRequest.getRemoteAddr();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));

        // 1. Pre-check: reject immediately if the account is currently blocked.
        loginAttemptService.checkAndThrowIfBlocked(email, now);

        // 2. Attempt authentication. On failure, record the attempt and re-throw.
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (Exception ex) {
            loginAttemptService.recordFailedAttempt(email, ipAddress, now);
            throw ex;
        }

        // 3. Authentication succeeded — reset the failure counter.
        loginAttemptService.resetOnSuccess(email);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 4. Generate JWT (with embedded jti claim).
        String jwt = tokenProvider.generateToken(authentication);
        String jti = tokenProvider.getJtiFromToken(jwt);
        LocalDateTime expiresAt = tokenProvider.getExpirationFromToken(jwt);

        // 5. Single-session enforcement: invalidate any prior session for this user.
        Usuario user = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no encontrado post-auth"));

        activeTokenCacheService.invalidateByUsuarioId(user.getId());
        activeTokenRepository.deleteByUsuarioId(user.getId());

        // 6. Persist the new session and warm the cache.
        activeTokenRepository.save(user.getId(), jti, expiresAt);
        activeTokenCacheService.put(jti, user.getId());

        // 7. Audit the successful login.
        auditoriaService.registrarAccion(user.getId(), "LOGIN", "Inicio de sesión exitoso.");

        return ResponseEntity.ok(new AuthResponse(jwt, user.getEmail(), user.getNombre(), user.getRol().name()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
            // No token provided — treat as already logged out (idempotent)
            return ResponseEntity.ok().build();
        }

        String jwt = bearerToken.substring(7);
        if (tokenProvider.validateToken(jwt)) {
            String jti = tokenProvider.getJtiFromToken(jwt);
            activeTokenRepository.deleteByJti(jti);
            activeTokenCacheService.invalidateByJti(jti);
            log.info("Session invalidated via logout for jti={}.", jti);
        }

        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales incorrectas");
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<String> handleLockedException(LockedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                "Su cuenta ha sido bloqueada temporalmente por múltiples intentos fallidos. " +
                        "Intente nuevamente más tarde.");
    }
}
