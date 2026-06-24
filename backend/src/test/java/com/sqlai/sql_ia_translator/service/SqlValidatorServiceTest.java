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

    @Test
    void acceptsSimpleSelect() {
        assertDoesNotThrow(() -> validator.validate("SELECT * FROM users"));
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void rejectsNullOrBlankSql(String sql) {
        assertThrows(InvalidSqlException.class, () -> validator.validate(sql));
    }

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
    void rejectsMultipleStatements() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT 1; SELECT 2"));
    }

    @Test
    void rejectsSingleLineComment() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM users -- this is a comment"));
    }

    @Test
    void rejectsBlockComment() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM users /* hidden */"));
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
    void rejectsSelectWithEmbeddedDelete() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validate("SELECT * FROM users WHERE 1=1; DELETE FROM users"));
    }
}
