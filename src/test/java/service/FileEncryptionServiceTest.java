package service;

import exception.AuthenticationFailureException;
import exception.FileOperationCancelledException;
import exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileEncryptionServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void fileRoundTripPreservesBytes() throws Exception {
        Path input = writeInput("note.txt", "Café ₹100 — सुरक्षित".getBytes(StandardCharsets.UTF_8));
        FileEncryptionService service = service();
        Path encrypted = service.encryptFile(input, password(), ignored -> true);
        Path decrypted = service.decryptFile(encrypted, password(), ignored -> true);
        assertArrayEquals(Files.readAllBytes(input), Files.readAllBytes(decrypted));
    }

    @Test
    void missingFileIsRejected() {
        assertThrows(ValidationException.class, () -> service().encryptFile(temporaryDirectory.resolve("missing.txt"), password(), ignored -> true));
    }

    @Test
    void directoryIsRejected() {
        assertThrows(ValidationException.class, () -> service().encryptFile(temporaryDirectory, password(), ignored -> true));
    }

    @Test
    void corruptEncryptedFileIsRejectedWithoutRestoredOutput() throws Exception {
        Path input = writeInput("note.txt", "content".getBytes(StandardCharsets.UTF_8));
        FileEncryptionService service = service();
        Path encrypted = service.encryptFile(input, password(), ignored -> true);
        Files.write(encrypted, new byte[] {1, 2, 3});
        assertThrows(Exception.class, () -> service.decryptFile(encrypted, password(), ignored -> true));
        assertFalse(Files.exists(temporaryDirectory.resolve("decrypted").resolve("note.txt")));
    }

    @Test
    void overwriteRejectionCancelsOperation() throws Exception {
        Path input = writeInput("note.txt", "content".getBytes(StandardCharsets.UTF_8));
        FileEncryptionService service = service();
        service.encryptFile(input, password(), ignored -> true);
        assertThrows(FileOperationCancelledException.class, () -> service.encryptFile(input, password(), ignored -> false));
    }

    @Test
    void wrongPasswordLeavesNoPartialDecryptedFile() throws Exception {
        Path input = writeInput("note.txt", "content".getBytes(StandardCharsets.UTF_8));
        FileEncryptionService service = service();
        Path encrypted = service.encryptFile(input, password(), ignored -> true);
        assertThrows(AuthenticationFailureException.class, () -> service.decryptFile(encrypted, otherPassword(), ignored -> true));
        assertFalse(Files.exists(temporaryDirectory.resolve("decrypted").resolve("note.txt")));
    }

    private FileEncryptionService service() {
        return new FileEncryptionService(temporaryDirectory.resolve("encrypted"), temporaryDirectory.resolve("decrypted"));
    }

    private Path writeInput(String fileName, byte[] content) throws Exception {
        Path input = temporaryDirectory.resolve(fileName);
        Files.write(input, content);
        return input;
    }

    private static char[] password() {
        return "TestPassword#2026".toCharArray();
    }

    private static char[] otherPassword() {
        return "OtherPassword#2026".toCharArray();
    }
}
