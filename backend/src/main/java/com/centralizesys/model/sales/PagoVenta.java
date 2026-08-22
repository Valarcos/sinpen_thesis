package com.centralizesys.model.sales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagoVenta {
    private Long id;
    private Long ventaId;
    private Long metodoPagoId; // Links to 'metodos_pago' table
    private Double monto = 0.0;
    private LocalDateTime fechaPago;
    private Boolean anulado;
    private Long usuarioId;

    public void setMonto(Double monto) {
        this.monto = (monto != null) ? monto : 0.0;
    }
}