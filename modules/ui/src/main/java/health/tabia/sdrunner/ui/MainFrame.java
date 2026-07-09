package health.tabia.sdrunner.ui;

import health.tabia.sdrunner.core.ConnectionEngine;
import health.tabia.sdrunner.core.QueryRunner;
import health.tabia.sdrunner.core.RunningQueries;
import health.tabia.sdrunner.core.model.ConnectionProfile;
import health.tabia.sdrunner.provisioner.DockerMysqlProvisioner;
import health.tabia.sdrunner.provisioner.EnvironmentSpec;
import health.tabia.sdrunner.provisioner.MysqlProvisioner;
import health.tabia.sdrunner.provisioner.ProvisionResult;
import health.tabia.sdrunner.store.ProfileStore;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main window: connections (left), SQL editor (center), results table (bottom).
 * Fase 1 keeps profiles in memory; Fase 2 will load them from the encrypted store.
 */
public class MainFrame extends JFrame {

    private final DefaultListModel<ConnectionProfile> profiles = new DefaultListModel<>();
    private final JList<ConnectionProfile> profileList = new JList<>(profiles);
    private final JTextArea sqlArea = new JTextArea("SELECT 1 AS ok;", 6, 60);
    private final DefaultTableModel resultModel = new DefaultTableModel();
    private final JTable resultTable = new JTable(resultModel);
    private final JLabel status = new JLabel("Pronto");

    private final ConnectionEngine engine = new ConnectionEngine();
    private final RunningQueries running = new RunningQueries();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "query-worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicReference<String> currentExecId = new AtomicReference<>();
    private final ProfileStore store;
    private final MysqlProvisioner provisioner = new DockerMysqlProvisioner();
    private ConnectionProfile active;

    public MainFrame(ProfileStore store) {
        super("sd-runner — DB Studio");
        this.store = store;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(null);

        add(buildConnectionsPanel(), BorderLayout.WEST);
        add(buildCenter(), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        loadProfiles();
    }

    private void loadProfiles() {
        profiles.clear();
        try {
            store.listProfiles().forEach(profiles::addElement);
        } catch (Exception e) {
            setStatus("Falha ao carregar perfis: " + e.getMessage());
        }
    }

    private JComponent buildConnectionsPanel() {
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JButton add = new JButton("Nova");
        JButton edit = new JButton("Editar");
        JButton connect = new JButton("Conectar");
        JButton del = new JButton("Excluir");
        JButton provision = new JButton("Novo ambiente MySQL");

        add.addActionListener(e -> {
            ConnectionProfile p = new ConnectionDialog(this, engine, null).showDialog();
            if (p != null) {
                store.saveProfile(p);
                loadProfiles();
            }
        });
        edit.addActionListener(e -> {
            ConnectionProfile sel = profileList.getSelectedValue();
            if (sel != null) {
                ConnectionProfile edited = new ConnectionDialog(this, engine, sel).showDialog();
                if (edited != null) {
                    store.saveProfile(edited);
                    loadProfiles();
                }
            }
        });
        connect.addActionListener(e -> onConnect());
        del.addActionListener(e -> {
            ConnectionProfile sel = profileList.getSelectedValue();
            if (sel != null) {
                engine.remove(sel.getId());
                store.deleteProfile(sel.getId());
                loadProfiles();
            }
        });

        provision.addActionListener(e -> onProvision());

        JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
        buttons.add(add);
        buttons.add(edit);
        buttons.add(connect);
        buttons.add(del);
        buttons.add(provision);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Conexões"));
        panel.add(new JScrollPane(profileList), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(260, 0));
        return panel;
    }

    private JComponent buildCenter() {
        JButton run = new JButton("Executar (F5)");
        JButton abort = new JButton("Abortar");
        run.addActionListener(e -> onRun());
        abort.addActionListener(e -> onAbort());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(run);
        toolbar.add(abort);

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createTitledBorder("SQL"));
        top.add(toolbar, BorderLayout.NORTH);
        top.add(new JScrollPane(sqlArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createTitledBorder("Resultados"));
        bottom.add(new JScrollPane(resultTable), BorderLayout.CENTER);

        sqlArea.getInputMap().put(KeyStroke.getKeyStroke("F5"), "run");
        sqlArea.getActionMap().put("run", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { onRun(); }
        });

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(0.35);
        return split;
    }

    private void onConnect() {
        ConnectionProfile sel = profileList.getSelectedValue();
        if (sel == null) {
            setStatus("Selecione uma conexão");
            return;
        }
        try {
            engine.register(sel.getId(), sel.jdbcUrl(), sel.getUser(), sel.getPassword(), sel.getDriverClass());
            active = sel;
            setStatus("Conectado: " + sel);
        } catch (Exception ex) {
            setStatus("Falha ao conectar: " + ex.getMessage());
        }
    }

    private void onProvision() {
        EnvironmentSpec spec = new NewEnvironmentDialog(this).showDialog();
        if (spec == null) {
            return;
        }
        setStatus("Provisionando ambiente '" + spec.name() + "'...");
        worker.submit(() -> {
            try {
                ProvisionResult result = provisioner.create(spec);
                ConnectionProfile profile = result.toProfile();
                store.saveProfile(profile);
                SwingUtilities.invokeLater(() -> {
                    loadProfiles();
                    setStatus("Ambiente pronto: " + profile + " (porta " + result.port() + ")");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> setStatus("Falha ao provisionar: " + ex.getMessage()));
            }
        });
    }

    private void onRun() {
        if (active == null || engine.get(active.getId()).isEmpty()) {
            setStatus("Conecte-se a um perfil antes de executar");
            return;
        }
        String sql = sqlArea.getText().trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
        }
        boolean modifying = !sql.toLowerCase().startsWith("select") && !sql.toLowerCase().startsWith("show")
                && !sql.toLowerCase().startsWith("describe") && !sql.toLowerCase().startsWith("explain");
        final String finalSql = sql;
        final String execId = UUID.randomUUID().toString();
        currentExecId.set(execId);
        store.addHistory(active.getId(), finalSql, System.currentTimeMillis());
        resultModel.setRowCount(0);
        resultModel.setColumnCount(0);
        setStatus("Executando...");

        worker.submit(() -> QueryRunner.runQuery(
                modifying, finalSql, engine.get(active.getId()).orElseThrow(), running, execId, null, null,
                (n, columns) -> SwingUtilities.invokeLater(() -> resultModel.setColumnIdentifiers(columns.toArray())),
                (n, row) -> SwingUtilities.invokeLater(() -> resultModel.addRow(row.toArray())),
                () -> SwingUtilities.invokeLater(() -> setStatus("OK — " + resultModel.getRowCount() + " linha(s)")),
                e -> SwingUtilities.invokeLater(() -> setStatus("Erro: " + e.getMessage()))
        ));
    }

    private void onAbort() {
        String execId = currentExecId.get();
        if (execId != null) {
            running.abort(execId);
            setStatus("Abort solicitado");
        }
    }

    private void setStatus(String s) {
        status.setText(s);
    }
}
