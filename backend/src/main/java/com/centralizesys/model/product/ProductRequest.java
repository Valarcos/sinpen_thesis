package com.centralizesys.model.product;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProductRequest {
    private String codigo;
    private String descripcion;
    private Double precioCosto = 0.0;
    private Double precioMayorista = 0.0;
    private Double precioMinorista = 0.0;

    public void setPrecioCosto(Double precioCosto) {
        this.precioCosto = (precioCosto != null) ? precioCosto : 0.0;
    }

    public void setPrecioMayorista(Double precioMayorista) {
        this.precioMayorista = (precioMayorista != null) ? precioMayorista : 0.0;
    }

    public void setPrecioMinorista(Double precioMinorista) {
        this.precioMinorista = (precioMinorista != null) ? precioMinorista : 0.0;
    }

    // For initial stock placement when creating a new product
    private Long ubicacionId;
    private Integer cantidad;
}