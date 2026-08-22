package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.model.returns.DevolucionRequest;
import com.centralizesys.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DevolucionesConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private VentaRepository ventaRepository;

    @Test
    @DisplayName("Vector 8: Concurrent registrarDevolucionParcial should not double-refund")
    void testConcurrentDevolucionParcial() throws InterruptedException {
        // 1. Setup Data
        Long userId = createTestUser();
        Long prodId = createTestProduct("CONC-DEV", 100.0, 10L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Conc Client");
        request.setUsuarioId(userId);

        VentaRequest.ItemRequest itemReq = new VentaRequest.ItemRequest();
        itemReq.setProductoId(prodId);
        itemReq.setCantidad(2L); // Total quantity is 2
        request.setItems(List.of(itemReq));

        Long pendingId = ventaService.crearPendiente(request, userId);

        PagoDeudaRequest pago = new PagoDeudaRequest();
        pago.setMetodoPagoId(1L);
        pago.setMontoPago(200.0);
        ventaService.registrarPago(pendingId, List.of(pago), userId);

        VentaResponse activaSale = ventaService.finalizarVenta(pendingId, userId);
        Long detalleId = activaSale.getItems().get(0).getId();

        // 2. Setup Concurrency
        int threads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // wait until all threads are ready

                    DevolucionRequest.DevolucionItemRequest devItem = new DevolucionRequest.DevolucionItemRequest();
                    devItem.setDetalleVentaId(detalleId);
                    devItem.setCantidadDevuelta(2L); // Trying to return all 2 units

                    DevolucionRequest devReq = new DevolucionRequest();
                    devReq.setItems(List.of(devItem));
                    devReq.setTipoReembolso("SALDO");
                    devReq.setUsuarioId(userId);

                    ventaService.registrarDevolucionParcial(activaSale.getId(), devReq, userId);
                    successCount.incrementAndGet();
                } catch (BusinessRuleException e) {
                    exceptionCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }

        // 3. Fire all threads simultaneously
        startLatch.countDown();
        done.await(5, TimeUnit.SECONDS);

        // 4. Verification
        // Only one thread should succeed, as returning 2 when 2 are available leaves 0 available.
        // The other 2 threads should fail with BusinessRuleException ("No se puede devolver más cantidad de la comprada")
        assertEquals(1, successCount.get(), "Only one refund should succeed");
        assertEquals(threads - 1, exceptionCount.get(), "Other refunds should fail due to pessimistic locking");
    }
}
