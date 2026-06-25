package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.dto.QueryResultDTO;
import com.sqlai.sql_ia_translator.exception.DatabaseConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Service
public class QueryExecutionService {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutionService.class);

    private static final int DEFAULT_MAX_ROWS = 500;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    public QueryResultDTO execute(DataSource dataSource, String sql) {
        return execute(dataSource, sql, DEFAULT_MAX_ROWS);
    }

    public QueryResultDTO execute(DataSource dataSource, String sql, int maxRows) {
        try {
            JdbcClient jdbc = JdbcClient.create(dataSource);

            String limitedSql = applyLimitIfAbsent(sql, maxRows + 1);

            List<Map<String, Object>> rows = jdbc.sql(limitedSql).query().listOfRows();

            boolean truncated = rows.size() > maxRows;
            if (truncated) {
                rows = rows.subList(0, maxRows);
            }

            List<String> columns = rows.isEmpty()
                    ? List.of()
                    : List.copyOf(rows.getFirst().keySet());

            return new QueryResultDTO(sql, columns, rows, rows.size(), truncated);
        } catch (Exception e) {
            log.error("Error al ejecutar consulta SQL", e);
            throw new DatabaseConnectionException("Error al ejecutar la consulta");
        }
    }

    private String applyLimitIfAbsent(String sql, int limit) {
        if (sql.strip().toUpperCase().contains("LIMIT")) {
            return sql;
        }
        String trimmed = sql.stripTrailing();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + " LIMIT " + limit;
    }
}