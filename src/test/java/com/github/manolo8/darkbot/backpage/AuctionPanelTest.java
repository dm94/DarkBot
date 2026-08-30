package com.github.manolo8.darkbot.backpage;

import eu.darkbot.unity.game.UnityAuctionManager;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuctionPanelTest {
    @Test
    void displaysUnityAuctionItems() {
        UnityAuctionManager unity = new UnityAuctionManager();
        unity.onList(Collections.singletonList(Map.of(
                "id", "laser", "group", "laser", "type", "hour",
                "highestBid", 100d, "myBid", 50d, "price", 500d)),
                Collections.emptyList(), Collections.emptyList());
        AuctionPanel panel = new AuctionPanel(new AuctionModule(new UnityAuctionBackend(unity), true));
        assertEquals(1, panel.getTable().getRowCount());
        assertEquals("laser", panel.getTable().getValueAt(0, 0));
    }
}
