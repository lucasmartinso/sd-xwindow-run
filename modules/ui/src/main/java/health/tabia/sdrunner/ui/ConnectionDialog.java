package health.tabia.sdrunner.ui;

import health.tabia.sdrunner.core.ConnectionEngine;
import health.tabia.sdrunner.core.model.ConnectionProfile;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.util.UUID;

/**
 * Modal form to create/edit a connection profile, with a "Test connection" button
 * that uses a disposable datasource (reused from core).
 */
public class ConnectionDialog extends JDialog {

    private final JTextField nameField = new JTextField(20);
    private final JTextField hostField = new JTextField(20);
    private final JTextField portField = new JTextField(20);
    private final JTextField dbField = new JTextField(20);
    private final JTextField userField = new JTextField(20);
    private final JPasswordField passField = new JPasswordField(20);

    private final ConnectionEngine engine;
    private ConnectionProfile result;

    public ConnectionDialog(Frame owner, ConnectionEngine engine, ConnectionProfile existing) {
        super(owner, existing == null ? "Nova conexão" : "Editar conexão", true);
        this.engine = engine;

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        addRow(form, c, 0, "Nome", nameField);
        addRow(form, c, 1, "Host", hostField);
        addRow(form, c, 2, "Porta", portField);
        addRow(form, c, 3, "Database", dbField);
        addRow(form, c, 4, "Usuário", userField);
        addRow(form, c, 5, "Senha", passField);

        ConnectionProfile base = existing != null ? existing : new ConnectionProfile(UUID.randomUUID().toString(), "Nova conexão");
        nameField.setText(base.getName());
        hostField.setText(base.getHost());
        portField.setText(String.valueOf(base.getPort()));
        dbField.setText(base.getDatabase());
        userField.setText(base.getUser());
        passField.setText(base.getPassword());

        JButton test = new JButton("Testar conexão");
        JButton save = new JButton("Salvar");
        JButton cancel = new JButton("Cancelar");

        test.addActionListener(e -> onTest());
        save.addActionListener(e -> {
            result = collect(base);
            dispose();
        });
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(test);
        buttons.add(save);
        buttons.add(cancel);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private ConnectionProfile collect(ConnectionProfile base) {
        base.setName(nameField.getText().trim());
        base.setHost(hostField.getText().trim());
        base.setPort(parsePort(portField.getText().trim()));
        base.setDatabase(dbField.getText().trim());
        base.setUser(userField.getText().trim());
        base.setPassword(new String(passField.getPassword()));
        return base;
    }

    private void onTest() {
        ConnectionProfile p = collect(new ConnectionProfile(UUID.randomUUID().toString(), "test"));
        try (ConnectionEngine.DisposableDataSource ds =
                     engine.createDisposable(p.jdbcUrl(), p.getUser(), p.getPassword(), p.getDriverClass());
             Connection conn = ds.dataSource().getConnection()) {
            boolean valid = conn.isValid(5);
            JOptionPane.showMessageDialog(this,
                    valid ? "Conexão OK" : "Conexão inválida",
                    "Teste", valid ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Falha: " + ex.getMessage(), "Teste", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static int parsePort(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 3306;
        }
    }

    private static void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
        c.fill = GridBagConstraints.NONE;
    }

    /** @return the edited profile, or null if cancelled. */
    public ConnectionProfile showDialog() {
        setVisible(true);
        return result;
    }
}
