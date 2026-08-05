package com.finpilot.importengine.service;

/**
 * Thrown when a user re-uploads a byte-identical file. Maps to HTTP 409
 * Conflict at the controller layer — this is an expected, user-facing
 * condition, not a system error.
 */
public class DuplicateImportException extends RuntimeException {
    public DuplicateImportException(String message) {
        super(message);
    }
}
