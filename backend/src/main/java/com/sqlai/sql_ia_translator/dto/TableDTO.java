package com.sqlai.sql_ia_translator.dto;

import java.util.List;

public record TableDTO(
        String name,
        List<ColumnDTO> columns,
        List<String> primaryKeys,
        List<ForeignKeyDTO> foreignKeys
) {}
