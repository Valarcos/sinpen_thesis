package com.centralizesys.model.gastos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GastoCaja {
    private Long id;
    private Double monto = 0.0;

    public void setMonto(Double monto) {
        this.monto = (monto != null) ? monto : 0.0;
    }
    private String motivo;
    private LocalDateTime fechaGasto;
    private LocalDateTime fechaRegistro;
    private String personaInvolucrada;
    private Long registradoPorUsuarioId;
    private String registradoPorUsuarioNombre; // Transient field for UI
    private String categoria;
    private Boolean anulado;
    private String razonAnulacion;
}
