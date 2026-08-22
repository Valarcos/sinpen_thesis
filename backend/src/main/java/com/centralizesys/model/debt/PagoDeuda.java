package com.centralizesys.model.debt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagoDeuda {
    private Long id;
    private Long deudaId;
    private Long metodoPagoId;
    private Double monto = 0.0;

    public void setMonto(Double monto) {
        this.monto = (monto != null) ? monto : 0.0;
    }
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaPago;
    private String observaciones;
    private Long usuarioId;
    private Boolean anulado;

    // Helper field for reading human readable values from JOINs
    private String metodoPagoNombre;
    private String usuarioNombre;
}
