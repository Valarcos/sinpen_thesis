package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.debt.DeudaResponse;
import com.centralizesys.model.debt.PagoDeuda;
import com.centralizesys.model.debt.PagoDeudaRequest;
import com.centralizesys.repository.DeudoresRepository;
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
class DeudoresConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private DeudoresService deudoresService;

    @Autowired
    private DeudoresRepository deudoresRepository;

    @Test
    @DisplayName("Vector 30: Concurrent anularPago should not double-increment debt")
    void testConcurrentAnularPago() throws InterruptedException {
        // 1. Setup Data
        Long userId = createTestUser();
        Long prodId = createTestProduct("CONC-PAGO", 100.0, 10L);

        // Create Sale with Debt
        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Conc Client");
        request.setUsuarioId(userId);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(prodId);
        item.setCantidad(5L); // $500 total
        request.setItems(List.of(item));

        Long pendingId = ventaService.crearPendiente(request, userId);

        // Pay only $100 -> Debt = $400
        PagoDeudaRequest pago = new PagoDeudaRequest();
        pago.setMetodoPagoId(1L);
        pago.setMontoPago(100.0);
        ventaService.registrarPago(pendingId, List.of(pago), userId);
        ventaService.finalizarVenta(pendingId, userId);

        // Find the Debt and its Payment
        DeudaResponse deuda = deudoresService.getAll().stream().filter(d -> d.getMontoOriginal() == 500.0).findFirst().orElseThrow();
        Long deudaId = deuda.getId();

        // Add another payment for the debt
        PagoDeudaRequest pago2 = new PagoDeudaRequest();
        pago2.setMetodoPagoId(1L);
        pago2.setMontoPago(100.0);
        deudoresService.registrarPago(deudaId, List.of(pago2), userId);

        List<PagoDeuda> pagos = deudoresService.getPagos(deudaId);
        Long pagoId = pagos.get(0).getId();

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
                    startLatch.await();
                    deudoresService.anularPago(pagoId);
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

        // 3. Fire
        startLatch.countDown();
        done.await(5, TimeUnit.SECONDS);

        // 4. Verify
        assertEquals(1, successCount.get(), "Only one thread should succeed annulling the payment");
        assertEquals(threads - 1, exceptionCount.get(), "Other threads should fail");

        // Initial debt was $400. We paid $100 -> debt=$300. We void the $100 payment -> debt should be back to $400.
        // If it double incremented, it would be $500.
        DeudaResponse updatedDeuda = deudoresRepository.findById(deudaId).orElseThrow();
        assertEquals(400.0, updatedDeuda.getMontoDeuda(), 0.01, "Debt amount should be accurately restored once");
    }
}
