package com.sqlai.sql_ia_translator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionServiceSanitizeTest {

    @Test
    void masksPasswordParameterInMysqlUrl() {
        String url = "jdbc:mysql://host:3306/db?password=secreto123&useSSL=false";
        String sanitized = ConnectionService.sanitizeJdbcUrl(url);

        assertFalse(sanitized.contains("secreto123"));
        assertTrue(sanitized.contains("password=***"));
        assertTrue(sanitized.contains("useSSL=false"));
    }

    @Test
    void masksEmbeddedCredentialsInUrl() {
        String url = "jdbc:postgresql://user:s3cret@host:5432/db";
        String sanitized = ConnectionService.sanitizeJdbcUrl(url);

        assertFalse(sanitized.contains("s3cret"));
    }

    @Test
    void leavesUrlWithoutCredentialsUnchanged() {
        String url = "jdbc:h2:mem:testdb";
        assertEquals(url, ConnectionService.sanitizeJdbcUrl(url));
    }

    @Test
    void handlesNullUrl() {
        assertEquals("[null]", ConnectionService.sanitizeJdbcUrl(null));
    }
}
