package com.github.manolo8.darkbot.backpage;

import eu.darkbot.api.managers.SkylabAPI;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Persistent bot-owned Skylab view; it never opens the Unity client screen. */
public final class SkylabPanel extends JPanel {
    private final SkylabTask task;
    private final SkylabAPI skylab;
    private final DefaultTableModel modulesModel = model("Creator", "Level", "State", "Efficiency", "Power");
    private final DefaultTableModel storageModel = model("Resource", "Amount", "Capacity");
    private final JTable modules = new JTable(modulesModel);
    private final JTable storage = new JTable(storageModel);
    private final JLabel status = new JLabel(" ");
    private final JLabel details = new JLabel(" ");
    private final JComboBox<String> transportMode = new JComboBox<>(new String[]{"send", "receive"});
    private final JTextField prometium = new JTextField("0");
    private final JTextField endurium = new JTextField("0");
    private final JTextField terbium = new JTextField("0");
    private final JTextField prometid = new JTextField("0");
    private final JTextField promerium = new JTextField("0");
    private final JTextField duranium = new JTextField("0");
    private final JTextField xenomit = new JTextField("0");
    private final JTextField seprom = new JTextField("0");

    public SkylabPanel(SkylabTask task) {
        super(new BorderLayout(8, 8));
        this.task = task;
        this.skylab = task.getSkylab();

        modules.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modules.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = modules.getSelectedRow();
                if (row >= 0 && row < modulesModel.getRowCount())
                    task.viewModule(String.valueOf(modulesModel.getValueAt(row, 0)));
            }
        });

        JPanel header = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> { task.refresh(); refreshView(); });
        actions.add(refresh);
        actions.add(status);
        header.add(actions, BorderLayout.NORTH);
        header.add(details, BorderLayout.SOUTH);

        JPanel transport = createTransportForm();
        JSplitPane tables = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(modules), new JScrollPane(storage));
        tables.setResizeWeight(0.58);
        add(header, BorderLayout.NORTH);
        add(tables, BorderLayout.CENTER);
        add(transport, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        refreshView();
    }

    private JPanel createTransportForm() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel form = new JPanel(new GridLayout(3, 6, 4, 2));
        form.setBorder(BorderFactory.createTitledBorder("Send resources / transport"));
        form.add(new JLabel("Mode"));
        form.add(transportMode);
        form.add(new JLabel(""));
        form.add(new JLabel(""));
        addField(form, "Prometium", prometium);
        addField(form, "Endurium", endurium);
        addField(form, "Terbium", terbium);
        addField(form, "Prometid", prometid);
        addField(form, "Promerium", promerium);
        addField(form, "Duranium", duranium);
        addField(form, "Xenomit", xenomit);
        addField(form, "Seprom", seprom);
        JButton send = new JButton("Send resources");
        send.setToolTipText("Send the selected resources to the selected Skylab module");
        send.addActionListener(e -> sendResources());
        panel.add(form, BorderLayout.CENTER);
        panel.add(send, BorderLayout.EAST);
        return panel;
    }

    private static void addField(JPanel form, String label, JTextField field) {
        form.add(new JLabel(label));
        form.add(field);
    }

    private void sendResources() {
        try {
            SkylabAPI.Action action = new SkylabAPI.Action(
                    "", String.valueOf(transportMode.getSelectedItem()), "send",
                    value(prometium), value(endurium), value(terbium), value(prometid),
                    value(promerium), value(duranium), value(xenomit), value(seprom));
            if (skylab.sendAction(action)) {
                task.refresh();
                status.setText("Transport requested");
            } else status.setText("Transport unavailable");
        } catch (IllegalArgumentException ex) {
            status.setText("Invalid resource amount");
        }
    }

    private static int value(JTextField field) {
        int result = Integer.parseInt(field.getText().trim());
        if (result < 0) throw new IllegalArgumentException("negative resource");
        return result;
    }

    /** Refreshes the visible snapshot; callers should invoke it on the EDT. */
    public void refreshView() {
        modulesModel.setRowCount(0);
        for (SkylabAPI.SkylabModule module : sortedModules()) {
            modulesModel.addRow(new Object[]{module.getType(), module.getLevel() + "/" + module.getMaxLevel(),
                    module.getState(), module.getEfficiency() + "%", module.getCurrentPower() + "/" + module.getMaxPower()});
        }
        storageModel.setRowCount(0);
        for (SkylabAPI.OreStorage ore : skylab.getOreStorages())
            storageModel.addRow(new Object[]{ore.getOreName(), ore.getCurrent(), ore.getCapacity()});
        status.setText(task.getStatusMessage().isEmpty() ? "State: " + task.getState() : task.getStatusMessage());
        StringBuilder summary = new StringBuilder("Storage efficiency: ").append(skylab.getStorageEfficiency());
        skylab.getProductivity().ifPresent(value -> summary.append(" | Productivity: ").append(value.getEfficiency())
                .append("% | robots ").append(value.getActive()).append('/').append(value.getMaxActive()));
        skylab.getCollectorInfo().ifPresent(value -> summary.append(" | Collector: ").append(value.getProduction())
                .append(" (remaining ").append(value.getLeftTime()).append(')'));
        skylab.getLastReceipt().ifPresent(value -> summary.append(" | Last result: ").append(value.getResultId()));
        details.setText(summary.toString());
    }

    private List<SkylabAPI.SkylabModule> sortedModules() {
        return new ArrayList<>(skylab.getModules());
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    public SkylabTask getTask() { return task; }

    public ListModel<String> getModulesModel() { return new AbstractListModel<String>() {
        @Override public int getSize() { return modulesModel.getRowCount(); }
        @Override public String getElementAt(int index) { return String.valueOf(modulesModel.getValueAt(index, 0)); }
    }; }

    public ListModel<String> getStorageModel() { return new AbstractListModel<String>() {
        @Override public int getSize() { return storageModel.getRowCount(); }
        @Override public String getElementAt(int index) { return String.valueOf(storageModel.getValueAt(index, 0)); }
    }; }
}
