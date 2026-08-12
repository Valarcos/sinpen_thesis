package com.centralizesys.model.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Domain model for a registered client.
 * Replaces the former plain-text `cliente_nombre` column approach
 * with a fully relational entity. The `saldo_a_favor` field is the
 * client's store credit balance, managed atomically in the DB.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {
    private Long id;
    private String nombre;
    private String telefono;
    private String dni;

    @Builder.Default
    private Double saldoAFavor = 0.0;

    @Builder.Default
    private Boolean activo = true;

    // Audit columns (consistent with Product.java pattern)
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @Builder.Default
    private Long creadoPor = 0L;

    @Builder.Default
    private Long actualizadoPor = 0L;
}
