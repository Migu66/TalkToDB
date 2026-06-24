package com.sqlai.sql_ia_translator.dto;

public record ForeignKeyDTO(
        String column,
        String referencedTable,
        String referencedColumn
) {}
