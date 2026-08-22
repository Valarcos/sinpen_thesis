package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.sales.DetalleVenta;
import com.centralizesys.model.sales.VentaRequest;
import com.centralizesys.model.sales.VentaResponse;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class VentaServicePricingTest {

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


    // --- GROUP 2: PRICING & MATH LOGIC (Package-Private Testing) ---

    @Test
    @DisplayName("UT-03: processItems calculates discount correctly (Base - Discount)")
    void processItems_CalculatesDiscount_Correctly() {
        // Arrange
        Product product = Product.builder().codigo("A").descripcion("Test Product").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        product.setId(1L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(2L);
        item.setValorDescuento(10.0); // 100 - 10 = 90

        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(product));

        // Act (Calling package-private method directly)
        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        // Assert
        assertEquals(180.0, result.getTotalVenta()); // 90 * 2
        DetalleVenta detail = result.getDetalles().getFirst();
        assertEquals(90.0, detail.getPrecioUnitario());
        assertEquals(10.0, detail.getDescuentoValor());
    }

    @Test
    @DisplayName("UT-16: processItems handles Zero or Null discount correctly")
    void processItems_HandlesZeroDiscount() {
        // Arrange
        Product product = Product.builder().codigo("A").descripcion("Test Product").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        product.setId(1L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);
        item.setValorDescuento(null); // Should be treated as 0.0

        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(product));

        // Act
        var result = ventaService.processItems(List.of(item), TipoVenta.MINORISTA);

        // Assert
        assertEquals(100.0, result.getTotalVenta());
        assertEquals(100.0, result.getDetalles().getFirst().getPrecioUnitario());
    }

    @Test
    @DisplayName("UT-04: processItems throws when discount is greater than price")
    void processItems_Throws_WhenDiscountExceedsPrice() {
        Product product = Product.builder().codigo("A").descripcion("Test").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        product.setId(1L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);
        item.setValorDescuento(101.0); // Exceeds 100

        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(product));

        List<VentaRequest.ItemRequest> items = List.of(item);

        assertThrows(BusinessRuleException.class, () -> ventaService.processItems(items, TipoVenta.MINORISTA));
    }

    @Test
    @DisplayName("UT-05: processItems throws when discount is negative")
    void processItems_Throws_WhenDiscountIsNegative() {
        Product product = Product.builder().codigo("A").descripcion("Test").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        product.setId(1L);

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);
        item.setValorDescuento(-10.0);

        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(product));

        List<VentaRequest.ItemRequest> items = List.of(item);

        assertThrows(BusinessRuleException.class, () -> ventaService.processItems(items, TipoVenta.MINORISTA));
    }

    @Test
    @DisplayName("UT-06: processItems rounds total to two decimals")
    void processItems_RoundsTotal_ToTwoDecimals() {
        // Scenario: 3 items at 33.3333333...
        // We simulate this by having 1 product with a weird calculated price
        // OR simply 3 distinct items that sum up weirdly.
        // Let's use 1 item with quantity 1 and a calculated price that requires
        // rounding.
        // Wait, the logic is: Math.round(totalAcumulado * 100.0) / 100.0

        Product p1 = Product.builder().codigo("A").descripcion("P1").precioCosto(10.0).precioMayorista(10.0).precioMinorista(10.555).build(); // DB stores double
        p1.setId(1L);

        VentaRequest.ItemRequest i1 = new VentaRequest.ItemRequest();
        i1.setProductoId(1L);
        i1.setCantidad(1L);
        i1.setValorDescuento(0.0);

        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p1));

        // Act
        var result = ventaService.processItems(List.of(i1), TipoVenta.MINORISTA);

        // Assert
        // 10.555 rounded should be 10.56
        assertEquals(10.56, result.getTotalVenta());
    }

    @Test
    @DisplayName("UT-06B: processItems uses Wholesale Price when TipoVenta is MAYORISTA")
    void processItems_UsesWholesalePrice() {
        // Arrange
        Product p = Product.builder().codigo("A").descripcion("P1").precioCosto(50.0).precioMayorista(100.0).precioMinorista(150.0).build(); // Cost, Wholesale, Retail
        p.setId(1L);

        VentaRequest.ItemRequest i1 = new VentaRequest.ItemRequest();
        i1.setProductoId(1L);
        i1.setCantidad(2L);

        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));

        // Act
        var result = ventaService.processItems(List.of(i1), TipoVenta.MAYORISTA);

        // Assert: 2 * 100.0 (Wholesale) = 200.0
        // If it used Retail, it would be 2 * 150.0 = 300.0
        assertEquals(200.0, result.getTotalVenta());
        assertEquals(100.0, result.getDetalles().getFirst().getPrecioUnitario());
    }

    // --- GLOBAL DISCOUNT TESTS ---

    @Test
    @DisplayName("UT-17: registrarVenta applies global discount correctly")
    void registrarVenta_AppliesGlobalDiscount() {
        // Arrange
        Product p = Product.builder().codigo("A").descripcion("P1").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));
        // Mock stock logic to avoid NPE
        when(stockRepository.findByProductId(anyLong())).thenReturn(List.of());
        when(ventaRepository.saveVenta(any())).thenReturn(1L);
        when(ventaRepository.findVendedorNombre(any())).thenReturn("Vendedora Test");

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(2L); // Subtotal: 200
        item.setValorDescuento(0.0);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setDescuentoGlobal(50.0); // 200 - 50 = 150
        request.setClienteNombre("Discount User");
        request.setUsuarioId(1L);

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertEquals(150.0, response.getTotalVenta());
        assertEquals(50.0, response.getDescuentoGlobal());
        assertEquals("Vendedora Test", response.getVendedorNombre());
    }

    @Test
    @DisplayName("UT-18: registrarVenta throws when global discount is negative")
    void registrarVenta_Throws_WhenGlobalDiscountNegative() {
        // Arrange
        Product p = Product.builder().codigo("A").descripcion("P1").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setDescuentoGlobal(-10.0);

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-19: registrarVenta throws when global discount exceeds subtotal")
    void registrarVenta_Throws_WhenGlobalDiscountExceedsTotal() {
        // Arrange
        Product p = Product.builder().codigo("A").descripcion("P1").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L); // Subtotal 100

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setDescuentoGlobal(101.0);

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    // --- GLOBAL SURCHARGE TESTS (Recargo Global) ---

    @Test
    @DisplayName("UT-20: registrarVenta applies global surcharge correctly (Total = Subtotal - Desc + Rec)")
    void registrarVenta_AppliesGlobalSurcharge() {
        // Arrange
        Product p = Product.builder().codigo("A").descripcion("P1").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));
        when(stockRepository.findByProductId(anyLong())).thenReturn(List.of());
        when(ventaRepository.saveVenta(any())).thenReturn(1L);
        when(ventaRepository.findVendedorNombre(any())).thenReturn("Vendedora Test");

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(2L); // Subtotal: 200
        item.setValorDescuento(0.0);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setRecargoGlobal(20.0); // 200 + 20 = 220
        request.setClienteNombre("Surcharge User");
        request.setUsuarioId(1L);

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertEquals(220.0, response.getTotalVenta());
        assertEquals(0.0, response.getDescuentoGlobal());
        assertEquals(20.0, response.getRecargoGlobal());
    }

    @Test
    @DisplayName("UT-21: registrarVenta applies both discount and surcharge correctly (Total = Sub - Desc + Rec)")
    void registrarVenta_AppliesDiscountAndSurcharge_Together() {
        // Arrange: Subtotal=200, Discount=30, Surcharge=10 -> Total = 200 - 30 + 10 = 180
        Product p = Product.builder().codigo("A").descripcion("P1").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));
        when(stockRepository.findByProductId(anyLong())).thenReturn(List.of());
        when(ventaRepository.saveVenta(any())).thenReturn(1L);
        when(ventaRepository.findVendedorNombre(any())).thenReturn("Vendedora Test");

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(2L);
        item.setValorDescuento(0.0);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setDescuentoGlobal(30.0);
        request.setRecargoGlobal(10.0);
        request.setClienteNombre("Mixed Modifier User");
        request.setUsuarioId(1L);

        // Act
        VentaResponse response = ventaService.registrarVenta(request);

        // Assert
        assertEquals(180.0, response.getTotalVenta()); // 200 - 30 + 10
        assertEquals(30.0, response.getDescuentoGlobal());
        assertEquals(10.0, response.getRecargoGlobal());
    }

    @Test
    @DisplayName("UT-22: registrarVenta throws when global surcharge is negative (adversarial)")
    void registrarVenta_Throws_WhenGlobalSurchargeNegative() {
        // Arrange
        Product p = Product.builder().codigo("A").descripcion("P1").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setRecargoGlobal(-100.0); // Adversarial: negative surcharge = uncontrolled discount bypass

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-23: registrarVenta throws when global surcharge is NaN (adversarial payload injection)")
    void registrarVenta_Throws_WhenGlobalSurchargeIsNaN() {
        // Arrange: simulates a raw HTTP payload injecting NaN, bypassing frontend validation
        Product p = Product.builder().codigo("A").descripcion("P1").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setRecargoGlobal(Double.NaN);

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-24: registrarVenta throws when global surcharge is Infinity (adversarial payload injection)")
    void registrarVenta_Throws_WhenGlobalSurchargeIsInfinity() {
        // Arrange: simulates a raw HTTP payload injecting Infinity
        Product p = Product.builder().codigo("A").descripcion("P1").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setRecargoGlobal(Double.POSITIVE_INFINITY);

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-25: registrarVenta throws when global discount is NaN (adversarial payload injection)")
    void registrarVenta_Throws_WhenGlobalDiscountIsNaN() {
        // Arrange
        Product p = Product.builder().codigo("A").descripcion("P1").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setDescuentoGlobal(Double.NaN); // Adversarial: NaN discount

        // Act & Assert
        assertThrows(BusinessRuleException.class, () -> ventaService.registrarVenta(request));
    }

    @Test
    @DisplayName("UT-26: registrarVenta defaults null recargoGlobal to 0.0 without throwing")
    void registrarVenta_DefaultsNullRecargoGlobal_ToZero() {
        // Arrange: recargoGlobal omitted from payload (null) - must not throw NPE
        Product p = Product.builder().codigo("A").descripcion("P1").precioCosto(50.0).precioMayorista(80.0).precioMinorista(100.0).build();
        p.setId(1L);
        when(productRepository.findByIdIncludingInactive(1L)).thenReturn(Optional.of(p));
        when(stockRepository.findByProductId(anyLong())).thenReturn(List.of());
        when(ventaRepository.saveVenta(any())).thenReturn(1L);
        when(ventaRepository.findVendedorNombre(any())).thenReturn("Vendedora Test");

        VentaRequest.ItemRequest item = new VentaRequest.ItemRequest();
        item.setProductoId(1L);
        item.setCantidad(1L);
        item.setValorDescuento(0.0);

        VentaRequest request = new VentaRequest();
        request.setItems(List.of(item));
        request.setRecargoGlobal(null); // Omitted in payload -> must default to 0.0
        request.setClienteNombre("No Surcharge User");
        request.setUsuarioId(1L);

        // Act & Assert: must not throw, total must equal subtotal
        VentaResponse response = ventaService.registrarVenta(request);
        assertEquals(100.0, response.getTotalVenta());
        assertEquals(0.0, response.getRecargoGlobal());
    }
}
