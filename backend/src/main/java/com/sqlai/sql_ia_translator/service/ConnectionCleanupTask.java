package com.sqlai.sql_ia_translator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Tarea periódica que cierra las conexiones dinámicas inactivas para evitar
 * fugas de recursos (CLAUDE.md §4.6). El TTL y la frecuencia son configurables
 * vía propiedades {@code app.connection.ttl-minutes} y
 * {@code app.connection.cleanup-interval-ms}.
 */
@Component
public class ConnectionCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(ConnectionCleanupTask.class);

    private final ConnectionService connectionService;
    private final Duration ttl;

    public ConnectionCleanupTask(ConnectionService connectionService,
                                 @Value("${app.connection.ttl-minutes:30}") long ttlMinutes) {
        this.connectionService = connectionService;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Scheduled(fixedDelayString = "${app.connection.cleanup-interval-ms:300000}")
    public void cleanupExpiredConnections() {
        int removed = connectionService.purgeExpired(ttl);
        if (removed > 0) {
            log.info("Limpieza TTL: {} conexión(es) inactiva(s) cerrada(s)", removed);
        }
    }
}
