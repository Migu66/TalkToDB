package com.sqlai.sql_ia_translator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sqlai.sql_ia_translator.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiting básico en memoria por cliente (IP), con ventana fija de un
 * minuto. Protege el endpoint de consultas frente a abuso (coste de OpenAI y
 * carga de la BD del usuario) — CLAUDE.md §4.6. Al superar el límite responde
 * {@code 429 Too Many Requests}. Configurable vía
 * {@code app.ratelimit.enabled} y {@code app.ratelimit.requests-per-minute}.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final long WINDOW_MS = 60_000L;

    private final boolean enabled;
    private final int limit;
    // Mapper propio: en Spring Boot 4 no se expone un bean ObjectMapper por
    // defecto, así que el interceptor serializa el cuerpo de error por su cuenta.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitInterceptor(
            @Value("${app.ratelimit.enabled:true}") boolean enabled,
            @Value("${app.ratelimit.requests-per-minute:20}") int limit) {
        this.enabled = enabled;
        this.limit = limit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!enabled) {
            return true;
        }

        String key = clientKey(request);
        long now = System.currentTimeMillis();

        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= WINDOW_MS) {
                return new Window(now);
            }
            existing.count++;
            return existing;
        });

        if (window.count > limit) {
            reject(request, response);
            return false;
        }
        return true;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.warn("Rate limit superado para cliente {} en {}", clientKey(request), request.getRequestURI());

        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponseDTO body = new ErrorResponseDTO(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                "Demasiadas consultas. Inténtalo de nuevo en unos instantes.",
                request.getRequestURI()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }

    /** Ventana de conteo mutable; las mutaciones ocurren dentro de map.compute (atómico por clave). */
    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
            this.count = 1;
        }
    }
}
