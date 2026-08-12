package com.centralizesys.model.product;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ProductResponse {
    private final Long id;
    private final String codigo;
    private final String descripcion;
    private final Double precioCosto;
    private final Double precioMayorista;
    private final Double precioMinorista;
    private final Long cantidadStock;

    // Audit fields exposed to the frontend (read-only, set by DB/service layer)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime fechaCreacion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime fechaActualizacion;

    private final Long creadoPor;
    private final Long actualizadoPor;

    public ProductResponse(Product product) {
        this.id = product.getId();
        this.codigo = product.getCodigo();
        this.descripcion = product.getDescripcion();
        this.precioCosto = product.getPrecioCosto();
        this.precioMayorista = product.getPrecioMayorista();
        this.precioMinorista = product.getPrecioMinorista();
        this.cantidadStock = product.getCantidadStock();
        this.fechaCreacion = product.getFechaCreacion();
        this.fechaActualizacion = product.getFechaActualizacion();
        this.creadoPor = product.getCreadoPor();
        this.actualizadoPor = product.getActualizadoPor();
    }
}