package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.dto.TableDTO;
import com.sqlai.sql_ia_translator.exception.InvalidSqlException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private static final Pattern TABLE_REFERENCE_PATTERN = Pattern.compile(
            "\\b(?:FROM|JOIN)\\s+([A-Za-z_][A-Za-z0-9_]*)",
            Pattern.CASE_INSENSITIVE
    );

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

    public void validateAgainstSchema(String sql, SchemaDTO schema) {
        Set<String> knownTables = schema.tables().stream()
                .map(TableDTO::name)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        List<String> referencedTables = extractTableNames(sql);

        List<String> unknown = referencedTables.stream()
                .filter(t -> !knownTables.contains(t.toUpperCase()))
                .toList();

        if (!unknown.isEmpty()) {
            throw new InvalidSqlException(
                    "La consulta referencia tablas inexistentes: " + String.join(", ", unknown)
            );
        }
    }

    List<String> extractTableNames(String sql) {
        List<String> tables = new ArrayList<>();
        Matcher matcher = TABLE_REFERENCE_PATTERN.matcher(sql);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
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