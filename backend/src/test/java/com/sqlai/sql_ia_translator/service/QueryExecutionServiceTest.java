package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.dto.QueryResultDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class QueryExecutionServiceTest {

    private static DriverManagerDataSource dataSource;
    private final QueryExecutionService service = new QueryExecutionService();

    @BeforeAll
    static void setUp() throws Exception {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:testdb_exec;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), city VARCHAR(100))");
            stmt.execute("INSERT INTO users VALUES (1, 'Ana', 'Madrid')");
            stmt.execute("INSERT INTO users VALUES (2, 'Carlos', 'Barcelona')");
            stmt.execute("INSERT INTO users VALUES (3, 'Lucía', 'Madrid')");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE users");
        }
    }

    @Test
    void executesSimpleSelect() {
        QueryResultDTO result = service.execute(dataSource, "SELECT * FROM users");

        assertEquals("SELECT * FROM users", result.generatedSql());
        assertEquals(3, result.rowCount());
        assertFalse(result.truncated());
        assertTrue(result.columns().contains("ID"));
        assertTrue(result.columns().contains("NAME"));
        assertTrue(result.columns().contains("CITY"));
    }

    @Test
    void executesSelectWithWhere() {
        QueryResultDTO result = service.execute(dataSource, "SELECT name FROM users WHERE city = 'Madrid'");

        assertEquals(2, result.rowCount());
        assertFalse(result.truncated());
    }

    @Test
    void appliesLimitWhenAbsent() {
        QueryResultDTO result = service.execute(dataSource, "SELECT * FROM users", 2);

        assertEquals(2, result.rowCount());
        assertTrue(result.truncated());
    }

    @Test
    void respectsExistingLimit() {
        QueryResultDTO result = service.execute(dataSource, "SELECT * FROM users LIMIT 1");

        assertEquals(1, result.rowCount());
        assertFalse(result.truncated());
    }

    @Test
    void returnsEmptyColumnsForNoResults() {
        QueryResultDTO result = service.execute(dataSource, "SELECT * FROM users WHERE city = 'Sevilla'");

        assertEquals(0, result.rowCount());
        assertTrue(result.columns().isEmpty());
        assertFalse(result.truncated());
    }

    @Test
    void stripsTrailingSemicolonBeforeAddingLimit() {
        QueryResultDTO result = service.execute(dataSource, "SELECT * FROM users;", 2);

        assertEquals(2, result.rowCount());
        assertTrue(result.truncated());
    }
}
