package com.github.manolo8.darkbot.backpage;

import com.github.manolo8.darkbot.backpage.auction.AuctionData;
import com.github.manolo8.darkbot.backpage.auction.AuctionItems;
import eu.darkbot.unity.game.UnityAuctionManager;

/** Bridges the packet-backed auction state to the existing auction task contract. */
public final class UnityAuctionBackend implements AuctionBackend {
    private final UnityAuctionManager unity;
    private final AuctionData data = new AuctionData();
    private volatile long lastRequest;

    public UnityAuctionBackend(UnityAuctionManager unity) {
        this.unity = unity;
    }

    @Override public AuctionData getData() { return data; }
    @Override public boolean isSolvingCaptcha() { return false; }

    @Override
    public Boolean update(long expiryTime) {
        if (unity == null) return false;
        if (expiryTime > 0 && System.currentTimeMillis() < lastRequest + expiryTime) return null;
        lastRequest = System.currentTimeMillis();
        return unity.requestList("all");
    }

    @Override
    public boolean bidItem(AuctionItems item, long amount) {
        if (unity == null || item == null || item.getAuctionType() == null) return false;
        return unity.bid(item.getLootId(), amount, item.getAuctionType().getId());
    }
}
