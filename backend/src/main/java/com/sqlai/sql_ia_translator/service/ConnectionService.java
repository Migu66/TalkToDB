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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionService.class);

    private final ConcurrentMap<String, ConnectionContext> connections = new ConcurrentHashMap<>();

    public String createConnection(ConnectionRequestDTO request) {
        HikariDataSource dataSource = buildDataSource(request);
        testConnection(dataSource);

        String connectionId = UUID.randomUUID().toString();
        connections.put(connectionId, new ConnectionContext(dataSource));
        return connectionId;
    }

    public DataSource getDataSource(String connectionId) {
        return getContext(connectionId).dataSource();
    }

    public SchemaDTO getSchema(String connectionId) {
        return getContext(connectionId).schema();
    }

    public void storeSchema(String connectionId, SchemaDTO schema) {
        ConnectionContext ctx = getContext(connectionId);
        connections.put(connectionId, ctx.withSchema(schema));
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

    private ConnectionContext getContext(String connectionId) {
        ConnectionContext ctx = connections.get(connectionId);
        if (ctx == null) {
            throw new ConnectionNotFoundException("No existe conexión con id: " + connectionId);
        }
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

    private void testConnection(HikariDataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(5)) {
                dataSource.close();
                throw new DatabaseConnectionException("La conexión a la base de datos no es válida");
            }
        } catch (SQLException e) {
            dataSource.close();
            log.error("Fallo al conectar a la base de datos (SQLState: {})", e.getSQLState(), e);
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

    private record ConnectionContext(HikariDataSource dataSource, SchemaDTO schema) {

        ConnectionContext(HikariDataSource dataSource) {
            this(dataSource, null);
        }

        ConnectionContext withSchema(SchemaDTO schema) {
            return new ConnectionContext(dataSource, schema);
        }
    }
}