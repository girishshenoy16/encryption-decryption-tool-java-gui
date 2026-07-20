package service;

import crypto.CryptoUtils;
import exception.CryptoException;
import exception.FileOperationCancelledException;
import exception.ValidationException;
import utility.InputValidator;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

/** Provides safe streamed file encryption and decryption operations. */
public final class FileEncryptionService {

    private static final String ENCRYPTED_EXTENSION = ".edt";
    private final Path encryptedDirectory;
    private final Path decryptedDirectory;

    /** Creates a service using the application's standard output directories. */
    public FileEncryptionService() {
        this(Path.of("encrypted_files"), Path.of("decrypted_files"));
    }

    /** Creates a service with explicit output directories, primarily for controlled application configuration. */
    public FileEncryptionService(Path encryptedDirectory, Path decryptedDirectory) {
        this.encryptedDirectory = Objects.requireNonNull(encryptedDirectory, "encryptedDirectory");
        this.decryptedDirectory = Objects.requireNonNull(decryptedDirectory, "decryptedDirectory");
    }

    /** Encrypts a selected file and writes the result as Base64 text into the encrypted output directory. */
    public Path encryptFile(Path input, char[] password, Predicate<Path> overwriteApproval)
            throws IOException, CryptoException, ValidationException, FileOperationCancelledException {
        InputValidator.requireReadableRegularFile(input);
        String fileName = InputValidator.requireSafeFileName(input.getFileName().toString());
        String baseName = fileName.replaceFirst("\\.[^.]+$", "");
        Path output = outputPath(encryptedDirectory, baseName + ".txt");
        if (Files.exists(output) && (overwriteApproval == null || !overwriteApproval.test(output))) {
            throw new FileOperationCancelledException();
        }
        Path temporary = Files.createTempFile(output.getParent(), "." + output.getFileName(), ".tmp");
        try {
            byte[] plain = Files.readAllBytes(input);
            String base64 = CryptoUtils.encryptText(new String(plain, StandardCharsets.UTF_8), password);
            try (OutputStream destination = new BufferedOutputStream(Files.newOutputStream(temporary), 64 * 1024)) {
                destination.write(base64.getBytes(StandardCharsets.UTF_8));
                destination.flush();
            }
            Arrays.fill(plain, (byte) 0);
            moveIntoPlace(temporary, output);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return output;
    }

    /** Decrypts an application encrypted file (.edt binary or .txt Base64) into the restored output directory. */
    public Path decryptFile(Path input, char[] password, Predicate<Path> overwriteApproval)
            throws IOException, CryptoException, ValidationException, FileOperationCancelledException {
        InputValidator.requireReadableRegularFile(input);
        String fileName = InputValidator.requireSafeFileName(input.getFileName().toString());
        if (fileName.endsWith(ENCRYPTED_EXTENSION) && fileName.length() > ENCRYPTED_EXTENSION.length()) {
            Path output = outputPath(decryptedDirectory, fileName.substring(0, fileName.length() - ENCRYPTED_EXTENSION.length()));
            transfer(input, output, password, overwriteApproval, false);
            return output;
        }
        if (fileName.endsWith(".txt")) {
            Path output = outputPath(decryptedDirectory, fileName.substring(0, fileName.length() - 4));
            if (Files.exists(output) && (overwriteApproval == null || !overwriteApproval.test(output))) {
                throw new FileOperationCancelledException();
            }
            Path temporary = Files.createTempFile(output.getParent(), "." + output.getFileName(), ".tmp");
            try {
                String base64 = Files.readString(input, StandardCharsets.UTF_8).trim();
                byte[] plain = CryptoUtils.decryptText(base64, password).getBytes(StandardCharsets.UTF_8);
                try (OutputStream destination = new BufferedOutputStream(Files.newOutputStream(temporary), 64 * 1024)) {
                    destination.write(plain);
                    destination.flush();
                }
                Arrays.fill(plain, (byte) 0);
                moveIntoPlace(temporary, output);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return output;
        }
        throw new ValidationException("Select a file with the " + ENCRYPTED_EXTENSION + " or .txt extension.");
    }

    private static Path outputPath(Path directory, String fileName) throws IOException, ValidationException {
        Path normalizedDirectory = InputValidator.requireOutputDirectory(directory);
        Path output = normalizedDirectory.resolve(InputValidator.requireSafeFileName(fileName)).normalize();
        if (!output.getParent().equals(normalizedDirectory)) {
            throw new ValidationException("Output path is invalid.");
        }
        return output;
    }

    private static void transfer(Path input, Path output, char[] password, Predicate<Path> overwriteApproval, boolean encrypt)
            throws IOException, CryptoException, FileOperationCancelledException {
        if (Files.exists(output) && (overwriteApproval == null || !overwriteApproval.test(output))) {
            throw new FileOperationCancelledException();
        }
        Path temporary = Files.createTempFile(output.getParent(), "." + output.getFileName(), ".tmp");
        try {
            try (BufferedInputStream source = new BufferedInputStream(Files.newInputStream(input), 64 * 1024);
                 BufferedOutputStream destination = new BufferedOutputStream(Files.newOutputStream(temporary), 64 * 1024)) {
                if (encrypt) {
                    CryptoUtils.encrypt(source, destination, password);
                } else {
                    CryptoUtils.decrypt(source, destination, password);
                }
            }
            moveIntoPlace(temporary, output);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveIntoPlace(Path temporary, Path output) throws IOException {
        try {
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
