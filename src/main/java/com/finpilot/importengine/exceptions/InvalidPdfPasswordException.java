package com.finpilot.importengine.exceptions;

/**
 * Thrown when a PDF is password-protected and either no password was provided
 * or the provided password was invalid.
 */
public class InvalidPdfPasswordException extends RuntimeException {
    public InvalidPdfPasswordException(String message) {
        super(message);
    }

    public InvalidPdfPasswordException(String message, Throwable cause) {
        super(message, cause);
    }
}
