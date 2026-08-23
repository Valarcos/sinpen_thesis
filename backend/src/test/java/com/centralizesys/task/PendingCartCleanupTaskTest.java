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

//TODO: This test class has disabled tests due to a cron job that is not yet properly managed and implemented.
public class PendingCartCleanupTaskTest extends BaseIntegrationTest {

    @Autowired
    private PendingCartCleanupTask cleanupTask;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @org.junit.jupiter.api.Disabled("Disabled because the PendingCartCleanupTask logic was disabled per user request")
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

        // 2. Create a "stale" cart WITHOUT payments (Should be cancelled)
        Venta staleEmptyCart = new Venta();
        staleEmptyCart.setFecha(LocalDateTime.now());
        staleEmptyCart.setFechaCreacion(LocalDateTime.now());
        staleEmptyCart.setTotalVenta(0.0);
        staleEmptyCart.setTipoVenta("MINORISTA");
        staleEmptyCart.setEstado("PENDIENTE");
        staleEmptyCart.setClienteId(clienteId);
        staleEmptyCart.setUsuarioId(userId);
        Long staleEmptyCartId = ventaRepository.saveVenta(staleEmptyCart);

        // 3. Create a "stale" cart WITH payments (Legitimate Pedido - Should NOT be cancelled)
        Venta stalePedido = new Venta();
        stalePedido.setFecha(LocalDateTime.now());
        stalePedido.setFechaCreacion(LocalDateTime.now());
        stalePedido.setTotalVenta(0.0);
        stalePedido.setTipoVenta("MINORISTA");
        stalePedido.setEstado("PENDIENTE");
        stalePedido.setClienteId(clienteId);
        stalePedido.setUsuarioId(userId);
        Long stalePedidoId = ventaRepository.saveVenta(stalePedido);

        // Backdate both stale carts manually
        LocalDateTime staleDate = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")).minusHours(50);
        jdbcTemplate.update("UPDATE ventas SET fecha_creacion = ?, fecha = ? WHERE id IN (?, ?)", staleDate, staleDate, staleEmptyCartId, stalePedidoId);

        // Insert a pending payment using SALDO for the stalePedido
        jdbcTemplate.update("INSERT INTO pagos_venta (venta_id, metodo_pago_id, monto, usuario_id, anulado, fecha_pago) " +
                "VALUES (?, ?, ?, ?, false, CURRENT_TIMESTAMP)", stalePedidoId, saldoMethodId, 50.0, userId);

        // 4. Create a "fresh" cart using standard repo
        Venta freshVenta = new Venta();
        freshVenta.setFecha(LocalDateTime.now());
        freshVenta.setFechaCreacion(LocalDateTime.now());
        freshVenta.setTotalVenta(0.0);
        freshVenta.setTipoVenta("MINORISTA");
        freshVenta.setEstado("PENDIENTE");
        freshVenta.setClienteId(clienteId);
        freshVenta.setUsuarioId(userId);
        Long savedFreshId = ventaRepository.saveVenta(freshVenta);

        // 5. Invoke Cleanup
        cleanupTask.cleanupStalePendingCarts();

        // 6. Assertions
        Optional<Venta> staleEmptyAfter = ventaRepository.findById(staleEmptyCartId);
        assertTrue(staleEmptyAfter.isPresent());
        assertEquals("CANCELADA_PENDIENTE", staleEmptyAfter.get().getEstado());

        Optional<Venta> stalePedidoAfter = ventaRepository.findById(stalePedidoId);
        assertTrue(stalePedidoAfter.isPresent());
        assertEquals("PENDIENTE", stalePedidoAfter.get().getEstado());

        Optional<Venta> freshAfter = ventaRepository.findById(savedFreshId);
        assertTrue(freshAfter.isPresent());
        assertEquals("PENDIENTE", freshAfter.get().getEstado());

        // Verify Saldo a Favor was NOT refunded (since the pedido was kept as valid)
        Double currentSaldo = jdbcTemplate.queryForObject("SELECT saldo_a_favor FROM clientes WHERE id = ?", Double.class, clienteId);
        assertEquals(50.0, currentSaldo, 0.001);
    }
}
