package com.centralizesys.model.purchase;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DetalleCompra {
    private Long id;
    private Long compraId;
    private Long productoId;
    private Long cantidad;
    private Double costoUnitario = 0.0;  // Cost at the moment of purchase
    private Double subtotal = 0.0;

    public void setCostoUnitario(Double costoUnitario) {
        this.costoUnitario = (costoUnitario != null) ? costoUnitario : 0.0;
    }

    public void setSubtotal(Double subtotal) {
        this.subtotal = (subtotal != null) ? subtotal : 0.0;
    }
}