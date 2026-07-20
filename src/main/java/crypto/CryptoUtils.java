package crypto;

import exception.AuthenticationFailureException;
import exception.CryptoException;
import exception.InvalidPayloadException;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Provides versioned AES-256-GCM encryption and decryption using a password-derived key.
 */
public final class CryptoUtils {

    private static final byte[] MAGIC = {'E', 'D', 'T', '1'};
    private static final byte VERSION = 1;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int HEADER_LENGTH = MAGIC.length + 1 + SALT_LENGTH + IV_LENGTH;
    private static final int MINIMUM_CIPHERTEXT_LENGTH = TAG_LENGTH_BITS / Byte.SIZE;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtils() {
    }

    /**
     * Encrypts UTF-8 text and returns a Base64 representation of the versioned binary payload.
     *
     * @param plaintext text to encrypt
     * @param password password used only for this operation
     * @return Base64-encoded encrypted payload
     * @throws CryptoException when encryption cannot be completed
     */
    public static String encryptText(String plaintext, char[] password) throws CryptoException {
        if (plaintext == null) {
            throw new CryptoException("Text to encrypt is required.");
        }
        return Base64.getEncoder().encodeToString(encrypt(plaintext.getBytes(StandardCharsets.UTF_8), password));
    }

    /**
     * Decrypts a Base64 text payload and converts its authenticated plaintext from UTF-8.
     *
     * @param base64Payload Base64-encoded encrypted payload
     * @param password password used only for this operation
     * @return decrypted UTF-8 text
     * @throws CryptoException when the payload is invalid or authentication fails
     */
    public static String decryptText(String base64Payload, char[] password) throws CryptoException {
        if (base64Payload == null) {
            throw new InvalidPayloadException("Encrypted text is required.");
        }
        try {
            return new String(decrypt(Base64.getDecoder().decode(base64Payload), password), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPayloadException("Encrypted text must be valid Base64.", exception);
        }
    }

    /**
     * Encrypts bytes into the application versioned binary payload.
     *
     * @param plaintext bytes to encrypt
     * @param password password used only for this operation
     * @return binary payload containing header, salt, IV, and authenticated ciphertext
     * @throws CryptoException when encryption cannot be completed
     */
    public static byte[] encrypt(byte[] plaintext, char[] password) throws CryptoException {
        if (plaintext == null) {
            throw new CryptoException("Data to encrypt is required.");
        }
        validatePassword(password);

        byte[] salt = randomBytes(SALT_LENGTH);
        byte[] iv = randomBytes(IV_LENGTH);
        SecretKey key = null;
        try {
            key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(HEADER_LENGTH + ciphertext.length)
                    .put(MAGIC)
                    .put(VERSION)
                    .put(salt)
                    .put(iv)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("Encryption could not be completed.", exception);
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            clearKey(key);
        }
    }

    /**
     * Decrypts and authenticates an application versioned binary payload.
     *
     * @param payload binary payload containing header, salt, IV, and authenticated ciphertext
     * @param password password used only for this operation
     * @return authenticated plaintext bytes
     * @throws CryptoException when the payload is invalid or authentication fails
     */
    public static byte[] decrypt(byte[] payload, char[] password) throws CryptoException {
        validatePassword(password);
        PayloadParts parts = parsePayload(payload);
        SecretKey key = null;
        try {
            key = deriveKey(password, parts.salt());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, parts.iv()));
            return cipher.doFinal(parts.ciphertext());
        } catch (AEADBadTagException exception) {
            throw new AuthenticationFailureException();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("Decryption could not be completed.", exception);
        } finally {
            parts.clear();
            clearKey(key);
        }
    }

    /**
     * Streams encrypted data to an output stream using the application payload format.
     * Neither stream is closed by this method.
     *
     * @param input plaintext source stream
     * @param output encrypted destination stream
     * @param password password used only for this operation
     * @throws IOException when stream access fails
     * @throws CryptoException when encryption cannot be completed
     */
    public static void encrypt(InputStream input, OutputStream output, char[] password) throws IOException, CryptoException {
        validatePassword(password);
        byte[] salt = randomBytes(SALT_LENGTH);
        byte[] iv = randomBytes(IV_LENGTH);
        SecretKey key = null;
        try {
            key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            output.write(ByteBuffer.allocate(HEADER_LENGTH).put(MAGIC).put(VERSION).put(salt).put(iv).array());
            processCipherInput(input, output, cipher);
            output.flush();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("File encryption could not be completed.", exception);
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            clearKey(key);
        }
    }

    /**
     * Streams and authenticates an encrypted payload to an output stream.
     * Neither stream is closed by this method.
     *
     * @param input encrypted source stream
     * @param output plaintext destination stream
     * @param password password used only for this operation
     * @throws IOException when stream access fails
     * @throws CryptoException when the payload is invalid or authentication fails
     */
    public static void decrypt(InputStream input, OutputStream output, char[] password) throws IOException, CryptoException {
        validatePassword(password);
        PayloadParts header = readHeader(input);
        SecretKey key = null;
        try {
            key = deriveKey(password, header.salt());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, header.iv()));
            processCipherInput(input, output, cipher);
            output.flush();
        } catch (AEADBadTagException exception) {
            throw new AuthenticationFailureException();
        } catch (GeneralSecurityException exception) {
            throw new CryptoException("File decryption could not be completed.", exception);
        } finally {
            header.clear();
            clearKey(key);
        }
    }

    private static PayloadParts parsePayload(byte[] payload) throws InvalidPayloadException {
        if (payload == null || payload.length < HEADER_LENGTH + MINIMUM_CIPHERTEXT_LENGTH) {
            throw new InvalidPayloadException("Encrypted data is incomplete or invalid.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        if (!Arrays.equals(MAGIC, magic)) {
            throw new InvalidPayloadException("Encrypted data has an unsupported format.");
        }
        if (buffer.get() != VERSION) {
            throw new InvalidPayloadException("Encrypted data uses an unsupported version.");
        }
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        byte[] ciphertext = new byte[buffer.remaining() - SALT_LENGTH - IV_LENGTH];
        buffer.get(salt);
        buffer.get(iv);
        buffer.get(ciphertext);
        return new PayloadParts(salt, iv, ciphertext);
    }

    private static PayloadParts readHeader(InputStream input) throws IOException, InvalidPayloadException {
        byte[] header = input.readNBytes(HEADER_LENGTH);
        if (header.length != HEADER_LENGTH) {
            throw new InvalidPayloadException("Encrypted file is incomplete or invalid.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(header);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        if (!Arrays.equals(MAGIC, magic)) {
            throw new InvalidPayloadException("Encrypted file has an unsupported format.");
        }
        if (buffer.get() != VERSION) {
            throw new InvalidPayloadException("Encrypted file uses an unsupported version.");
        }
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        buffer.get(salt);
        buffer.get(iv);
        Arrays.fill(header, (byte) 0);
        return new PayloadParts(salt, iv, new byte[0]);
    }

    private static void processCipherInput(InputStream input, OutputStream output, Cipher cipher)
            throws IOException, GeneralSecurityException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            byte[] processed = cipher.update(buffer, 0, read);
            if (processed != null && processed.length > 0) {
                output.write(processed);
                Arrays.fill(processed, (byte) 0);
            }
        }
        Arrays.fill(buffer, (byte) 0);
        byte[] finalBytes = cipher.doFinal();
        try {
            if (finalBytes != null && finalBytes.length > 0) {
                output.write(finalBytes);
            }
        } finally {
            if (finalBytes != null) {
                Arrays.fill(finalBytes, (byte) 0);
            }
        }
    }

    private static SecretKey deriveKey(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec specification = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        try {
            byte[] encodedKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification)
                    .getEncoded();
            try {
                return new SecretKeySpec(encodedKey, "AES");
            } finally {
                Arrays.fill(encodedKey, (byte) 0);
            }
        } finally {
            specification.clearPassword();
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static void validatePassword(char[] password) throws CryptoException {
        if (password == null || password.length == 0) {
            throw new CryptoException("A password is required.");
        }
    }

    private static void clearKey(SecretKey key) {
        if (key != null && key.getEncoded() != null) {
            byte[] encoded = key.getEncoded();
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private record PayloadParts(byte[] salt, byte[] iv, byte[] ciphertext) {
        private void clear() {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(ciphertext, (byte) 0);
        }
    }
}
