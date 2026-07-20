package exception;

/** Thrown when AES-GCM authentication rejects a password or encrypted payload. */
public final class AuthenticationFailureException extends CryptoException {

    public AuthenticationFailureException() {
        super("The password is incorrect or the encrypted data has been modified.");
    }
}
