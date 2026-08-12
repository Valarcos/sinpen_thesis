package com.centralizesys.model.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight response DTO for client list endpoints.
 * Only exposes data safe for the UI. saldo_a_favor is included
 * so the frontend can dynamically show/hide the "Saldo a Favor"
 * payment option.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteResponse {
    private Long id;
    private String nombre;
    private String telefono;
    private String dni;
    private Double saldoAFavor;
    private Boolean activo;
}
