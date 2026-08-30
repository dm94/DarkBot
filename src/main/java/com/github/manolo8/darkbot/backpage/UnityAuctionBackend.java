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

    @Override
    public AuctionData getData() {
        syncData();
        return data;
    }
    @Override public boolean isSolvingCaptcha() { return false; }

    @Override
    public Boolean update(long expiryTime) {
        if (unity == null) return false;
        if (expiryTime > 0 && System.currentTimeMillis() < lastRequest + expiryTime) return null;
        lastRequest = System.currentTimeMillis();
        return unity.requestList();
    }

    @Override
    public boolean bidItem(AuctionItems item, long amount) {
        if (unity == null || item == null || item.getAuctionType() == null) return false;
        return unity.bid(item.getLootId(), amount, item.getAuctionType().getId());
    }

    private void syncData() {
        if (unity == null) return;
        data.getAuctionItems().clear();
        for (UnityAuctionManager.Item source : unity.getItems()) {
            AuctionItems target = new AuctionItems();
            target.setId(source.id);
            target.setLootId(source.group.isEmpty() ? source.id : source.group);
            target.setName(source.id);
            target.setItemType(source.type);
            target.setAuctionType(typeOf(source.type));
            target.setHighestBidderId(source.highestBidderId);
            target.setCurrentBid(asLong(source.highestBid != 0d ? source.highestBid : source.price));
            target.setOwnBid(asLong(source.myBid));
            target.setInstantBuy(asLong(source.price));
            data.getAuctionItems().put(target.getId(), target);
        }
    }

    private static AuctionItems.Type typeOf(String value) {
        if (value != null) {
            for (AuctionItems.Type type : AuctionItems.Type.values())
                if (type.getId().equalsIgnoreCase(value)) return type;
        }
        return AuctionItems.Type.HOUR;
    }

    private static long asLong(double value) {
        if (value <= 0d) return 0L;
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.round(value);
    }
}
