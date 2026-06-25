package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.dto.ConnectionRequestDTO;
import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.exception.ConnectionNotFoundException;
import com.sqlai.sql_ia_translator.exception.DatabaseConnectionException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);

    private final ConcurrentMap<String, ConnectionContext> connections = new ConcurrentHashMap<>();
    private final JdbcUrlValidator jdbcUrlValidator;

    public ConnectionService(JdbcUrlValidator jdbcUrlValidator) {
        this.jdbcUrlValidator = jdbcUrlValidator;
    }

    public String createConnection(ConnectionRequestDTO request) {
        String safeUrl = sanitizeJdbcUrl(request.jdbcUrl());

        jdbcUrlValidator.validate(request.jdbcUrl());
        HikariDataSource dataSource = buildDataSource(request);
        testConnection(dataSource, safeUrl);

        String connectionId = UUID.randomUUID().toString();
        connections.put(connectionId, new ConnectionContext(dataSource));
        log.info("Conexión creada (id={}, url={})", connectionId, safeUrl);
        return connectionId;
    }

    public DataSource getDataSource(String connectionId) {
        return getContext(connectionId).dataSource();
    }

    public SchemaDTO getSchema(String connectionId) {
        return getContext(connectionId).schema();
    }

    public void storeSchema(String connectionId, SchemaDTO schema) {
        getContext(connectionId).setSchema(schema);
    }

    public void removeConnection(String connectionId) {
        ConnectionContext ctx = connections.remove(connectionId);
        if (ctx == null) {
            throw new ConnectionNotFoundException("No existe conexión con id: " + connectionId);
        }
        ctx.dataSource().close();
    }

    public boolean exists(String connectionId) {
        return connections.containsKey(connectionId);
    }

    /**
     * Cierra y elimina las conexiones cuyo último acceso supere el TTL indicado.
     * Devuelve cuántas se purgaron. Lo invoca {@link ConnectionCleanupTask}.
     */
    public int purgeExpired(Duration ttl) {
        Instant threshold = Instant.now().minus(ttl);
        int removed = 0;
        Iterator<Map.Entry<String, ConnectionContext>> it = connections.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ConnectionContext> entry = it.next();
            if (entry.getValue().lastAccess().isBefore(threshold)) {
                it.remove();
                try {
                    entry.getValue().dataSource().close();
                } catch (RuntimeException e) {
                    log.warn("No se pudo cerrar el DataSource de la conexión id={}", entry.getKey(), e);
                }
                removed++;
                log.info("Conexión expirada cerrada por inactividad (id={})", entry.getKey());
            }
        }
        return removed;
    }

    public int activeConnections() {
        return connections.size();
    }

    private ConnectionContext getContext(String connectionId) {
        ConnectionContext ctx = connections.get(connectionId);
        if (ctx == null) {
            throw new ConnectionNotFoundException("No existe conexión con id: " + connectionId);
        }
        ctx.touch();
        return ctx;
    }

    private HikariDataSource buildDataSource(ConnectionRequestDTO request) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(request.jdbcUrl());
        config.setUsername(request.username());
        config.setPassword(request.password());
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }

    private void testConnection(HikariDataSource dataSource, String safeUrl) {
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(5)) {
                dataSource.close();
                throw new DatabaseConnectionException("La conexión a la base de datos no es válida");
            }
        } catch (SQLException e) {
            dataSource.close();
            log.error("Fallo al conectar a la base de datos (url={}, SQLState: {})", safeUrl, e.getSQLState(), e);
            throw new DatabaseConnectionException("No se pudo conectar a la base de datos");
        }
    }

    static String sanitizeJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return "[null]";
        }
        return jdbcUrl
                .replaceAll("(password=)[^&;]*", "$1***")
                .replaceAll("(:)[^/@:]+(@)", "$1***$2");
    }

    /**
     * Contexto de una conexión activa. Mantiene el DataSource, el esquema
     * extraído y el instante del último acceso (para el TTL del Commit 31).
     * La contraseña solo vive dentro del HikariDataSource.
     */
    private static final class ConnectionContext {

        private final HikariDataSource dataSource;
        private final Instant createdAt;
        private volatile SchemaDTO schema;
        private volatile Instant lastAccess;

        ConnectionContext(HikariDataSource dataSource) {
            this.dataSource = dataSource;
            this.createdAt = Instant.now();
            this.lastAccess = this.createdAt;
        }

        HikariDataSource dataSource() {
            return dataSource;
        }

        SchemaDTO schema() {
            return schema;
        }

        void setSchema(SchemaDTO schema) {
            this.schema = schema;
        }

        Instant lastAccess() {
            return lastAccess;
        }

        void touch() {
            this.lastAccess = Instant.now();
        }
    }
}