package com.github.manolo8.darkbot.backpage.auction;

import java.util.Objects;

public final class AuctionItemInfo {

    private final String id;
    private final String lootId;
    private final String name;
    private final String itemType;
    private final AuctionItems.Type type;
    private final long highestBidderId;
    private final long currentBid;
    private final long ownBid;
    private final long instantBuy;

    private AuctionItemInfo(AuctionItems item) {
        this.id = item.getId();
        this.lootId = item.getLootId();
        this.name = item.getName();
        this.itemType = item.getItemType();
        this.type = item.getAuctionType();
        this.highestBidderId = item.getHighestBidder();
        this.currentBid = item.getCurrentBid();
        this.ownBid = item.getOwnBid();
        this.instantBuy = item.getInstantBuy();
    }

    public static AuctionItemInfo of(AuctionItems item) {
        Objects.requireNonNull(item, "item");
        return new AuctionItemInfo(item);
    }

    public String getId() {
        return id;
    }

    public String getLootId() {
        return lootId;
    }

    public String getName() {
        return name;
    }

    public String getItemType() {
        return itemType;
    }

    public AuctionItems.Type getType() {
        return type;
    }

    public long getHighestBidderId() {
        return highestBidderId;
    }

    public long getCurrentBid() {
        return currentBid;
    }

    public long getOwnBid() {
        return ownBid;
    }

    public long getInstantBuy() {
        return instantBuy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuctionItemInfo)) return false;
        return Objects.equals(id, ((AuctionItemInfo) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "AuctionItemInfo{" +
                "id=" + id +
                ", name=" + name +
                ", type=" + type +
                ", currentBid=" + currentBid +
                ", ownBid=" + ownBid +
                ", instantBuy=" + instantBuy +
                '}';
    }
}
