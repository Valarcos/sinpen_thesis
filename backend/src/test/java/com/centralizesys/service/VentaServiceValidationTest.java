package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.ResourceNotFoundException;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.TipoVenta;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.StockRepository;
import com.centralizesys.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class VentaServiceValidationTest {

    @Mock
    private VentaRepository ventaRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private DeudoresRepository deudoresRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private com.centralizesys.repository.AlertaChequeRepository alertaChequeRepository;
    @Mock
    private com.centralizesys.repository.MetodoPagoRepository metodoPagoRepository;
    @Mock
    private com.centralizesys.repository.ClienteRepository clienteRepository;
    @Mock
    private com.centralizesys.repository.DevolucionesRepository devolucionesRepository;

    @InjectMocks
    private VentaService ventaService;

    // --- GROUP 1: INPUT VALIDATION (Public API) ---

    @Test
    @DisplayName("UT-01: registrarVenta throws BusinessRuleException when items list is null")
    void registrarVenta_Throws_WhenItemsNull() {
        VentaRequest request = new VentaRequest();
        request.setItems(null);

        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-01 (Var): registrarVenta throws BusinessRuleException when items list is empty")
    void registrarVenta_Throws_WhenItemsEmpty() {
        VentaRequest request = new VentaRequest();
        request.setItems(Collections.emptyList());

        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-02: registrarVenta throws ResourceNotFoundException when product does not exist")
    void registrarVenta_Throws_WhenProductNotFound() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(99L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));

        when(productRepository.findByIdIncludingInactive(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> ventaService.registrarVenta(request));
    }

    // --- GROUP 6: SOFT-DELETE PRODUCT GUARD ---

    @Test
    @DisplayName("UT-23: processItems throws BusinessRuleException when product is logically deleted (activo=false)")
    void processItems_Throws_WhenProductIsInactive() {
        // Arrange: Product exists in DB but is soft-deleted
        Product deletedProduct = Product.builder().id(1L).codigo("OLD-CODE").descripcion("Producto Archivado")
                .precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0)
                .cantidadStock(0L).activo(false).build();

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        // Simulate defence-in-depth: the service receives the inactive product object
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(deletedProduct));

        List<VentaRequest.ItemRequest> items = List.of(item);

        // Act & Assert: must be BusinessRuleException, NOT ResourceNotFoundException
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> ventaService.processItems(items, TipoVenta.MINORISTA));

        assertTrue(ex.getMessage().contains("eliminado"),
                "Error message must indicate the product is archived, not missing");
        assertTrue(ex.getMessage().contains("Producto Archivado"),
                "Error message must include the product's description for user clarity");
    }

    // --- PHASE 2.1 MATHEMATICAL EXPLOITS ---

    @Test
    @DisplayName("UT-24: processItems throws BusinessRuleException when item quantity is less than or equal to zero")
    void processItems_Throws_WhenQuantityIsNegative() {
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(-5L); // Negative quantity

        List<VentaRequest.ItemRequest> items = List.of(item);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> ventaService.processItems(items, TipoVenta.MINORISTA));

        assertTrue(ex.getMessage().toLowerCase().contains("cero") || ex.getMessage().toLowerCase().contains("positiva") || ex.getMessage().toLowerCase().contains("inválida") || ex.getMessage().toLowerCase().contains("negativ"),
                "Error message must indicate the quantity is invalid");
    }

    @Test
    @DisplayName("UT-25: cobrarCheque throws BusinessRuleException when overpaying debt")
    void cobrarCheque_Throws_WhenOverpayingDebt() {
        com.centralizesys.model.cheque.AlertaCheque cheque = new com.centralizesys.model.cheque.AlertaCheque(1L, 100L, 5000.0, LocalDate.now(), "PENDIENTE", null, null);
        cheque.setTipoOrigen("DEUDA_FIADO");

        com.centralizesys.model.sales.MetodoPago metodo = new com.centralizesys.model.sales.MetodoPago();
        metodo.setId(1L);
        metodo.setActivo(true);

        com.centralizesys.model.debt.DeudaResponse deuda = new com.centralizesys.model.debt.DeudaResponse();
        deuda.setId(100L);
        deuda.setMontoDeuda(4000.0); // Debt is 4000, Cheque is 5000

        when(alertaChequeRepository.findById(1L)).thenReturn(Optional.of(cheque));
        when(metodoPagoRepository.findById(1L)).thenReturn(Optional.of(metodo));
        when(deudoresRepository.findByVentaId(100L)).thenReturn(Optional.of(deuda));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> ventaService.cobrarCheque(1L, 1L, 1L));

        assertTrue(ex.getMessage().toLowerCase().contains("supera") || ex.getMessage().toLowerCase().contains("mayor"),
                "Error message must indicate overpayment");
    }

    // --- PHASE 2.2 STATE MACHINE & INJECTION EXPLOITS ---

    @Test
    @DisplayName("UT-28: registrarVenta throws BusinessRuleException when client is logically deleted (activo=false)")
    void registrarVenta_Throws_WhenClienteIsInactive() {
        // Arrange
        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setClienteId(5L); // Specific client ID injected

        com.centralizesys.model.client.Cliente inactiveClient = new com.centralizesys.model.client.Cliente();
        inactiveClient.setId(5L);
        inactiveClient.setActivo(false); // Inactive client

        when(clienteRepository.findById(5L)).thenReturn(Optional.of(inactiveClient));

        // Act & Assert
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));

        assertTrue(ex.getMessage().toLowerCase().contains("inactivo") || ex.getMessage().toLowerCase().contains("eliminado") || ex.getMessage().toLowerCase().contains("activo"),
                "Error message must indicate that the client is not active");
    }
}
