package com.aether.app.estimate;

/**
 * Thrown when a user attempts to upload a PDF with a filename that already exists for the tenant.
 */
public class DuplicateFileNameException extends RuntimeException {

    public DuplicateFileNameException(String tenantId, String fileName) {
        super(String.format("A file named '%s' already exists for this tenant.", fileName));
    }
}
