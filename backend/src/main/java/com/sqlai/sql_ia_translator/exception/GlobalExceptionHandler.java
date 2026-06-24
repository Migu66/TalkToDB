package com.sqlai.sql_ia_translator.exception;

import com.sqlai.sql_ia_translator.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidSqlException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidSql(InvalidSqlException ex, HttpServletRequest request) {
        logger.warn("SQL rechazado: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ConnectionNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleConnectionNotFound(ConnectionNotFoundException ex, HttpServletRequest request) {
        logger.warn("Conexión no encontrada: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DatabaseConnectionException.class)
    public ResponseEntity<ErrorResponseDTO> handleDatabaseConnection(DatabaseConnectionException ex, HttpServletRequest request) {
        logger.error("Error de conexión a la base de datos", ex);
        return buildResponse(HttpStatus.BAD_GATEWAY, "No se pudo conectar a la base de datos", request);
    }

    @ExceptionHandler(OpenAiException.class)
    public ResponseEntity<ErrorResponseDTO> handleOpenAi(OpenAiException ex, HttpServletRequest request) {
        logger.error("Error al comunicarse con OpenAI", ex);
        return buildResponse(HttpStatus.BAD_GATEWAY, "Error al generar la consulta SQL", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneric(Exception ex, HttpServletRequest request) {
        logger.error("Error interno no controlado", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request);
    }

    private ResponseEntity<ErrorResponseDTO> buildResponse(HttpStatus status, String message, HttpServletRequest request) {
        var body = new ErrorResponseDTO(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
