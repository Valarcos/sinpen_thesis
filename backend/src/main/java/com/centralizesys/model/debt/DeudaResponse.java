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
    private Double montoDeuda; // The current remaining balance
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaDeuda; // dd-mm-YYYY
    private String estado; // PENDIENTE, PARCIAL, PAGADO
    private Double montoOriginal; // NEW: From ventas.total_venta
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaUltimoPago; // NEW: From deudores.fecha_pago
}