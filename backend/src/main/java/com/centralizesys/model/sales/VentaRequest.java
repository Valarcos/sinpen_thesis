package com.centralizesys.model.sales;

import com.centralizesys.model.cheque.AlertaChequeRequest;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class VentaRequest {
    private String clienteNombre;
    private Double descuentoGlobal = 0.0; // NEW

    // NOTE: This field is ALWAYS overridden by VentaController using
    // SecurityUtils.getAuthenticatedUserId().
    // Any value sent from the client in the request body is discarded. Do NOT trust
    // client-supplied identity.
    private Long usuarioId;
    private TipoVenta tipoVenta = TipoVenta.MINORISTA; // Default to Retail

    // The list of products being bought
    private List<ItemRequest> items;

    // The list of payment methods used (e.g. $500 Cash + $200 Card)
    private List<PagoRequest> pagos;

    // The list of cheques if the sale is paid with cheques
    private List<AlertaChequeRequest> cheques;

    // Nested static classes for the inner lists
    @Data
    @NoArgsConstructor
    public static class ItemRequest {
        private Long productoId;
        private Long cantidad;

        // We removed explicit 'precioUnitario' input preference.
        // Now the system calculates it, OR the user overrides it via discounts.
        // If the user wants to manually type a final price, they can use FIXED discount
        // calculating the difference, or we can keep 'precioManual' as an override.
        // For this rule, we stick to Discount Logic:

        private Double valorDescuento = 0.0;
        private String razonDescuento;
    }

    @Data
    @NoArgsConstructor
    public static class PagoRequest {
        private Long metodoPagoId; // ID from metodos_pago table
        private Double monto;
    }
}