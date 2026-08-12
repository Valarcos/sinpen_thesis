package com.centralizesys.model.returns;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for POST /api/ventas/{id}/devolucion-parcial.
 * The authenticatedUserId is injected by the Controller from the JWT,
 * never trusted from the client payload (Zero-Trust).
 */
@Data
@NoArgsConstructor
public class DevolucionRequest {

    /**
     * Each item in this list specifies a line item (detalle_venta_id)
     * and how many units are being returned.
     */
    private List<DevolucionItemRequest> items;

    /**
     * How the refund should be applied.
     * 'SALDO'   → add to clientes.saldo_a_favor (default, safest).
     * 'EFECTIVO' → record a negative pagos_venta entry (direct cash refund).
     * Must be 'SALDO' when the sale has an active debt (enforced server-side).
     */
    private String tipoReembolso;

    /** Optional free-text note for the return record. */
    private String observaciones;

    // Injected by Controller, never deserialized from client JSON.
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long usuarioId;

    @Data
    @NoArgsConstructor
    public static class DevolucionItemRequest {
        /** ID of the detalles_venta row being partially/fully returned. */
        private Long detalleVentaId;

        /** Number of units being returned (must be > 0). */
        private Long cantidadDevuelta;
    }
}
