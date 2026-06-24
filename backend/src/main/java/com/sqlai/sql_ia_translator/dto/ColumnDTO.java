package com.sqlai.sql_ia_translator.dto;

public record ColumnDTO(
        String name,
        String type,
        boolean nullable
) {}
