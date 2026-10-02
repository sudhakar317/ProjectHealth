package com.clinic.common;

public class ConflictException extends RuntimeException {

    private final String field;

    /** @param field the request field to attach the message to, or null for a general conflict */
    public ConflictException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
