package com.github.manolo8.darkbot.backpage;

import com.github.manolo8.darkbot.backpage.auction.AuctionData;
import com.github.manolo8.darkbot.backpage.auction.AuctionItems;

/** Backend used by the auction task; implementations may use HTTP or Unity packets. */
public interface AuctionBackend {
    AuctionData getData();
    boolean isSolvingCaptcha();
    Boolean update(long expiryTime);
    boolean bidItem(AuctionItems item, long amount);
}
