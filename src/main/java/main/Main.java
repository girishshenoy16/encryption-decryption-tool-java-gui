package main;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import gui.EncryptionFrame;

/**
 * Application entry point for the Encryption Decryption Tool.
 */
public final class Main {

    private Main() {
    }

    /**
     * Starts the application on the Swing Event Dispatch Thread.
     *
     * @param args command-line arguments, currently unused
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            applySystemLookAndFeel();
            EncryptionFrame frame = new EncryptionFrame();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    private static void applySystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
            // Swing's default look and feel remains available if the system look and feel cannot be applied.
        }
    }
}
