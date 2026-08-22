package com.centralizesys.model.sales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Venta {
    private Long id;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fecha; // YYYY-MM-DD HH:mm:ss
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaCreacion;
    private String clienteNombre;
    private Long clienteId;           // FK to clientes.id (nullable for legacy sales)
    private Double totalVenta = 0.0;
    private Double descuentoGlobal = 0.0;
    private Double recargoGlobal = 0.0;  // Global surcharge (e.g., tax, credit card fee). Formula: Total = Subtotal - Descuento + Recargo
    private Double saldoGenerado = 0.0;

    public void setTotalVenta(Double totalVenta) {
        this.totalVenta = (totalVenta != null) ? totalVenta : 0.0;
    }

    public void setDescuentoGlobal(Double descuentoGlobal) {
        this.descuentoGlobal = (descuentoGlobal != null) ? descuentoGlobal : 0.0;
    }

    public void setRecargoGlobal(Double recargoGlobal) {
        this.recargoGlobal = (recargoGlobal != null) ? recargoGlobal : 0.0;
    }

    public void setSaldoGenerado(Double saldoGenerado) {
        this.saldoGenerado = (saldoGenerado != null) ? saldoGenerado : 0.0;
    }

    private String tipoVenta; // NEW: Persisted 'MAYORISTA' or 'MINORISTA'
    private Long usuarioId; // For audit/security
    private String estado; // ACTIVA, ANULADA
    private Double costoTotal = 0.0; // Dynamically calculated
    private Long cantidadProductos; // Dynamically calculated
    private Integer version; // Optimistic locking version

    public void setCostoTotal(Double costoTotal) {
        this.costoTotal = (costoTotal != null) ? costoTotal : 0.0;
    }
}
