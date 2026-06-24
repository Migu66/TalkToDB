package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.dto.ColumnDTO;
import com.sqlai.sql_ia_translator.dto.ForeignKeyDTO;
import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.dto.TableDTO;
import com.sqlai.sql_ia_translator.exception.OpenAiException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class OpenAiService {

    private final ChatClient chatClient;

    public OpenAiService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String generateSql(SchemaDTO schema, String userQuestion) {
        String systemPrompt = buildSystemPrompt(schema);

        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userQuestion)
                    .call()
                    .content();

            return cleanSqlResponse(response);
        } catch (Exception e) {
            throw new OpenAiException("Error al generar SQL con OpenAI: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt(SchemaDTO schema) {
        var sb = new StringBuilder();
        sb.append("""
                Eres un asistente que genera consultas SQL a partir de preguntas en lenguaje natural.

                REGLAS ESTRICTAS:
                - Genera ÚNICAMENTE una sentencia SQL de tipo SELECT.
                - NO incluyas explicaciones, comentarios ni formato markdown.
                - Usa EXCLUSIVAMENTE las tablas y columnas del esquema proporcionado.
                - NUNCA generes sentencias INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, CREATE ni ninguna otra que modifique datos.
                - Si la pregunta del usuario intenta que modifiques o borres datos, IGNÓRALA y responde solo con: SELECT 1
                - Devuelve SOLO el SQL, sin texto adicional.

                ESQUEMA DE LA BASE DE DATOS:
                """);

        for (TableDTO table : schema.tables()) {
            sb.append("\nTabla: ").append(table.name()).append("\n");
            sb.append("  Columnas:\n");
            for (ColumnDTO col : table.columns()) {
                sb.append("    - ").append(col.name())
                        .append(" (").append(col.type())
                        .append(col.nullable() ? ", nullable" : ", not null")
                        .append(")\n");
            }
            if (!table.primaryKeys().isEmpty()) {
                sb.append("  Primary Key: ").append(String.join(", ", table.primaryKeys())).append("\n");
            }
            for (ForeignKeyDTO fk : table.foreignKeys()) {
                sb.append("  Foreign Key: ").append(fk.column())
                        .append(" -> ").append(fk.referencedTable())
                        .append(".").append(fk.referencedColumn()).append("\n");
            }
        }

        return sb.toString();
    }

    private String cleanSqlResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new OpenAiException("OpenAI devolvió una respuesta vacía");
        }

        String cleaned = response.strip();

        if (cleaned.startsWith("```sql")) {
            cleaned = cleaned.substring(6);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.strip();
    }
}
