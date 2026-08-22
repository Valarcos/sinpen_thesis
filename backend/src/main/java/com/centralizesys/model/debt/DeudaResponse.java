package com.centralizesys.model.debt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeudaResponse {
    private Long id;
    private Long ventaId;
    private String clienteNombre;
    private Long clienteId;
    private Double montoDeuda = 0.0; // The current remaining balance
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaDeuda; // dd-mm-YYYY
    private String estado; // PENDIENTE, PARCIAL, PAGADO
    private Double montoOriginal = 0.0; // NEW: From ventas.total_venta
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaUltimoPago; // NEW: From deudores.fecha_pago

    public void setMontoDeuda(Double montoDeuda) {
        this.montoDeuda = (montoDeuda != null) ? montoDeuda : 0.0;
    }

    public void setMontoOriginal(Double montoOriginal) {
        this.montoOriginal = (montoOriginal != null) ? montoOriginal : 0.0;
    }
}