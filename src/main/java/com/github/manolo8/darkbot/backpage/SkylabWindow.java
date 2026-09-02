package com.github.manolo8.darkbot.backpage;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;

/** Reusable bot-owned window for packet-backed Skylab management. */
public final class SkylabWindow {
    private final JFrame frame;
    private final SkylabPanel panel;

    public SkylabWindow(SkylabTask task) {
        panel = new SkylabPanel(task);
        task.setStateListener(() -> javax.swing.SwingUtilities.invokeLater(panel::refreshView));
        frame = new JFrame("Skylab");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        frame.setSize(520, 420);
        frame.setLocationByPlatform(true);
    }

    public void show() {
        panel.refreshView();
        panel.getTask().open();
        frame.setVisible(true);
        frame.toFront();
    }

    public void refresh() {
        panel.refreshView();
    }

    public SkylabPanel getPanel() {
        return panel;
    }
}
