package exception;

/** Thrown when a user declines to overwrite an existing output file. */
public final class FileOperationCancelledException extends Exception {

    public FileOperationCancelledException() {
        super("The file operation was cancelled.");
    }
}
