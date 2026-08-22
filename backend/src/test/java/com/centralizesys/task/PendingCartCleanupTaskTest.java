package com.centralizesys.task;

import com.centralizesys.BaseIntegrationTest;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.model.client.Cliente;
import com.centralizesys.repository.ClienteRepository;
import com.centralizesys.repository.VentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PendingCartCleanupTaskTest extends BaseIntegrationTest {

    @Autowired
    private PendingCartCleanupTask cleanupTask;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Test
    public void testCleanupStalePendingCarts() {
        // 1. Create a Client
        Cliente c = Cliente.builder()
                .nombre("Test Client")
                .telefono("12345678")
                .dni("123")
                .saldoAFavor(100.0)
                .activo(true)
                .build();
        Long clienteId = clienteRepository.save(c, 1L).getId();

        clienteRepository.deductSaldo(clienteId, 50.0);

        // Let's find the SALDO method ID.
        Long saldoMethodId = jdbcTemplate.queryForObject("SELECT id FROM metodos_pago WHERE acronimo = 'SALDO'", Long.class);

        Long userId = createTestUser();

        // 2. Create the "stale" cart using standard repo
        Venta staleVenta = new Venta();
        staleVenta.setFecha(LocalDateTime.now());
        staleVenta.setFechaCreacion(LocalDateTime.now());
        staleVenta.setTotalVenta(0.0);
        staleVenta.setDescuentoGlobal(0.0);
        staleVenta.setRecargoGlobal(0.0);
        staleVenta.setTipoVenta("MINORISTA");
        staleVenta.setEstado("PENDIENTE");
        staleVenta.setClienteId(clienteId);
        staleVenta.setClienteNombre("Test Client");
        staleVenta.setUsuarioId(userId);
        Long staleCartId = ventaRepository.saveVenta(staleVenta);

        // Backdate the stale cart manually to bypass auditing
        LocalDateTime staleDate = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")).minusHours(50);
        jdbcTemplate.update("UPDATE ventas SET fecha_creacion = ?, fecha = ? WHERE id = ?", staleDate, staleDate, staleCartId);

        // Insert a pending payment using SALDO
        jdbcTemplate.update("INSERT INTO pagos_venta (venta_id, metodo_pago_id, monto, usuario_id, anulado, fecha_pago) " +
                "VALUES (?, ?, ?, ?, false, CURRENT_TIMESTAMP)", staleCartId, saldoMethodId, 50.0, userId);

        // 3. Create a "fresh" cart using standard repo
        Venta freshVenta = new Venta();
        freshVenta.setFecha(LocalDateTime.now());
        freshVenta.setFechaCreacion(LocalDateTime.now());
        freshVenta.setTotalVenta(0.0);
        freshVenta.setDescuentoGlobal(0.0);
        freshVenta.setRecargoGlobal(0.0);
        freshVenta.setTipoVenta("MINORISTA");
        freshVenta.setEstado("PENDIENTE");
        freshVenta.setClienteId(clienteId);
        freshVenta.setClienteNombre("Test Client");
        freshVenta.setUsuarioId(userId);
        Long savedFreshId = ventaRepository.saveVenta(freshVenta);

        // 4. Invoke Cleanup
        cleanupTask.cleanupStalePendingCarts();

        // 5. Assertions
        Optional<Venta> staleAfter = ventaRepository.findById(staleCartId);
        assertTrue(staleAfter.isPresent());
        assertEquals("CANCELADA_PENDIENTE", staleAfter.get().getEstado());

        Optional<Venta> freshAfter = ventaRepository.findById(savedFreshId);
        assertTrue(freshAfter.isPresent());
        assertEquals("PENDIENTE", freshAfter.get().getEstado());

        // Verify Saldo a Favor was refunded (50 was refunded to the client)
        // Original saldo 100 -> deducted 50 -> should be 100 after refund
        Double currentSaldo = jdbcTemplate.queryForObject("SELECT saldo_a_favor FROM clientes WHERE id = ?", Double.class, clienteId);
        assertEquals(100.0, currentSaldo, 0.001);
    }
}
