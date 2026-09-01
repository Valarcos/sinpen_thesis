package com.centralizesys.model.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {
    private Long id;
    private String nombre;
    private String email;
    private String passwordHash; // Stores BCrypt hash, NOT plain text
    private String securityPin; // Stores BCrypt hash of the report security PIN, nullable
    private UsuarioRole rol;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaCreacion;
    // Soft-delete flag. When false, the user is logically deleted and
    // cannot log in or be returned by any user query.
    private boolean activo;
}