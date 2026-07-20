package exception;

/** Thrown when encrypted data does not match the supported payload format. */
public final class InvalidPayloadException extends CryptoException {

    public InvalidPayloadException(String message) {
        super(message);
    }

    public InvalidPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
