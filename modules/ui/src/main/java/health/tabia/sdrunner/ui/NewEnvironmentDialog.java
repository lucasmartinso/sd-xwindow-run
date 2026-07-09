package health.tabia.sdrunner.ui;

import health.tabia.sdrunner.provisioner.EnvironmentSpec;

import javax.swing.*;
import java.awt.*;

/** Collects parameters to provision a new MySQL environment (container). */
public class NewEnvironmentDialog extends JDialog {

    private final JTextField nameField = new JTextField("dev", 20);
    private final JTextField versionField = new JTextField("8", 20);
    private final JTextField dbField = new JTextField("app", 20);
    private final JPasswordField passField = new JPasswordField("dev", 20);
    private final JTextArea seedArea = new JTextArea(
            "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(32));\n"
                    + "INSERT INTO users VALUES (1,'alice'),(2,'bob');", 6, 30);
    private EnvironmentSpec result;

    public NewEnvironmentDialog(Frame owner) {
        super(owner, "Novo ambiente MySQL", true);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        row(form, c, 0, "Nome", nameField);
        row(form, c, 1, "Versão MySQL", versionField);
        row(form, c, 2, "Database", dbField);
        row(form, c, 3, "Senha root", passField);
        c.gridx = 0; c.gridy = 4; form.add(new JLabel("Seed (SQL)"), c);
        c.gridx = 1; form.add(new JScrollPane(seedArea), c);

        JButton create = new JButton("Provisionar");
        JButton cancel = new JButton("Cancelar");
        create.addActionListener(e -> {
            result = new EnvironmentSpec(
                    nameField.getText().trim(),
                    versionField.getText().trim(),
                    0,
                    new String(passField.getPassword()),
                    dbField.getText().trim(),
                    seedArea.getText());
            dispose();
        });
        cancel.addActionListener(e -> { result = null; dispose(); });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(create);
        buttons.add(cancel);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private static void row(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0; c.gridy = row; form.add(new JLabel(label), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; form.add(field, c);
        c.fill = GridBagConstraints.NONE;
    }

    public EnvironmentSpec showDialog() {
        setVisible(true);
        return result;
    }
}
