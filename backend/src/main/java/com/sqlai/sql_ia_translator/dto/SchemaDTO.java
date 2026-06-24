package com.sqlai.sql_ia_translator.dto;

import java.util.List;

public record SchemaDTO(
        List<TableDTO> tables
) {}
