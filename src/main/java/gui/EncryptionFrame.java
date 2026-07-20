package gui;

import crypto.CryptoUtils;
import exception.FileOperationCancelledException;
import exception.ValidationException;
import service.FileEncryptionService;
import utility.InputValidator;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/** Main Swing window for text and file encryption workflows. */
public final class EncryptionFrame extends JFrame {

    private final JTextArea sourceArea = new JTextArea(10, 42);
    private final JTextArea resultArea = new JTextArea(10, 42);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JCheckBox showPassword = new JCheckBox("Show password");
    private final JTextField selectedFileField = new JTextField();
    private final JLabel statusLabel = new JLabel("Ready");
    private final JButton encryptButton = new JButton("Encrypt Text");
    private final JButton decryptButton = new JButton("Decrypt Text");
    private final JButton copyButton = new JButton("Copy Result");
    private final JButton saveButton = new JButton("Save Result");
    private final JButton clearButton = new JButton("Clear");
    private final JButton exitButton = new JButton("Exit");
    private final JButton selectFileButton = new JButton("Select File");
    private final JButton encryptFileButton = new JButton("Encrypt File");
    private final JButton decryptFileButton = new JButton("Decrypt File");
    private final FileEncryptionService fileService = new FileEncryptionService();
    private Path selectedFile;

    /** Creates the complete, non-blocking application layout. */
    public EncryptionFrame() {
        super("Encryption Decryption Tool");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(900, 720);
        setMinimumSize(new java.awt.Dimension(760, 600));
        configureComponents();
        wireActions();
        add(createContent());
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                requestExit();
            }
        });
    }

    private void configureComponents() {
        java.awt.Font unicodeFont = new java.awt.Font("Nirmala UI", java.awt.Font.PLAIN, 14);
        sourceArea.setFont(unicodeFont);
        resultArea.setFont(unicodeFont);
        passwordField.setFont(unicodeFont);
        selectedFileField.setFont(unicodeFont);
        sourceArea.setLineWrap(true);
        sourceArea.setWrapStyleWord(true);
        sourceArea.setToolTipText("Enter plaintext to encrypt or Base64 encrypted text to decrypt.");
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setToolTipText("The completed operation result appears here.");
        selectedFileField.setEditable(false);
        selectedFileField.setToolTipText("The selected file path.");
        passwordField.setToolTipText("Use a password with at least 12 characters.");
        showPassword.addActionListener(event -> passwordField.setEchoChar(showPassword.isSelected() ? (char) 0 : '•'));
        configureButtonTooltips();
        encryptFileButton.setEnabled(false);
        decryptFileButton.setEnabled(false);
    }

    private void configureButtonTooltips() {
        encryptButton.setToolTipText("Encrypt source text using AES-256-GCM.");
        decryptButton.setToolTipText("Decrypt Base64 source text after authentication.");
        copyButton.setToolTipText("Copy the result to the system clipboard.");
        saveButton.setToolTipText("Save the result as a UTF-8 text file.");
        clearButton.setToolTipText("Clear text, selected file, and password fields.");
        exitButton.setToolTipText("Exit the application safely.");
        selectFileButton.setToolTipText("Select a file to encrypt or decrypt.");
        encryptFileButton.setToolTipText("Encrypt the selected file in the background.");
        decryptFileButton.setToolTipText("Decrypt the selected .edt file in the background.");
    }

    private void wireActions() {
        encryptButton.addActionListener(event -> runTextOperation(true));
        decryptButton.addActionListener(event -> runTextOperation(false));
        selectFileButton.addActionListener(event -> selectFile());
        encryptFileButton.addActionListener(event -> runFileOperation(true));
        decryptFileButton.addActionListener(event -> runFileOperation(false));
        copyButton.addActionListener(event -> { resultArea.copy(); setStatus("Result copied to clipboard."); });
        saveButton.addActionListener(event -> saveResult());
        clearButton.addActionListener(event -> clearAll());
        exitButton.addActionListener(event -> requestExit());
    }

    private void runTextOperation(boolean encrypt) {
        char[] password = passwordField.getPassword();
        try {
            InputValidator.requireText(sourceArea.getText(), encrypt ? "Plaintext" : "Encrypted text");
            InputValidator.requirePassword(password);
            resultArea.setText(encrypt ? CryptoUtils.encryptText(sourceArea.getText(), password)
                    : CryptoUtils.decryptText(sourceArea.getText(), password));
            setStatus(encrypt ? "Text encrypted successfully." : "Text decrypted successfully.");
        } catch (Exception exception) {
            showFailure(exception);
        } finally {
            Arrays.fill(password, '\0');
            passwordField.setText("");
        }
    }

    private void selectFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            selectedFile = chooser.getSelectedFile().toPath();
            selectedFileField.setText(selectedFile.toString());
            encryptFileButton.setEnabled(false);
            decryptFileButton.setEnabled(false);
            clearFields();

            String fileName = selectedFile.getFileName().toString();
            if (fileName.endsWith(".edt")) {
                decryptFileButton.setEnabled(true);
                setStatus("Encrypted file selected, ready for decryption: " + fileName);
                return;
            }

            try {
                String content = Files.readString(selectedFile, StandardCharsets.UTF_8);
                if (isEncryptedContent(content)) {
                    sourceArea.setText("Loaded encrypted Base64 payload\n" + (content.length() > 200 ? content.substring(0, 200) + "..." : content));
                    decryptFileButton.setEnabled(true);
                    setStatus("Encrypted payload loaded, ready for decryption: " + fileName);
                } else {
                    sourceArea.setText(content);
                    if (content.length() > 10000) {
                        setStatus("Large text file loaded (showing first 10K chars): " + fileName);
                    } else {
                        setStatus("Text file loaded: " + fileName);
                    }
                    encryptFileButton.setEnabled(true);
                }
            } catch (Exception exception) {
                selectedFile = null;
                selectedFileField.setText("");
                encryptFileButton.setEnabled(false);
                decryptFileButton.setEnabled(false);
                String message = exception.getMessage() == null ? exception.toString() : exception.getMessage();
                setStatus("Unable to load file: " + message);
                JOptionPane.showMessageDialog(this, "Cannot read file content:\n" + message, "File Read Error", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private boolean isEncryptedContent(String text) {
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(text.trim());
            if (decoded.length < 4 + 1 + 16 + 12) return false;
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(decoded);
            byte[] magic = new byte[4];
            buffer.get(magic);
            return java.util.Arrays.equals(new byte[] { 'E', 'D', 'T', '1' }, magic);
        } catch (Exception e) {
            return false;
        }
    }

    private void runFileOperation(boolean encrypt) {
        char[] password = passwordField.getPassword();
        try {
            InputValidator.requirePassword(password);
            InputValidator.requireReadableRegularFile(selectedFile);
        } catch (Exception exception) {
            Arrays.fill(password, '\0');
            showFailure(exception);
            return;
        }
        setBusy(true);
        new SwingWorker<Path, Void>() {
            @Override protected Path doInBackground() throws Exception {
                return encrypt ? fileService.encryptFile(selectedFile, password, EncryptionFrame.this::approveOverwrite)
                        : fileService.decryptFile(selectedFile, password, EncryptionFrame.this::approveOverwrite);
            }
            @Override protected void done() {
                try {
                    Path output = get();
                    if (encrypt) {
                        try {
                            String encrypted = Files.readString(output, StandardCharsets.UTF_8);
                            resultArea.setText(encrypted.length() > 10000 ? encrypted.substring(0, 10000) + "\n[Output truncated for display]" : encrypted);
                        } catch (Exception readException) {
                            resultArea.setText("Encrypted file saved to: " + output.toAbsolutePath());
                        }
                        setStatus("File encrypted: " + output.getFileName());
                        JOptionPane.showMessageDialog(EncryptionFrame.this,
                                "Encrypted file saved to:\n" + output.toAbsolutePath(), "Encryption complete",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        try {
                            String decrypted = Files.readString(output, StandardCharsets.UTF_8);
                            resultArea.setText(decrypted.length() > 10000 ? decrypted.substring(0, 10000) + "\n[Output truncated for display]" : decrypted);
                        } catch (Exception readException) {
                            resultArea.setText("Decrypted file saved to: " + output.toAbsolutePath() + "\n(The content is binary and cannot be shown as text.)");
                        }
                        setStatus("File decrypted: " + output.getFileName());
                        JOptionPane.showMessageDialog(EncryptionFrame.this,
                                "Decrypted file saved to:\n" + output.toAbsolutePath()
                                        + "\n\nYour original file was not changed.", "Decryption complete",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception exception) {
                    showFailure(exception.getCause() == null ? exception : exception.getCause());
                } finally {
                    Arrays.fill(password, '\0'); passwordField.setText(""); setBusy(false);
                }
            }
        }.execute();
    }

    private boolean approveOverwrite(Path output) {
        AtomicBoolean approved = new AtomicBoolean(false);
        try {
            SwingUtilities.invokeAndWait(() -> approved.set(JOptionPane.showConfirmDialog(this,
                    "Replace existing file " + output.getFileName() + "?", "Confirm overwrite",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION));
        } catch (Exception exception) {
            return false;
        }
        return approved.get();
    }

    private void saveResult() {
        if (resultArea.getText().isEmpty()) { showFailure(new ValidationException("There is no result to save.")); return; }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Result as Text File");
        chooser.setApproveButtonText("Save");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path filePath = chooser.getSelectedFile().toPath();
            try {
                String fileName = filePath.getFileName().toString().toLowerCase();
                if (!fileName.endsWith(".txt")) { filePath = filePath.resolveSibling(fileName + ".txt"); }
                Files.writeString(filePath, resultArea.getText(), StandardCharsets.UTF_8);
                setStatus("Result saved to: " + filePath.getFileName());
            } catch (Exception exception) { showFailure(exception); }
        }
    }

    private void clearAll() { clearFields(); selectedFile = null; selectedFileField.setText(""); setStatus("Cleared."); }

    private void clearFields() { sourceArea.setText(""); resultArea.setText(""); passwordField.setText(""); encryptFileButton.setEnabled(false); decryptFileButton.setEnabled(false); }
    private void setBusy(boolean busy) { encryptButton.setEnabled(!busy); decryptButton.setEnabled(!busy); selectFileButton.setEnabled(!busy); encryptFileButton.setEnabled(!busy && selectedFile != null); decryptFileButton.setEnabled(!busy && selectedFile != null && selectedFile.getFileName().toString().endsWith(".edt")); }
    private void setStatus(String message) { statusLabel.setText(message); }
    private void showFailure(Throwable exception) { String message = exception instanceof FileOperationCancelledException ? "Operation cancelled." : exception.getMessage(); setStatus(message); JOptionPane.showMessageDialog(this, message, "Operation failed", JOptionPane.ERROR_MESSAGE); }

    private JPanel createContent() {
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(createTextPanel(), BorderLayout.CENTER);
        content.add(createControlsPanel(), BorderLayout.SOUTH);
        return content;
    }

    private JPanel createTextPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 10));
        panel.add(wrap("Source Text", "Plaintext for encryption or Base64 ciphertext for decryption.", sourceArea));
        panel.add(wrap("Result", "Authenticated plaintext or Base64 encrypted output.", resultArea));
        return panel;
    }

    private JPanel wrap(String title, String accessibleDescription, JTextArea area) {
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.getAccessibleContext().setAccessibleDescription(accessibleDescription);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(scrollPane);
        return panel;
    }

    private JPanel createControlsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(3, 3, 3, 3);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 0;
        addRow(panel, constraints, 0, new JLabel("Password:"), passwordField, showPassword);
        addRow(panel, constraints, 1, new JLabel("Selected File:"), selectedFileField, selectFileButton);
        JPanel textActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        textActions.add(encryptButton); textActions.add(decryptButton); textActions.add(copyButton); textActions.add(saveButton);
        JPanel fileActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fileActions.add(encryptFileButton); fileActions.add(decryptFileButton); fileActions.add(clearButton); fileActions.add(exitButton);
        addRow(panel, constraints, 2, new JLabel("Text Actions:"), textActions, new JLabel());
        addRow(panel, constraints, 3, new JLabel("File Actions:"), fileActions, new JLabel());
        addRow(panel, constraints, 4, new JLabel("Status:"), statusLabel, new JLabel());
        return panel;
    }

    private void addRow(JPanel panel, GridBagConstraints constraints, int row, java.awt.Component label,
                        java.awt.Component main, java.awt.Component trailing) {
        constraints.gridy = row; constraints.gridx = 0; constraints.weightx = 0; panel.add(label, constraints);
        constraints.gridx = 1; constraints.weightx = 1; panel.add(main, constraints);
        constraints.gridx = 2; constraints.weightx = 0; panel.add(trailing, constraints);
    }

    private void requestExit() {
        if (JOptionPane.showConfirmDialog(this, "Exit the application?", "Confirm Exit", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) dispose();
    }

    public JTextArea sourceArea() { return sourceArea; }
    public JTextArea resultArea() { return resultArea; }
    public JPasswordField passwordField() { return passwordField; }
    public JTextField selectedFileField() { return selectedFileField; }
    public JLabel statusLabel() { return statusLabel; }
    public JButton encryptButton() { return encryptButton; }
    public JButton decryptButton() { return decryptButton; }
    public JButton copyButton() { return copyButton; }
    public JButton saveButton() { return saveButton; }
    public JButton clearButton() { return clearButton; }
    public JButton exitButton() { return exitButton; }
    public JButton selectFileButton() { return selectFileButton; }
    public JButton encryptFileButton() { return encryptFileButton; }
    public JButton decryptFileButton() { return decryptFileButton; }
}
