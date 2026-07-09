package health.tabia.sdrunner.ui;

import health.tabia.sdrunner.store.MasterKeyManager;
import health.tabia.sdrunner.store.ProfileStore;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point: unlocks the encrypted store (master passphrase) then boots the Swing UI.
 * A SD_MASTER_PASSPHRASE env var auto-unlocks (useful for headless/demo runs).
 */
public final class App {

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // default L&F
        }

        Path stateDir = Path.of(System.getenv().getOrDefault("STATE_DIR",
                System.getProperty("user.home") + "/.sd-runner"));
        Files.createDirectories(stateDir);

        MasterKeyManager keys = new MasterKeyManager();
        ProfileStore store = new ProfileStore(stateDir.resolve("app.db"), keys);
        boolean firstRun = store.isFirstRun();

        String envPass = System.getenv("SD_MASTER_PASSPHRASE");

        SwingUtilities.invokeLater(() -> {
            String pass = envPass;
            if (pass == null || pass.isEmpty()) {
                pass = UnlockDialog.prompt(firstRun);
            }
            if (pass == null) {
                System.exit(0);
            }
            keys.unlock(pass);
            if (firstRun) {
                store.initVerifier();
            } else if (!store.verifyPassphrase()) {
                JOptionPane.showMessageDialog(null, "Passphrase incorreta.", "Erro", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            new MainFrame(store).setVisible(true);
        });
    }
}
