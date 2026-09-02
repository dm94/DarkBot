package com.github.manolo8.darkbot.gui.titlebar;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.backpage.AuctionModule;
import com.github.manolo8.darkbot.backpage.AuctionWindow;
import com.github.manolo8.darkbot.core.api.adapters.UnityPacketAdapter;
import com.github.manolo8.darkbot.gui.MainGui;

import javax.swing.*;

final class AuctionButton extends JButton {
    private AuctionWindow window;

    AuctionButton(Main main, MainGui frame) {
        super("Auction");
        setToolTipText("View auctions and place a bid manually");
        addActionListener(e -> open(main));
    }

    private void open(Main main) {
        AuctionModule module = null;
        if (Main.API instanceof UnityPacketAdapter) {
            UnityPacketAdapter adapter = (UnityPacketAdapter) Main.API;
            if (adapter.getAuctionBackend() != null)
                module = new AuctionModule(adapter.getAuctionBackend(), true);
        } else {
            module = new AuctionModule(main.backpage);
        }
        if (module == null) return;
        if (window == null) window = new AuctionWindow(module);
        window.show();
    }
}
