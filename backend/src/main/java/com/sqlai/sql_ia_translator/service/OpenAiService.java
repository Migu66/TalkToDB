package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.exception.OpenAiException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class OpenAiService {

    private final ChatClient chatClient;
    private final PromptBuilderService promptBuilder;

    public OpenAiService(ChatClient.Builder chatClientBuilder, PromptBuilderService promptBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.promptBuilder = promptBuilder;
    }

    public String generateSql(SchemaDTO schema, String userQuestion) {
        String systemPrompt = promptBuilder.buildSystemPrompt(schema);

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

    String cleanSqlResponse(String response) {
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
