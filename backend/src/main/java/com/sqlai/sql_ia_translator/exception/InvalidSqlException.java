package com.sqlai.sql_ia_translator.exception;

public class InvalidSqlException extends RuntimeException {

    public InvalidSqlException(String message) {
        super(message);
    }

    public InvalidSqlException(String message, Throwable cause) {
        super(message, cause);
    }
}
