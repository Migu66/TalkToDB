package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.exception.OpenAiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class OpenAiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);

    private static final Pattern PREFIX_PATTERN = Pattern.compile(
            "^(?i)(?:sql\\s*:|query\\s*:)\\s*", Pattern.MULTILINE
    );

    private final ChatClient chatClient;
    private final PromptBuilderService promptBuilder;

    public OpenAiService(ChatClient.Builder chatClientBuilder, PromptBuilderService promptBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.promptBuilder = promptBuilder;
    }

    public String generateSql(SchemaDTO schema, String userQuestion) {
        String systemPrompt = promptBuilder.buildSystemPrompt(schema);

        log.debug("System prompt enviado a OpenAI:\n{}", systemPrompt);
        log.debug("Pregunta del usuario: {}", userQuestion);

        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userQuestion)
                    .call()
                    .content();

            log.debug("Respuesta cruda de OpenAI: {}", response);

            String sql = cleanSqlResponse(response);

            log.debug("SQL limpio: {}", sql);

            return sql;
        } catch (OpenAiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error al comunicarse con OpenAI", e);
            throw new OpenAiException("Error al generar la consulta SQL");
        }
    }

    String cleanSqlResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new OpenAiException("OpenAI devolvió una respuesta vacía");
        }

        String cleaned = response.strip();

        if (cleaned.startsWith("```sql")) {
            cleaned = cleaned.substring(6);
        } else if (cleaned.startsWith("```SQL")) {
            cleaned = cleaned.substring(6);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        cleaned = cleaned.strip();

        cleaned = PREFIX_PATTERN.matcher(cleaned).replaceFirst("");

        return cleaned.strip();
    }
}
