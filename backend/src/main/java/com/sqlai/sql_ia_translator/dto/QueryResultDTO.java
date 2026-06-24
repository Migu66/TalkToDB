package com.sqlai.sql_ia_translator.dto;

import java.util.List;
import java.util.Map;

public record QueryResultDTO(
        String generatedSql,
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        boolean truncated
) {}
