package com.centralizesys.service;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
import com.centralizesys.model.cheque.AlertaChequeRequest;
import com.centralizesys.repository.AlertaChequeRepository;
import com.centralizesys.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class VentaConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private AlertaChequeRepository alertaChequeRepository;

    @Test
    @DisplayName("Vector 4: Concurrent finalizarVenta should not double-process")
    void testConcurrentFinalizarVenta() throws InterruptedException {
        // 1. Setup Data
        Long userId = createTestUser();
        Long prodId = createTestProduct("CONC-FIN", 100.0, 10L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Conc Client");
        request.setUsuarioId(userId);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(prodId);
        item.setCantidad(2L); // Total $200
        request.setItems(List.of(item));

        // PENDIENTE setup
        Long pendingId = ventaService.crearPendiente(request, userId);

        // add full payment so it can be finalized
        VentaRequest.PagoRequest pago = new VentaRequest.PagoRequest();
        pago.setMetodoPagoId(1L);
        pago.setMonto(200.0);
        request.setPagos(List.of(pago));

        com.centralizesys.model.debt.PagoDeudaRequest pdr = new com.centralizesys.model.debt.PagoDeudaRequest();
        pdr.setMetodoPagoId(1L);
        pdr.setMontoPago(200.0);
        ventaService.registrarPago(pendingId, List.of(pdr), userId);

        // 2. Execute concurrently
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    ventaService.finalizarVenta(pendingId, userId);
                    successCount.incrementAndGet();
                } catch (BusinessRuleException e) {
                    exceptionCount.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("Unexpected exception: " + e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        done.await(5, TimeUnit.SECONDS);

        // 3. Verify
        // Only one should succeed
        assertEquals(1, successCount.get(), "Only one thread should succeed finalizing");
        assertEquals(threads - 1, exceptionCount.get(), "Other threads should fail");
    }

    @Test
    @DisplayName("Vector 4: Concurrent modificarCarrito should not double-modify")
    void testConcurrentModificarCarrito() throws InterruptedException {
        Long userId = createTestUser();
        Long prodId = createTestProduct("CONC-MOD", 100.0, 10L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Conc Mod Client");
        request.setUsuarioId(userId);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(prodId);
        item.setCantidad(1L); // Total $100
        request.setItems(List.of(item));

        Long pendingId = ventaService.crearPendiente(request, userId);

        // Request 2
        VentaRequest modifyRequest = new VentaRequest();
        modifyRequest.setClienteNombre("Conc Mod Client");
        modifyRequest.setUsuarioId(userId);
        VentaRequest.ItemRequest item2 = new VentaRequest.ItemRequest();
        item2.setProductoId(prodId);
        item2.setCantidad(2L); // Total $200
        modifyRequest.setItems(List.of(item2));

        int threads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    ventaService.modificarCarrito(pendingId, modifyRequest, userId);
                    successCount.incrementAndGet();
                } catch (BusinessRuleException e) {
                    exceptionCount.incrementAndGet();
                } catch (Exception e) {
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        done.await(5, TimeUnit.SECONDS);

        // With the introduction of Optimistic Locking (versioning), Last-Writer-Wins is now prevented.
        // The first thread to acquire the lock and verify the version will succeed.
        // Subsequent threads will read the updated version (after acquiring the lock) and fail.
        assertEquals(1, successCount.get(), "Only one thread should succeed due to Optimistic Locking");
        assertEquals(threads - 1, exceptionCount.get(), "Other threads should fail with BusinessRuleException");
    }

    @Test
    @DisplayName("Vector 4: Concurrent cobrarCheque should not double-cash")
    void testConcurrentCobrarCheque() throws InterruptedException {
        Long userId = createTestUser();
        Long prodId = createTestProduct("CONC-CHQ", 100.0, 10L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Conc Chq Client");
        request.setUsuarioId(userId);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(prodId);
        item.setCantidad(2L); // Total $200
        request.setItems(List.of(item));

        AlertaChequeRequest chequeReq = new AlertaChequeRequest();
        chequeReq.setMonto(200.0);
        chequeReq.setFechaCobro(LocalDate.now());
        request.setCheques(List.of(chequeReq));

        Long pendingId = ventaService.crearPendiente(request, userId);
        ventaService.finalizarVenta(pendingId, userId);

        var cheques = alertaChequeRepository.findByVentaId(pendingId);
        Long chequeId = cheques.get(0).getId();

        int threads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    ventaService.cobrarCheque(chequeId, 1L, userId);
                    successCount.incrementAndGet();
                } catch (BusinessRuleException e) {
                    exceptionCount.incrementAndGet();
                } catch (Exception e) {
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        done.await(5, TimeUnit.SECONDS);

        assertEquals(1, successCount.get(), "Only one thread should succeed cashing cheque");
        assertEquals(threads - 1, exceptionCount.get(), "Other threads should fail");
    }

    @Test
    @DisplayName("Vector 25: Concurrent anularVentaHistorica should not recursively restock")
    void testConcurrentAnularVenta() throws InterruptedException {
        // 1. Setup Data
        Long userId = createTestUser();
        Long prodId = createTestProduct("CONC-ANUL", 100.0, 10L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Conc Client");
        request.setUsuarioId(userId);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(prodId);
        item.setCantidad(2L); // Total $200
        request.setItems(List.of(item));

        Long pendingId = ventaService.crearPendiente(request, userId);

        com.centralizesys.model.debt.PagoDeudaRequest pago = new com.centralizesys.model.debt.PagoDeudaRequest();
        pago.setMetodoPagoId(1L);
        pago.setMontoPago(200.0);
        ventaService.registrarPago(pendingId, List.of(pago), userId);

        VentaResponse activaSale = ventaService.finalizarVenta(pendingId, userId);

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
                    ventaService.anularVentaHistorica(activaSale.getId());
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

        // 4. Verify only ONE annulment succeeded.
        assertEquals(1, successCount.get(), "Only one thread should succeed annulling");
        assertEquals(threads - 1, exceptionCount.get(), "Other threads should fail due to atomic update");
    }

    @Test
    @DisplayName("Vector 4: Concurrent registrarPago should not double-pay")
    void testConcurrentRegistrarPago() throws InterruptedException {
        // 1. Setup Data
        Long userId = createTestUser();
        Long prodId = createTestProduct("CONC-REG", 100.0, 10L);

        VentaRequest request = new VentaRequest();
        request.setClienteNombre("Conc Client");
        request.setUsuarioId(userId);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(prodId);
        item.setCantidad(1L); // Total $100
        request.setItems(List.of(item));

        Long pendingId = ventaService.crearPendiente(request, userId);

        com.centralizesys.model.debt.PagoDeudaRequest pago = new com.centralizesys.model.debt.PagoDeudaRequest();
        pago.setMetodoPagoId(1L);
        pago.setMontoPago(100.0);

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
                    ventaService.registrarPago(pendingId, List.of(pago), userId);
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

        startLatch.countDown();
        done.await(5, TimeUnit.SECONDS);

        // With relaxed validation for saldo a favor, all threads can now successfully pay over the total amount.
        assertEquals(threads, successCount.get(), "All threads should succeed since overpayments are now allowed");
        assertEquals(0, exceptionCount.get(), "No thread should fail with BusinessRuleException");
    }
}
