package com.sqlai.sql_ia_translator.dto;

import jakarta.validation.constraints.NotBlank;

public record NaturalLanguageQueryDTO(
        @NotBlank String connectionId,
        @NotBlank String question
) {}
