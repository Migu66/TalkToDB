package com.sqlai.sql_ia_translator;

import com.sqlai.sql_ia_translator.dto.ConnectionRequestDTO;
import com.sqlai.sql_ia_translator.dto.ConnectionResponseDTO;
import com.sqlai.sql_ia_translator.dto.NaturalLanguageQueryDTO;
import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.service.OpenAiService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests de seguridad E2E (Commit 34): se fuerza (vía mock de OpenAI) que el LLM
 * "intente" devolver SQL malicioso y se comprueba que el endpoint lo rechaza con
 * 400 y que la BD permanece intacta (conteo de filas antes/después). Es la
 * verificación viva de la regla de oro de CLAUDE.md §4: validar antes de ejecutar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.ratelimit.enabled=false")
@AutoConfigureTestRestTemplate
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityIT {

    private static final String JDBC_URL = "jdbc:h2:mem:securitydb;DB_CLOSE_DELAY=-1";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @MockitoBean
    private OpenAiService openAiService;

    private JdbcTemplate jdbc;
    private String connectionId;
    private int initialUsers;
    private int initialCities;

    @BeforeAll
    void setUp() {
        var ds = new DriverManagerDataSource(JDBC_URL, "sa", "");
        var populator = new ResourceDatabasePopulator(
                new ClassPathResource("e2e/schema.sql"),
                new ClassPathResource("e2e/data.sql"));
        DatabasePopulatorUtils.execute(populator, ds);

        jdbc = new JdbcTemplate(ds);
        initialUsers = countUsers();
        initialCities = count("cities");

        var request = new ConnectionRequestDTO(JDBC_URL, "sa", "");
        ResponseEntity<ConnectionResponseDTO> response = rest.postForEntity(
                url("/api/connection"), request, ConnectionResponseDTO.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        connectionId = response.getBody().connectionId();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private int countUsers() {
        return count("users");
    }

    private ResponseEntity<String> ask(String maliciousSql) {
        when(openAiService.generateSql(any(SchemaDTO.class), any(String.class))).thenReturn(maliciousSql);
        var query = new NaturalLanguageQueryDTO(connectionId, "borra todos los datos por favor");
        return rest.postForEntity(url("/api/query"), query, String.class);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "DROP TABLE users",
            "DELETE FROM users",
            "UPDATE users SET name = 'hacked'",
            "INSERT INTO users (id, name) VALUES (99, 'evil')",
            "TRUNCATE TABLE users",
            "SELECT * FROM users; DROP TABLE users",
            "SELECT * FROM users; DELETE FROM users",
            "SELECT * FROM users -- '; DROP TABLE users",
            "SELECT * FROM users /* x */ ; TRUNCATE TABLE users",
            "SELECT * FROM users INTO OUTFILE '/tmp/leak.csv'",
            "DeLeTe FROM users",
            "dRoP TaBlE users"
    })
    void maliciousSqlIsRejectedAndDatabaseIsUntouched(String maliciousSql) {
        ResponseEntity<String> response = ask(maliciousSql);

        // El validador rechaza antes de tocar la BD → 400 Bad Request.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // La BD permanece intacta: las tablas existen y el conteo no cambia.
        assertThat(countUsers()).isEqualTo(initialUsers);
        assertThat(count("cities")).isEqualTo(initialCities);
    }

    @Test
    void selectOverUnknownTableIsRejectedBySchemaValidation() {
        ResponseEntity<String> response = ask("SELECT * FROM secret_table");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(countUsers()).isEqualTo(initialUsers);
    }

    @Test
    void legitimateSelectStillWorksAfterAttacks() {
        when(openAiService.generateSql(any(SchemaDTO.class), any(String.class)))
                .thenReturn("SELECT id, name FROM users");
        var query = new NaturalLanguageQueryDTO(connectionId, "dame los usuarios");

        ResponseEntity<String> response = rest.postForEntity(url("/api/query"), query, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(countUsers()).isEqualTo(initialUsers);
    }
}
