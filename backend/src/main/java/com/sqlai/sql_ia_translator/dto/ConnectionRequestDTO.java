package com.sqlai.sql_ia_translator.dto;

import jakarta.validation.constraints.NotBlank;

public record ConnectionRequestDTO(
        @NotBlank String jdbcUrl,
        @NotBlank String username,
        String password
) {}
