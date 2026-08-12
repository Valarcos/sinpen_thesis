package com.centralizesys.model.product;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    private Long id;
    private String codigo;
    private String descripcion;
    private Double precioCosto;
    private Double precioMayorista;
    private Double precioMinorista;

    // Read-Only field. Logic handled by DB Triggers.
    // Lombok's @Setter on the class generates setters for everything,
    // so we specifically disable it for this field.
    // No setter is provided to prevent accidental Java-side modifications.
    @Setter(lombok.AccessLevel.NONE)
    @Builder.Default
    private Long cantidadStock = 0L;

    // Soft-delete flag. When false, the product is logically deleted and invisible
    // to the application. Physical row and stock history are preserved.
    @Builder.Default
    private boolean activo = true;

    // --- Audit Fields ---
    // fecha_creacion is set once on INSERT and never overwritten by the UPDATE SQL.
    // fecha_actualizacion is auto-stamped by the DB trigger trg_update_producto_timestamp.
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaCreacion;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaActualizacion;

    // References usuarios(id). Defaults to 0 (Sistema) to satisfy DB NOT NULL
    // constraint when no authenticated user is available (e.g., integration test setup).
    @Builder.Default
    private Long creadoPor = 0L;

    @Builder.Default
    private Long actualizadoPor = 0L;


}