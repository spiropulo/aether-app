package com.aether.app.estimate;

/**
 * Thrown when a user attempts to upload a PDF with a filename that already exists
 * for the tenant (new-project flow) or for the same project (import-into-project flow).
 */
public class DuplicateFileNameException extends RuntimeException {

    public DuplicateFileNameException(String tenantId, String fileName) {
        super(String.format("A file named '%s' already exists for this tenant.", fileName));
    }

    /** Same display name was already uploaded for this project (Import from PDF). */
    public static DuplicateFileNameException alreadyImportedForProject(String fileName) {
        return new DuplicateFileNameException(
                String.format(
                        "A file named '%s' was already uploaded for this project. Rename the file or wait until processing finishes.",
                        fileName));
    }

    private DuplicateFileNameException(String message) {
        super(message);
    }
}
