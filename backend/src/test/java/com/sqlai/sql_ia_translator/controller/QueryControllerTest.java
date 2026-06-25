package com.sqlai.sql_ia_translator.controller;

import com.sqlai.sql_ia_translator.dto.ColumnDTO;
import com.sqlai.sql_ia_translator.dto.QueryResultDTO;
import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.dto.TableDTO;
import com.sqlai.sql_ia_translator.exception.ConnectionNotFoundException;
import com.sqlai.sql_ia_translator.exception.DatabaseConnectionException;
import com.sqlai.sql_ia_translator.exception.InvalidSqlException;
import com.sqlai.sql_ia_translator.exception.OpenAiException;
import com.sqlai.sql_ia_translator.service.ConnectionService;
import com.sqlai.sql_ia_translator.service.OpenAiService;
import com.sqlai.sql_ia_translator.service.QueryExecutionService;
import com.sqlai.sql_ia_translator.service.SqlValidatorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QueryController.class)
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConnectionService connectionService;

    @MockitoBean
    private OpenAiService openAiService;

    @MockitoBean
    private SqlValidatorService sqlValidatorService;

    @MockitoBean
    private QueryExecutionService queryExecutionService;

    private static final String CONNECTION_ID = "test-conn-id";
    private static final String QUESTION = "Dame todos los usuarios de Madrid";
    private static final String GENERATED_SQL = "SELECT * FROM users WHERE city = 'Madrid'";

    private final SchemaDTO schema = new SchemaDTO(List.of(
            new TableDTO("users", List.of(
                    new ColumnDTO("id", "INT", false),
                    new ColumnDTO("name", "VARCHAR", true),
                    new ColumnDTO("city", "VARCHAR", true)
            ), List.of("id"), List.of())
    ));

    private final QueryResultDTO queryResult = new QueryResultDTO(
            GENERATED_SQL,
            List.of("id", "name", "city"),
            List.of(
                    Map.of("id", 1, "name", "Ana", "city", "Madrid"),
                    Map.of("id", 3, "name", "Lucía", "city", "Madrid")
            ),
            2,
            false
    );

    // ── Caso feliz: pipeline completo ──

    @Test
    void fullPipelineReturnsResult() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(connectionService.getSchema(CONNECTION_ID)).thenReturn(schema);
        when(connectionService.getDataSource(CONNECTION_ID)).thenReturn(ds);
        when(openAiService.generateSql(schema, QUESTION)).thenReturn(GENERATED_SQL);
        when(queryExecutionService.execute(ds, GENERATED_SQL)).thenReturn(queryResult);

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId": "%s", "question": "%s"}
                                """.formatted(CONNECTION_ID, QUESTION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedSql").value(GENERATED_SQL))
                .andExpect(jsonPath("$.rowCount").value(2))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.rows.length()").value(2));

        verify(sqlValidatorService).validate(GENERATED_SQL);
        verify(sqlValidatorService).validateAgainstSchema(GENERATED_SQL, schema);
    }

    // ── Conexión no encontrada → 404 ──

    @Test
    void returnsNotFoundForInvalidConnectionId() throws Exception {
        when(connectionService.getSchema(anyString()))
                .thenThrow(new ConnectionNotFoundException("No existe conexión con id: unknown"));

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId": "unknown", "question": "test"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── Error de OpenAI → 502 ──

    @Test
    void returnsBadGatewayOnOpenAiError() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(connectionService.getSchema(CONNECTION_ID)).thenReturn(schema);
        when(connectionService.getDataSource(CONNECTION_ID)).thenReturn(ds);
        when(openAiService.generateSql(any(), anyString()))
                .thenThrow(new OpenAiException("Connection refused"));

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId": "%s", "question": "%s"}
                                """.formatted(CONNECTION_ID, QUESTION)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    // ── SQL rechazado por el validador → 400 ──

    @Test
    void returnsBadRequestOnInvalidSql() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(connectionService.getSchema(CONNECTION_ID)).thenReturn(schema);
        when(connectionService.getDataSource(CONNECTION_ID)).thenReturn(ds);
        when(openAiService.generateSql(any(), anyString())).thenReturn("DROP TABLE users");
        doThrow(new InvalidSqlException("Solo se permiten consultas SELECT"))
                .when(sqlValidatorService).validate("DROP TABLE users");

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId": "%s", "question": "%s"}
                                """.formatted(CONNECTION_ID, QUESTION)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(queryExecutionService, never()).execute(any(), anyString());
    }

    // ── Tablas inexistentes en el esquema → 400 ──

    @Test
    void returnsBadRequestOnSchemaValidationFailure() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(connectionService.getSchema(CONNECTION_ID)).thenReturn(schema);
        when(connectionService.getDataSource(CONNECTION_ID)).thenReturn(ds);
        when(openAiService.generateSql(any(), anyString()))
                .thenReturn("SELECT * FROM tabla_fantasma");
        doThrow(new InvalidSqlException("La consulta referencia tablas inexistentes: tabla_fantasma"))
                .when(sqlValidatorService).validateAgainstSchema(anyString(), any());

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId": "%s", "question": "%s"}
                                """.formatted(CONNECTION_ID, QUESTION)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(queryExecutionService, never()).execute(any(), anyString());
    }

    // ── Error al ejecutar la query → 502 ──

    @Test
    void returnsBadGatewayOnExecutionError() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(connectionService.getSchema(CONNECTION_ID)).thenReturn(schema);
        when(connectionService.getDataSource(CONNECTION_ID)).thenReturn(ds);
        when(openAiService.generateSql(any(), anyString())).thenReturn(GENERATED_SQL);
        when(queryExecutionService.execute(ds, GENERATED_SQL))
                .thenThrow(new DatabaseConnectionException("Error al ejecutar la consulta"));

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId": "%s", "question": "%s"}
                                """.formatted(CONNECTION_ID, QUESTION)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    // ── Validación del request: campos obligatorios ──

    @Test
    void returnsBadRequestWhenConnectionIdIsBlank() throws Exception {
        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId": "", "question": "test"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(openAiService, queryExecutionService);
    }

    @Test
    void returnsBadRequestWhenQuestionIsBlank() throws Exception {
        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId": "abc", "question": ""}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(openAiService, queryExecutionService);
    }

    // ── Verifica orden de operaciones en el pipeline ──

    @Test
    void validatesBeforeExecuting() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(connectionService.getSchema(CONNECTION_ID)).thenReturn(schema);
        when(connectionService.getDataSource(CONNECTION_ID)).thenReturn(ds);
        when(openAiService.generateSql(any(), anyString())).thenReturn(GENERATED_SQL);
        when(queryExecutionService.execute(ds, GENERATED_SQL)).thenReturn(queryResult);

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectionId": "%s", "question": "%s"}
                                """.formatted(CONNECTION_ID, QUESTION)))
                .andExpect(status().isOk());

        var inOrder = inOrder(openAiService, sqlValidatorService, queryExecutionService);
        inOrder.verify(openAiService).generateSql(any(), anyString());
        inOrder.verify(sqlValidatorService).validate(GENERATED_SQL);
        inOrder.verify(sqlValidatorService).validateAgainstSchema(GENERATED_SQL, schema);
        inOrder.verify(queryExecutionService).execute(ds, GENERATED_SQL);
    }
}
