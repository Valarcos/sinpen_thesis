package com.centralizesys.task;

import com.centralizesys.repository.ActiveTokenRepository;
import com.centralizesys.repository.LoginAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Scheduled task that performs periodic housekeeping on security-related tables.
 *
 * <p>Runs hourly to prune data that is no longer operationally relevant:
 * <ul>
 *   <li><b>Expired tokens:</b> JWT sessions whose expiry has passed cannot be used anyway,
 *       so removing them keeps the {@code active_tokens} table lean.</li>
 *   <li><b>Expired login blocks:</b> Records in {@code login_attempts} whose
 *       {@code blocked_until} has passed are pruned. Records without a block are retained
 *       (they hold the attempt counter, which resets on next successful login).</li>
 * </ul>
 */
@Component
public class TokenCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupTask.class);

    private final ActiveTokenRepository activeTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;

    public TokenCleanupTask(ActiveTokenRepository activeTokenRepository,
                            LoginAttemptRepository loginAttemptRepository) {
        this.activeTokenRepository = activeTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
    }

    /**
     * Runs every hour at minute 0. Deletes expired sessions and lifted login blocks.
     *
     * <p>Cron expression: {@code 0 0 * * * *} (top of every hour).
     */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredData() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        log.info("Running security housekeeping cleanup at {}.", now);

        activeTokenRepository.deleteExpired(now);
        log.info("Purged expired active_tokens before {}.", now);

        loginAttemptRepository.deleteExpiredBlocks(now);
        log.info("Purged expired login_attempts blocks before {}.", now);
    }
}
