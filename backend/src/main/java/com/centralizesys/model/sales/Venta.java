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
    private Double totalVenta;
    private Double descuentoGlobal;
    private String tipoVenta; // NEW: Persisted 'MAYORISTA' or 'MINORISTA'
    private Long usuarioId; // For audit/security
    private String estado; // ACTIVA, ANULADA
    private Double costoTotal; // Dynamically calculated
    private Long cantidadProductos; // Dynamically calculated
}
