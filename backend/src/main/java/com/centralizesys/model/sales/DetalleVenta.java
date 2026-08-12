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
    private Double costoSnapshot; // Cost at time of sale (for profit calculations)

    private Long cantidad;

    private Double precioLista; // Original Price
    private Double descuentoValor; // The input value
    private String razonDescuento; // Reason for the specific discount

    private Double precioUnitario; // Final Price
    private Double subtotal;
    private Boolean anulado;
    private Long cantidadDevuelta; // Units already returned
}