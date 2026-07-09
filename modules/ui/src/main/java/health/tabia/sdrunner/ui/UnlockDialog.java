package health.tabia.sdrunner.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Prompts for the master passphrase. On first run, asks to define it (with confirmation).
 */
public final class UnlockDialog {

    private UnlockDialog() {
    }

    /** @return the passphrase, or null if the user cancelled. */
    public static String prompt(boolean firstRun) {
        JPasswordField pass = new JPasswordField(24);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0;
        panel.add(new JLabel(firstRun ? "Defina a passphrase mestra:" : "Passphrase mestra:"), c);
        c.gridx = 1;
        panel.add(pass, c);

        JPasswordField confirm = new JPasswordField(24);
        if (firstRun) {
            c.gridx = 0; c.gridy = 1;
            panel.add(new JLabel("Confirme:"), c);
            c.gridx = 1;
            panel.add(confirm, c);
        }

        while (true) {
            int result = JOptionPane.showConfirmDialog(null, panel,
                    firstRun ? "Definir passphrase" : "Desbloquear",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return null;
            }
            String p = new String(pass.getPassword());
            if (p.isEmpty()) {
                JOptionPane.showMessageDialog(null, "A passphrase não pode ser vazia.");
                continue;
            }
            if (firstRun && !p.equals(new String(confirm.getPassword()))) {
                JOptionPane.showMessageDialog(null, "As passphrases não conferem.");
                continue;
            }
            return p;
        }
    }
}
