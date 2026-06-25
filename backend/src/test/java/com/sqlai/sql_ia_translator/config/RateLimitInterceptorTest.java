package com.sqlai.sql_ia_translator.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitInterceptorTest {

    private MockHttpServletRequest request(String ip) {
        var req = new MockHttpServletRequest("POST", "/api/query");
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void allowsRequestsUpToLimitThenBlocks() throws Exception {
        var interceptor = new RateLimitInterceptor(true, 2);

        assertTrue(interceptor.preHandle(request("1.1.1.1"), new MockHttpServletResponse(), null));
        assertTrue(interceptor.preHandle(request("1.1.1.1"), new MockHttpServletResponse(), null));

        var blocked = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request("1.1.1.1"), blocked, null));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), blocked.getStatus());
        assertTrue(blocked.getContentAsString().contains("Demasiadas consultas"));
    }

    @Test
    void countersAreIndependentPerClientIp() throws Exception {
        var interceptor = new RateLimitInterceptor(true, 1);

        assertTrue(interceptor.preHandle(request("2.2.2.2"), new MockHttpServletResponse(), null));
        assertFalse(interceptor.preHandle(request("2.2.2.2"), new MockHttpServletResponse(), null));

        // Otra IP no se ve afectada por el límite de la primera
        assertTrue(interceptor.preHandle(request("3.3.3.3"), new MockHttpServletResponse(), null));
    }

    @Test
    void honoursXForwardedForHeader() throws Exception {
        var interceptor = new RateLimitInterceptor(true, 1);

        var first = request("10.0.0.1");
        first.addHeader("X-Forwarded-For", "9.9.9.9, 10.0.0.1");
        assertTrue(interceptor.preHandle(first, new MockHttpServletResponse(), null));

        var second = request("10.0.0.2");
        second.addHeader("X-Forwarded-For", "9.9.9.9");
        assertFalse(interceptor.preHandle(second, new MockHttpServletResponse(), null),
                "Misma IP real (X-Forwarded-For) debe compartir contador");
    }

    @Test
    void disabledInterceptorAllowsEverything() throws Exception {
        var interceptor = new RateLimitInterceptor(false, 1);

        for (int i = 0; i < 5; i++) {
            assertTrue(interceptor.preHandle(request("4.4.4.4"), new MockHttpServletResponse(), null));
        }
    }
}
