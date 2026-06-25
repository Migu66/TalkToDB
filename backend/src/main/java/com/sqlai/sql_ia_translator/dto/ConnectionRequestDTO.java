package com.sqlai.sql_ia_translator.dto;

import jakarta.validation.constraints.NotBlank;

public record ConnectionRequestDTO(
        @NotBlank String jdbcUrl,
        @NotBlank String username,
        String password
) {

    /**
     * toString enmascarado: evita que la contraseña aparezca en claro si el DTO
     * se loguea por accidente (CLAUDE.md §4.7). El record genera por defecto un
     * toString que incluiría la password; aquí se sobrescribe para ocultarla.
     */
    @Override
    public String toString() {
        return "ConnectionRequestDTO[jdbcUrl=" + jdbcUrl
                + ", username=" + username
                + ", password=" + (password == null || password.isEmpty() ? "[none]" : "***")
                + "]";
    }
}
