package com.github.manolo8.darkbot.backpage;

import com.github.manolo8.darkbot.backpage.auction.AuctionItemInfo;
import com.github.manolo8.darkbot.backpage.auction.AuctionItems;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/** Manual auction view modelled after Pikabot's tabs: auctions, own bids and history. */
public final class AuctionPanel extends JPanel {
    private final AuctionModule module;
    private final DefaultTableModel auctionModel = model("Item", "Type", "Highest bid", "My bid", "Buy now");
    private final DefaultTableModel bidsModel = model("Item", "Type", "Highest bid", "My bid", "Buy now");
    private final JTable auctionTable = new JTable(auctionModel);
    private final JTable bidsTable = new JTable(bidsModel);
    private final JTextField amount = new JTextField("0", 10);
    private final JComboBox<AuctionItems.Type> type = new JComboBox<>(AuctionItems.Type.values());
    private final JLabel status = new JLabel(" ");

    public AuctionPanel(AuctionModule module) {
        super(new BorderLayout(8, 8));
        this.module = module;
        auctionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bidsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton refresh = new JButton("Refresh auctions");
        refresh.addActionListener(e -> { module.refresh(); refreshView(); });
        JButton bid = new JButton("Place bid");
        bid.addActionListener(e -> placeBid(auctionTable));
        JButton quickBuy = new JButton("Buy now");
        quickBuy.addActionListener(e -> placeBid(auctionTable));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(refresh);
        controls.add(new JLabel("Tab:"));
        controls.add(type);
        controls.add(new JLabel("Bid amount:"));
        controls.add(amount);
        controls.add(bid);
        controls.add(quickBuy);
        controls.add(status);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Auctions", new JScrollPane(auctionTable));
        tabs.addTab("My bids", new JScrollPane(bidsTable));
        add(controls, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        refreshView();
    }

    private void placeBid(JTable source) {
        int row = source.getSelectedRow();
        if (row < 0) { status.setText("Select an auction"); return; }
        try {
            long value = Long.parseLong(amount.getText().trim());
            if (value <= 0) throw new NumberFormatException();
            List<AuctionItemInfo> items = module.getItems((AuctionItems.Type) type.getSelectedItem());
            if (row >= items.size()) { status.setText("Refresh the list"); return; }
            module.bid(items.get(row), value).thenAccept(ok -> SwingUtilities.invokeLater(() ->
                    status.setText(ok ? "Bid sent" : "Bid rejected")));
            status.setText("Sending bid...");
        } catch (NumberFormatException ex) {
            status.setText("Invalid bid amount");
        }
    }

    public void refreshView() {
        fill(auctionModel, module.getItems());
        fill(bidsModel, module.getItems());
        status.setText(module.isAvailable() ? module.getStatusMessage() : "Auction unavailable");
    }

    private static void fill(DefaultTableModel target, List<AuctionItemInfo> items) {
        target.setRowCount(0);
        for (AuctionItemInfo item : items)
            target.addRow(new Object[]{item.getName(), item.getType(), item.getCurrentBid(),
                    item.getOwnBid(), item.getInstantBuy()});
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    public JTable getTable() { return auctionTable; }
    public AuctionModule getModule() { return module; }
}
