package com.centralizesys.model.cheque;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertaCheque {
    private Long id;
    private Long ventaId;
    private Double monto = 0.0;
    private LocalDate fechaCobro;
    private String estado;
    private Long pagoVentaId;

    private String tipoOrigen;
    private Long pagoDeudaId;

    // Transient field for UI display
    private String metodoPagoNombre;

    public void setMonto(Double monto) {
        this.monto = (monto != null) ? monto : 0.0;
    }

    public AlertaCheque(Long id, Long ventaId, Double monto, LocalDate fechaCobro, String estado, Long pagoVentaId, String metodoPagoNombre) {
        this.id = id;
        this.ventaId = ventaId;
        this.monto = (monto != null) ? monto : 0.0;
        this.fechaCobro = fechaCobro;
        this.estado = estado;
        this.pagoVentaId = pagoVentaId;
        this.tipoOrigen = "VENTA";
        this.pagoDeudaId = null;
        this.metodoPagoNombre = metodoPagoNombre;
    }
}
