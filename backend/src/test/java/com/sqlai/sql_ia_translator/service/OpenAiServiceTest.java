package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.dto.ColumnDTO;
import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.dto.TableDTO;
import com.sqlai.sql_ia_translator.exception.OpenAiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenAiServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder chatClientBuilder;

    private OpenAiService openAiService;
    private ChatClient chatClient;

    private final PromptBuilderService promptBuilder = spy(new PromptBuilderService());

    private final SchemaDTO schema = new SchemaDTO(List.of(
            new TableDTO("users", List.of(
                    new ColumnDTO("id", "INT", false),
                    new ColumnDTO("name", "VARCHAR", true)
            ), List.of("id"), List.of())
    ));

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        openAiService = new OpenAiService(chatClientBuilder, promptBuilder);
    }

    // ── cleanSqlResponse: formatos de respuesta sucia ──

    @Test
    void cleansPlainSql() {
        assertEquals("SELECT * FROM users",
                openAiService.cleanSqlResponse("SELECT * FROM users"));
    }

    @Test
    void cleansSqlFenceBlock() {
        assertEquals("SELECT * FROM users",
                openAiService.cleanSqlResponse("```sql\nSELECT * FROM users\n```"));
    }

    @Test
    void cleansSqlFenceUpperCase() {
        assertEquals("SELECT * FROM users",
                openAiService.cleanSqlResponse("```SQL\nSELECT * FROM users\n```"));
    }

    @Test
    void cleansGenericFenceBlock() {
        assertEquals("SELECT * FROM users",
                openAiService.cleanSqlResponse("```\nSELECT * FROM users\n```"));
    }

    @Test
    void cleansSqlPrefix() {
        assertEquals("SELECT * FROM users",
                openAiService.cleanSqlResponse("SQL: SELECT * FROM users"));
    }

    @Test
    void cleansSqlPrefixLowerCase() {
        assertEquals("SELECT * FROM users",
                openAiService.cleanSqlResponse("sql: SELECT * FROM users"));
    }

    @Test
    void cleansQueryPrefix() {
        assertEquals("SELECT * FROM users",
                openAiService.cleanSqlResponse("Query: SELECT * FROM users"));
    }

    @Test
    void cleansLeadingAndTrailingWhitespace() {
        assertEquals("SELECT * FROM users",
                openAiService.cleanSqlResponse("  \n  SELECT * FROM users  \n  "));
    }

    @Test
    void cleansFenceWithExtraWhitespace() {
        assertEquals("SELECT * FROM users",
                openAiService.cleanSqlResponse("```sql\n  SELECT * FROM users  \n```"));
    }

    @Test
    void preservesInternalWhitespace() {
        String sql = "SELECT *\nFROM users\nWHERE id = 1";
        assertEquals(sql, openAiService.cleanSqlResponse(sql));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void rejectsNullOrBlankResponse(String response) {
        assertThrows(OpenAiException.class,
                () -> openAiService.cleanSqlResponse(response));
    }

    // ── generateSql: flujo completo con ChatClient mockeado ──

    @Test
    void generateSqlReturnsCleaned() {
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content()
        ).thenReturn("```sql\nSELECT id, name FROM users\n```");

        String sql = openAiService.generateSql(schema, "Dame todos los usuarios");

        assertEquals("SELECT id, name FROM users", sql);
    }

    @Test
    void generateSqlDelegatesPromptBuilding() {
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content()
        ).thenReturn("SELECT 1");

        openAiService.generateSql(schema, "test");

        verify(promptBuilder).buildSystemPrompt(schema);
    }

    // ── generateSql: manejo de errores ──

    @Test
    void generateSqlThrowsOnNullResponse() {
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .content()
        ).thenReturn(null);

        assertThrows(OpenAiException.class,
                () -> openAiService.generateSql(schema, "test"));
    }

    @Test
    void generateSqlWrapsNetworkError() {
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
        ).thenThrow(new RuntimeException("Connection refused"));

        var ex = assertThrows(OpenAiException.class,
                () -> openAiService.generateSql(schema, "test"));
        assertTrue(ex.getMessage().contains("Connection refused"));
    }
}
