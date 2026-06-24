package com.sqlai.sql_ia_translator.exception;

public class ConnectionNotFoundException extends RuntimeException {

    public ConnectionNotFoundException(String message) {
        super(message);
    }

    public ConnectionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
