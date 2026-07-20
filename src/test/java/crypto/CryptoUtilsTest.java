package crypto;

import exception.AuthenticationFailureException;
import exception.InvalidPayloadException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CryptoUtilsTest {

    @Test
    void encryptDecryptRoundTripPreservesText() throws Exception {
        char[] password = password();
        String encrypted = CryptoUtils.encryptText("A secure project note.", password);
        assertEquals("A secure project note.", CryptoUtils.decryptText(encrypted, password));
    }

    @Test
    void wrongPasswordFailsWithoutReturningPlaintext() throws Exception {
        String encrypted = CryptoUtils.encryptText("Protected", password());
        assertThrows(AuthenticationFailureException.class, () -> CryptoUtils.decryptText(encrypted, otherPassword()));
    }

    @Test
    void tamperedPayloadFailsAuthentication() throws Exception {
        byte[] payload = CryptoUtils.encrypt("Protected".getBytes(StandardCharsets.UTF_8), password());
        payload[payload.length - 1] ^= 1;
        assertThrows(AuthenticationFailureException.class, () -> CryptoUtils.decrypt(payload, password()));
    }

    @Test
    void invalidBase64IsRejected() {
        assertThrows(InvalidPayloadException.class, () -> CryptoUtils.decryptText("not base64!", password()));
    }

    @Test
    void malformedPayloadIsRejected() {
        assertThrows(InvalidPayloadException.class, () -> CryptoUtils.decrypt(new byte[0], password()));
    }

    @Test
    void unicodeAndSpecialCharactersRoundTrip() throws Exception {
        String plaintext = "Café ₹100 — सुरक्षित! @#%";
        assertEquals(plaintext, CryptoUtils.decryptText(CryptoUtils.encryptText(plaintext, password()), password()));
    }

    @Test
    void longTextRoundTrips() throws Exception {
        String plaintext = "encryption-".repeat(50_000);
        assertEquals(plaintext, CryptoUtils.decryptText(CryptoUtils.encryptText(plaintext, password()), password()));
    }

    @Test
    void repeatedEncryptionProducesDifferentPayloads() throws Exception {
        String first = CryptoUtils.encryptText("same text", password());
        String second = CryptoUtils.encryptText("same text", password());
        assertNotEquals(first, second);
        assertArrayEquals(Base64.getDecoder().decode(first), Base64.getDecoder().decode(first));
    }

    private static char[] password() {
        return "TestPassword#2026".toCharArray();
    }

    private static char[] otherPassword() {
        return "OtherPassword#2026".toCharArray();
    }
}
