package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.exception.DatabaseConnectionException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class JdbcUrlValidator {

    private static final List<String> ALLOWED_PREFIXES = List.of(
            "jdbc:mysql:", "jdbc:postgresql:", "jdbc:h2:"
    );

    private static final Set<String> DANGEROUS_PARAMS = Set.of(
            "allowloadlocalinfile",
            "allowurlinlocalinfile",
            "autodeserialization",
            "autodeserialize",
            "allowmultiqueries",
            "allowpublickeyretrieval",
            "socksfactory",
            "socksproxyhost",
            "socksproxyport",
            "sslfactory",
            "socketfactory",
            "init",
            "runinit",
            "initializationscript",
            "loggerfile",
            "logslowqueries"
    );

    private static final Pattern PARAM_SPLITTER = Pattern.compile("[?&;]");

    public void validate(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new DatabaseConnectionException("La URL de conexión no puede estar vacía");
        }

        String lower = jdbcUrl.strip().toLowerCase();

        validatePrefix(lower);
        validateNoEmbeddedCredentials(lower);
        validateNoBlacklistedParams(lower);
    }

    private void validatePrefix(String url) {
        boolean allowed = ALLOWED_PREFIXES.stream().anyMatch(url::startsWith);
        if (!allowed) {
            throw new DatabaseConnectionException(
                    "Tipo de base de datos no soportado. Prefijos permitidos: " +
                            String.join(", ", ALLOWED_PREFIXES));
        }
    }

    private void validateNoEmbeddedCredentials(String url) {
        if (url.contains("@") && url.matches(".*://[^/]*:.*@.*")) {
            throw new DatabaseConnectionException(
                    "No se permiten credenciales embebidas en la URL. Usa los campos usuario/contraseña");
        }
    }

    private void validateNoBlacklistedParams(String url) {
        int queryStart = indexOfFirstParamSeparator(url);
        if (queryStart < 0) {
            return;
        }

        String queryString = url.substring(queryStart);
        String[] params = PARAM_SPLITTER.split(queryString);

        for (String param : params) {
            String key = param.split("=", 2)[0].strip().toLowerCase();
            if (DANGEROUS_PARAMS.contains(key)) {
                throw new DatabaseConnectionException(
                        "La URL contiene un parámetro no permitido: " + key);
            }
        }
    }

    private int indexOfFirstParamSeparator(String url) {
        int question = url.indexOf('?');
        int semicolon = url.indexOf(';');
        if (question < 0) return semicolon;
        if (semicolon < 0) return question;
        return Math.min(question, semicolon);
    }
}
