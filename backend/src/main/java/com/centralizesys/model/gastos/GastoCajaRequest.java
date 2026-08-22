package com.centralizesys.model.gastos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class GastoCajaRequest {

    @NotNull(message = "El monto es obligatorio")
    @Positive(message = "El monto debe ser positivo")
    private Double monto = 0.0;

    public void setMonto(Double monto) {
        this.monto = (monto != null) ? monto : 0.0;
    }

    @NotBlank(message = "El motivo es obligatorio")
    private String motivo;

    // Si viene null, se usará el default asignado al instanciar
    private LocalDateTime fechaGasto = LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));

    public void setFechaGasto(LocalDateTime fechaGasto) {
        this.fechaGasto = (fechaGasto != null) ? fechaGasto : LocalDateTime.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    // Si viene null o vacío, se usará el nombre del usuario logueado
    private String personaInvolucrada;

    private String categoria;
}
