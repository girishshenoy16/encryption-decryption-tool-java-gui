package utility;

import exception.ValidationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Validates user input before encryption and decryption operations. */
public final class InputValidator {

    public static final int MINIMUM_PASSWORD_LENGTH = 12;

    private InputValidator() {
    }

    /** Validates non-empty source text. */
    public static void requireText(String value, String fieldName) throws ValidationException {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required.");
        }
    }

    /** Validates the password without storing it. */
    public static void requirePassword(char[] password) throws ValidationException {
        if (password == null || password.length == 0) {
            throw new ValidationException("Please enter a password before continuing.");
        }
        if (password.length < MINIMUM_PASSWORD_LENGTH) {
            throw new ValidationException("Password must contain at least " + MINIMUM_PASSWORD_LENGTH + " characters.");
        }
    }

    /** Validates that a selected path is a readable regular file. */
    public static void requireReadableRegularFile(Path path) throws ValidationException {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ValidationException("Select a readable regular file.");
        }
    }

    /** Creates and validates an output directory. */
    public static Path requireOutputDirectory(Path directory) throws IOException, ValidationException {
        if (directory == null) {
            throw new ValidationException("Output directory is required.");
        }
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory) || !Files.isWritable(directory)) {
            throw new ValidationException("Output directory is not writable.");
        }
        return directory.toAbsolutePath().normalize();
    }

    /** Validates that a name is a single safe file name rather than a path. */
    public static String requireSafeFileName(String name) throws ValidationException {
        if (name == null || name.isBlank() || Path.of(name).getNameCount() != 1 || name.equals(".") || name.equals("..")) {
            throw new ValidationException("The selected file name is invalid.");
        }
        return name;
    }
}
