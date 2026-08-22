package com.centralizesys.model.purchase;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CompraItemRequest {
    private Long productoId;
    private Long cantidad;
    private Double costoUnitario = 0.0;
    private Long ubicacionId;       // We need to know WHERE this stock is entering (e.g., "Depósito" or "Local")

    public void setCostoUnitario(Double costoUnitario) {
        this.costoUnitario = (costoUnitario != null) ? costoUnitario : 0.0;
    }
}