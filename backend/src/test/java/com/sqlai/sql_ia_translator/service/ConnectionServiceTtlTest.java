package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.dto.ConnectionRequestDTO;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionServiceTtlTest {

    private final ConnectionService service = new ConnectionService(new JdbcUrlValidator());

    private ConnectionRequestDTO h2(String db) {
        return new ConnectionRequestDTO("jdbc:h2:mem:" + db + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test
    void purgesInactiveConnection() throws InterruptedException {
        String id = service.createConnection(h2("ttl_purge"));
        assertTrue(service.exists(id));

        Thread.sleep(50);
        int removed = service.purgeExpired(Duration.ofMillis(1));

        assertEquals(1, removed);
        assertFalse(service.exists(id));
        assertEquals(0, service.activeConnections());
    }

    @Test
    void keepsRecentConnection() {
        String id = service.createConnection(h2("ttl_keep"));

        int removed = service.purgeExpired(Duration.ofMinutes(30));

        assertEquals(0, removed);
        assertTrue(service.exists(id));
        service.removeConnection(id);
    }

    @Test
    void accessRefreshesLastAccessAndAvoidsPurge() throws InterruptedException {
        String id = service.createConnection(h2("ttl_touch"));

        Thread.sleep(50);
        service.getDataSource(id); // marca lastAccess al usarse

        int removed = service.purgeExpired(Duration.ofMillis(40));

        assertEquals(0, removed, "Una conexión usada recientemente no debe purgarse");
        assertTrue(service.exists(id));
        service.removeConnection(id);
    }
}
