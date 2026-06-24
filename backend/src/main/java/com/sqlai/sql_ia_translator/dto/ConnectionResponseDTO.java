package com.sqlai.sql_ia_translator.dto;

import java.util.List;

public record ConnectionResponseDTO(
        String connectionId,
        int tableCount,
        List<String> tableNames
) {}
