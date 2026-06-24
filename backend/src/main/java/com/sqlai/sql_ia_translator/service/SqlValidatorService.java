package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.exception.InvalidSqlException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SqlValidatorService {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE",
            "CREATE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "CALL",
            "MERGE", "RENAME", "REPLACE", "LOAD", "IMPORT", "EXPORT",
            "INTO", "ATTACH", "PRAGMA"
    );

    private static final Pattern WORD_BOUNDARY_PATTERN = Pattern.compile("\\b(%s)\\b".formatted(
            String.join("|", FORBIDDEN_KEYWORDS)
    ), Pattern.CASE_INSENSITIVE);

    private static final Pattern SQL_COMMENT_PATTERN = Pattern.compile("--.*|/\\*[\\s\\S]*?\\*/");

    private static final Pattern MULTI_STATEMENT_PATTERN = Pattern.compile(";\\s*\\S");

    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new InvalidSqlException("La consulta SQL está vacía");
        }

        String trimmed = sql.strip();

        rejectComments(trimmed);
        requireSelectStart(trimmed);
        rejectForbiddenKeywords(trimmed);
        rejectMultiStatement(trimmed);
    }

    private void rejectComments(String sql) {
        if (SQL_COMMENT_PATTERN.matcher(sql).find()) {
            throw new InvalidSqlException("La consulta SQL contiene comentarios no permitidos");
        }
    }

    private void requireSelectStart(String sql) {
        if (!sql.toUpperCase().startsWith("SELECT")) {
            throw new InvalidSqlException("Solo se permiten consultas SELECT");
        }
    }

    private void rejectForbiddenKeywords(String sql) {
        var matcher = WORD_BOUNDARY_PATTERN.matcher(sql);
        if (matcher.find()) {
            throw new InvalidSqlException(
                    "La consulta contiene una palabra clave prohibida: " + matcher.group().toUpperCase()
            );
        }
    }

    private void rejectMultiStatement(String sql) {
        if (MULTI_STATEMENT_PATTERN.matcher(sql).find()) {
            throw new InvalidSqlException("No se permiten múltiples sentencias SQL");
        }
    }
}