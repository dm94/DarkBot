package com.github.manolo8.darkbot.backpage;

import javax.swing.*;
import java.awt.*;

/** Reusable window for manually inspecting auctions and placing bids. */
public final class AuctionWindow {
    private final JFrame frame;
    private final AuctionPanel panel;

    public AuctionWindow(AuctionModule module) {
        panel = new AuctionPanel(module);
        frame = new JFrame("Auction");
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        frame.setSize(700, 420);
        frame.setLocationByPlatform(true);
    }

    public void show() {
        panel.refreshView();
        frame.setVisible(true);
        frame.toFront();
    }

    public void refresh() { panel.refreshView(); }
    public AuctionPanel getPanel() { return panel; }
}
