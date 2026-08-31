package com.centralizesys.model.auth;

/**
 * Request DTO for updating user details.
 * All fields are optional - only non-null fields will be updated.
 */
public record UpdateUserRequest(
        String nombre, // Optional - update name
        String email, // Optional - update email
        String password, // Optional - set new password (will be hashed)
        String rol, // Optional - change role (ADMIN, EMPLEADO, OWNER)
        String securityPin // Optional - set new security pin (will be hashed)
) {
}
