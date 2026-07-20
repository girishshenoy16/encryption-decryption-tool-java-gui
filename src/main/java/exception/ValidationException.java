package exception;

/** Thrown when user input does not meet application validation requirements. */
public final class ValidationException extends Exception {

    public ValidationException(String message) {
        super(message);
    }
}
