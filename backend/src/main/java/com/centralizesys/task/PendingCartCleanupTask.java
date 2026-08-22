package com.centralizesys.task;

import com.centralizesys.repository.VentaRepository;
import com.centralizesys.service.VentaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class PendingCartCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(PendingCartCleanupTask.class);

    private final VentaRepository ventaRepository;
    private final VentaService ventaService;

    public PendingCartCleanupTask(VentaRepository ventaRepository, VentaService ventaService) {
        this.ventaRepository = ventaRepository;
        this.ventaService = ventaService;
    }

    /**
     * Runs every day at midnight (Argentina time) to clean up PENDIENTE carts older than 48 hours.
     * Prevents funds (Saldo a Favor) from being locked indefinitely in abandoned carts.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Argentina/Buenos_Aires")
    public void cleanupStalePendingCarts() {
        logger.info("Starting PendingCartCleanupTask: checking for abandoned carts...");

        LocalDateTime cutoffDate = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")).minusHours(48);
        List<Long> staleCartIds = ventaRepository.findPendientesOlderThan(cutoffDate);

        if (staleCartIds.isEmpty()) {
            logger.info("No stale carts found.");
            return;
        }

        int successCount = 0;
        for (Long cartId : staleCartIds) {
            try {
                // User 0L represents the System
                ventaService.cancelarPendiente(cartId, 0L);
                successCount++;
                logger.info("Successfully cancelled abandoned cart ID: {}", cartId);
            } catch (Exception e) {
                logger.error("Failed to cancel abandoned cart ID: " + cartId, e);
            }
        }

        logger.info("PendingCartCleanupTask finished. Cancelled {}/{} carts.", successCount, staleCartIds.size());
    }
}
