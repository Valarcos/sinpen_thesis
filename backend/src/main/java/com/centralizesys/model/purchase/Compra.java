package com.centralizesys.model.purchase;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Compra {
    private Long id;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fecha; // ISO-8601
    private String proveedor;
    private String nroComprobante;
    private Double totalCompra = 0.0;
    private Long usuarioId; // Optional, for audit/ownership, company owners buying

    public void setTotalCompra(Double totalCompra) {
        this.totalCompra = (totalCompra != null) ? totalCompra : 0.0;
    }
}