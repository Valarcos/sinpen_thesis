package com.centralizesys.model.gastos;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GastoCajaAnulacionRequest {
    private String razonAnulacion = "";

    public void setRazonAnulacion(String razonAnulacion) {
        this.razonAnulacion = (razonAnulacion != null) ? razonAnulacion : "";
    }
}
