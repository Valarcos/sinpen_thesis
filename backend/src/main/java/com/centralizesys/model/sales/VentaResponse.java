package com.centralizesys.model.sales;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentaResponse {
    private Long id;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fecha;
    private String clienteNombre;
    private Long clienteId;
    private String vendedorNombre; // Name of the user who made the sale
    private Double totalVenta;
    private Double descuentoGlobal;
    private Double recargoGlobal;  // Global surcharge
    private String tipoVenta;
    private List<DetalleVenta> items;
    private List<PagoVenta> pagos;

    // List of warning messages (e.g., "Stock negativo para ART-123")
    private List<String> alertas;
    private String estado;
    private Double costoTotal;
    private Integer version;
}