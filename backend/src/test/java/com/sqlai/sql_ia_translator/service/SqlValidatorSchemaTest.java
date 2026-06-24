package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.dto.ColumnDTO;
import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.dto.TableDTO;
import com.sqlai.sql_ia_translator.exception.InvalidSqlException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlValidatorSchemaTest {

    private final SqlValidatorService validator = new SqlValidatorService();

    private final SchemaDTO schema = new SchemaDTO(List.of(
            new TableDTO("users", List.of(
                    new ColumnDTO("id", "INT", false),
                    new ColumnDTO("name", "VARCHAR", true),
                    new ColumnDTO("city", "VARCHAR", true)
            ), List.of("id"), List.of()),
            new TableDTO("orders", List.of(
                    new ColumnDTO("id", "INT", false),
                    new ColumnDTO("user_id", "INT", false),
                    new ColumnDTO("total", "DECIMAL", true)
            ), List.of("id"), List.of())
    ));

    // ── Casos válidos ──

    @Test
    void acceptsQueryWithKnownTable() {
        assertDoesNotThrow(() -> validator.validateAgainstSchema(
                "SELECT * FROM users", schema));
    }

    @Test
    void acceptsQueryWithMultipleKnownTables() {
        assertDoesNotThrow(() -> validator.validateAgainstSchema(
                "SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id", schema));
    }

    @Test
    void acceptsTableNameCaseInsensitive() {
        assertDoesNotThrow(() -> validator.validateAgainstSchema(
                "SELECT * FROM USERS", schema));
    }

    @Test
    void acceptsSubqueryWithKnownTables() {
        assertDoesNotThrow(() -> validator.validateAgainstSchema(
                "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders)", schema));
    }

    // ── Casos inválidos ──

    @Test
    void rejectsQueryWithUnknownTable() {
        var ex = assertThrows(InvalidSqlException.class,
                () -> validator.validateAgainstSchema("SELECT * FROM nonexistent", schema));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    @Test
    void rejectsQueryWithOneUnknownAmongKnown() {
        var ex = assertThrows(InvalidSqlException.class,
                () -> validator.validateAgainstSchema(
                        "SELECT * FROM users JOIN fake_table ON users.id = fake_table.uid", schema));
        assertTrue(ex.getMessage().contains("fake_table"));
    }

    @Test
    void rejectsJoinWithUnknownTable() {
        assertThrows(InvalidSqlException.class,
                () -> validator.validateAgainstSchema(
                        "SELECT * FROM users LEFT JOIN secrets ON users.id = secrets.uid", schema));
    }

    // ── Extracción de nombres de tabla ──

    @Test
    void extractsTableFromSimpleSelect() {
        var tables = validator.extractTableNames("SELECT * FROM users");
        assertEquals(List.of("users"), tables);
    }

    @Test
    void extractsTablesFromJoin() {
        var tables = validator.extractTableNames(
                "SELECT * FROM users u JOIN orders o ON u.id = o.user_id");
        assertEquals(List.of("users", "orders"), tables);
    }

    @Test
    void extractsTablesFromMultipleJoins() {
        var tables = validator.extractTableNames(
                "SELECT * FROM users u LEFT JOIN orders o ON u.id = o.user_id INNER JOIN products p ON o.pid = p.id");
        assertEquals(List.of("users", "orders", "products"), tables);
    }

    @Test
    void extractsTablesFromSubquery() {
        var tables = validator.extractTableNames(
                "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders)");
        assertEquals(List.of("users", "orders"), tables);
    }

    @Test
    void returnsEmptyForNoFromClause() {
        var tables = validator.extractTableNames("SELECT 1");
        assertTrue(tables.isEmpty());
    }
}
