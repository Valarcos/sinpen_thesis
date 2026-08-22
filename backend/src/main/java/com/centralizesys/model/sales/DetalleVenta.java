package com.centralizesys.model.sales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleVenta {
    private Long id;
    private Long ventaId;
    private Long productoId;

    private String codigoSnapshot;
    private String descripcionSnapshot;
    private Double costoSnapshot = 0.0; // Cost at time of sale (for profit calculations)

    private Long cantidad;

    private Double precioLista = 0.0; // Original Price
    private Double descuentoValor = 0.0; // The input value
    private String razonDescuento; // Reason for the specific discount

    private Double precioUnitario = 0.0; // Final Price
    private Double subtotal = 0.0;
    private Boolean anulado;
    private Long cantidadDevuelta; // Units already returned

    public void setCostoSnapshot(Double costoSnapshot) {
        this.costoSnapshot = (costoSnapshot != null) ? costoSnapshot : 0.0;
    }

    public void setPrecioLista(Double precioLista) {
        this.precioLista = (precioLista != null) ? precioLista : 0.0;
    }

    public void setDescuentoValor(Double descuentoValor) {
        this.descuentoValor = (descuentoValor != null) ? descuentoValor : 0.0;
    }

    public void setPrecioUnitario(Double precioUnitario) {
        this.precioUnitario = (precioUnitario != null) ? precioUnitario : 0.0;
    }

    public void setSubtotal(Double subtotal) {
        this.subtotal = (subtotal != null) ? subtotal : 0.0;
    }
}