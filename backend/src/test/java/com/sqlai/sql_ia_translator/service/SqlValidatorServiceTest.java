package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.exception.InvalidSqlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlValidatorServiceTest {

    private final SqlValidatorService validator = new SqlValidatorService();

    // ──────────────────────────────────────────────
    // Casos VÁLIDOS
    // ──────────────────────────────────────────────

    @Test
    void acceptsSimpleSelect() {
        assertDoesNotThrow(() -> validator.validate("SELECT * FROM usuarios"));
    }

    @Test
    void acceptsLowercaseSelect() {
        assertDoesNotThrow(() -> validator.validate("select id, nombre from t where ciudad='Madrid'"));
    }

    @Test
    void acceptsMixedCaseSelect() {
        assertDoesNotThrow(() -> validator.validate("SeLeCt * FROM users"));
    }

    @Test
    void acceptsSelectWithWhereAndJoin() {
        assertDoesNotThrow(() -> validator.validate(
                "SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id WHERE u.city = 'Madrid'"
        ));
    }

    @Test
    void acceptsSelectWithLimit() {
        assertDoesNotThrow(() -> validator.validate("SELECT id, name FROM products LIMIT 10"));
    }

    @Test
    void acceptsSelectWithTrailingSemicolon() {
        assertDoesNotThrow(() -> validator.validate("SELECT * FROM users;"));
    }

    @Test
    void acceptsSelectWithLineBreaks() {
        assertDoesNotThrow(() -> validator.validate("""
                SELECT id, name
                FROM users
                WHERE city = 'Madrid'
                ORDER BY name
                """));
    }

    @Test
    void acceptsSelectWithSubquery() {
        assertDoesNotThrow(() -> validator.validate(
                "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders)"
        ));
    }

    @Test
    void acceptsSelectCount() {
        assertDoesNotThrow(() -> validator.validate("SELECT COUNT(*) FROM users"));
    }

    @Test
    void acceptsSelectWithGroupByAndHaving() {
        assertDoesNotThrow(() -> validator.validate(
                "SELECT city, COUNT(*) FROM users GROUP BY city HAVING COUNT(*) > 5"
        ));
    }

    // ──────────────────────────────────────────────
    // Casos INVÁLIDOS — null / vacío
    // ──────────────────────────────────────────────

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void rejectsNullOrBlankSql(String sql) {
        assertThrows(InvalidSqlException.class, () -> validator.validate(sql));
    }

    // ──────────────────────────────────────────────
    // Casos INVÁLIDOS — no empieza por SELECT
    // ──────────────────────────────────────────────

    @Test
    void rejectsInsert() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("INSERT INTO users (name) VALUES ('test')"));
    }

    @Test
    void rejectsUpdate() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("UPDATE users SET name = 'hacked'"));
    }

    @Test
    void rejectsDelete() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("DELETE FROM users"));
    }

    @Test
    void rejectsDropTable() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("DROP TABLE users"));
    }

    @Test
    void rejectsLeadingSpacesBeforeNonSelect() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("   ;DELETE FROM users"));
    }

    // ──────────────────────────────────────────────
    // Casos INVÁLIDOS — keywords prohibidas (blacklist)
    // ──────────────────────────────────────────────

    @Test
    void rejectsForbiddenKeywordInsideSelect() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM users; DROP TABLE users"));
    }

    @Test
    void rejectsForbiddenKeywordCaseInsensitive() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("select * from users; dRoP table users"));
    }

    @Test
    void rejectsMixedCaseDelete() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("DeLeTe FROM users"));
    }

    @Test
    void rejectsMixedCaseDrop() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("dRoP TABLE users"));
    }

    @Test
    void rejectsAlterTable() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("ALTER TABLE users ADD COLUMN age INT"));
    }

    @Test
    void rejectsTruncate() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("TRUNCATE TABLE users"));
    }

    @Test
    void rejectsGrantStatement() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("GRANT ALL ON users TO hacker"));
    }

    @Test
    void rejectsExecStoredProcedure() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("EXEC sp_executesql N'SELECT 1'"));
    }

    @Test
    void rejectsCallProcedure() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("CALL my_procedure()"));
    }

    @Test
    void rejectsSelectIntoOutfile() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM users INTO OUTFILE '/tmp/data.csv'"));
    }

    @Test
    void rejectsSelectIntoDumpfile() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM users INTO DUMPFILE '/tmp/data.bin'"));
    }

    @Test
    void rejectsAttachDatabase() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("ATTACH DATABASE 'stolen.db' AS stolen"));
    }

    @Test
    void rejectsPragma() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("PRAGMA table_info(users)"));
    }

    // ──────────────────────────────────────────────
    // Casos INVÁLIDOS — multi-statement (stacking)
    // ──────────────────────────────────────────────

    @Test
    void rejectsMultipleStatements() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT 1; SELECT 2"));
    }

    @Test
    void rejectsSelectThenDrop() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM t; DROP TABLE t"));
    }

    @Test
    void rejectsSelectThenDelete() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM t; DELETE FROM t"));
    }

    @Test
    void rejectsSelectWithEmbeddedDelete() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM users WHERE 1=1; DELETE FROM users"));
    }

    // ──────────────────────────────────────────────
    // Casos INVÁLIDOS — comentarios sospechosos
    // ──────────────────────────────────────────────

    @Test
    void rejectsSingleLineComment() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM users -- this is a comment"));
    }

    @Test
    void rejectsCommentHidingPayload() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM t -- ; DROP TABLE t"));
    }

    @Test
    void rejectsBlockComment() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM users /* hidden */"));
    }

    @Test
    void rejectsBlockCommentBeforeTruncate() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM t /* */ ; TRUNCATE t"));
    }

    @Test
    void rejectsCommentAtStart() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("/* bypass */ SELECT * FROM users"));
    }
}
