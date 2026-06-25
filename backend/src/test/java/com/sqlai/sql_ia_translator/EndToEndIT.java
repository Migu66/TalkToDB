package com.sqlai.sql_ia_translator;

import com.sqlai.sql_ia_translator.dto.ConnectionRequestDTO;
import com.sqlai.sql_ia_translator.dto.ConnectionResponseDTO;
import com.sqlai.sql_ia_translator.dto.NaturalLanguageQueryDTO;
import com.sqlai.sql_ia_translator.dto.QueryResultDTO;
import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.service.OpenAiService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test E2E del flujo completo (Commit 33): arranca el servidor real, conecta a
 * una BD H2 con datos de prueba vía la API, y ejecuta una consulta con la IA
 * mockeada. Verifica conexión → extracción de esquema → consulta → resultados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.ratelimit.enabled=false")
@AutoConfigureTestRestTemplate
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndIT {

    private static final String JDBC_URL = "jdbc:h2:mem:e2edb;DB_CLOSE_DELAY=-1";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @MockitoBean
    private OpenAiService openAiService;

    @BeforeAll
    void populateDatabase() {
        var ds = new DriverManagerDataSource(JDBC_URL, "sa", "");
        var populator = new ResourceDatabasePopulator(
                new ClassPathResource("e2e/schema.sql"),
                new ClassPathResource("e2e/data.sql"));
        DatabasePopulatorUtils.execute(populator, ds);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String connect() {
        var request = new ConnectionRequestDTO(JDBC_URL, "sa", "");
        ResponseEntity<ConnectionResponseDTO> response = rest.postForEntity(
                url("/api/connection"), request, ConnectionResponseDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().connectionId()).isNotBlank();
        return response.getBody().connectionId();
    }

    @Test
    void fullFlowConnectExtractSchemaQueryReturnsRows() {
        String connectionId = connect();

        // La IA "genera" un SELECT válido sobre el esquema real.
        String sql = "SELECT id, name, city FROM users WHERE city = 'Madrid'";
        when(openAiService.generateSql(any(SchemaDTO.class), any(String.class))).thenReturn(sql);

        var query = new NaturalLanguageQueryDTO(connectionId, "Dame los usuarios de Madrid");
        ResponseEntity<QueryResultDTO> response = rest.postForEntity(
                url("/api/query"), query, QueryResultDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        QueryResultDTO body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.generatedSql()).isEqualTo(sql);
        assertThat(body.rowCount()).isEqualTo(2);
        assertThat(body.truncated()).isFalse();
        assertThat(body.columns()).hasSize(3);
        assertThat(body.rows())
                .allSatisfy(row -> assertThat(row.values()).contains("Madrid"));
    }

    @Test
    void schemaEndpointExposesTablesAndForeignKeys() {
        String connectionId = connect();

        ResponseEntity<SchemaDTO> response = rest.getForEntity(
                url("/api/connection/" + connectionId + "/schema"), SchemaDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SchemaDTO schema = response.getBody();
        assertThat(schema).isNotNull();
        assertThat(schema.tables()).extracting(t -> t.name().toUpperCase())
                .contains("USERS", "CITIES");

        var usersTable = schema.tables().stream()
                .filter(t -> t.name().equalsIgnoreCase("users"))
                .findFirst().orElseThrow();
        assertThat(usersTable.foreignKeys())
                .anySatisfy(fk -> assertThat(fk.referencedTable().toUpperCase()).isEqualTo("CITIES"));
    }

    @Test
    void deleteRemovesConnection() {
        String connectionId = connect();

        ResponseEntity<Void> deleted = rest.exchange(
                url("/api/connection/" + connectionId), HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> afterDelete = rest.getForEntity(
                url("/api/connection/" + connectionId + "/schema"), String.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
