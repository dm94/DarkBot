package com.github.manolo8.darkbot.backpage;

import com.github.manolo8.darkbot.backpage.auction.AuctionData;
import com.github.manolo8.darkbot.backpage.auction.AuctionItemInfo;
import com.github.manolo8.darkbot.backpage.auction.AuctionItems;
import eu.darkbot.api.extensions.backpage.BackpageModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionModuleTest {

    private final AuctionData data = new AuctionData();
    private final AuctionManager auction = mock(AuctionManager.class);
    private final AuctionModule module = new AuctionModule(auction);

    @BeforeEach
    void setUp() {
        when(auction.getData()).thenReturn(data);
    }

    @Test
    void exposesStableIdAndInitialState() {
        assertEquals(AuctionModule.MODULE_ID, module.getId());
        assertEquals("AuctionModule", module.getName());
        assertEquals(BackpageModule.State.IDLE, module.getState());
        assertEquals(0, module.getReadyAtMs());
        assertEquals("", module.getStatusMessage());
        assertFalse(module.isAutoRefresh());
    }

    @Test
    void refreshTriggersSingleUpdateAndSchedulesNext() {
        when(auction.isSolvingCaptcha()).thenReturn(false);
        when(auction.update(0L)).thenReturn(true);

        module.refresh();
        module.onTickTask();

        verify(auction, times(1)).update(0L);
        assertEquals(BackpageModule.State.IDLE, module.getState());
        assertTrue(module.getReadyAtMs() > System.currentTimeMillis());
        assertTrue(module.getReadyAtMs() <= System.currentTimeMillis() + module.getRefreshIntervalMs());

        module.onTickTask();

        verify(auction, times(1)).update(0L);
    }

    @Test
    void autoRefreshCyclesUpdatesAndRunsInRunningState() {
        when(auction.isSolvingCaptcha()).thenReturn(false);
        when(auction.update(0L)).thenReturn(true);
        module.setAutoRefresh(true);

        module.onTickTask();

        assertEquals(BackpageModule.State.RUNNING, module.getState());
        assertTrue(module.getReadyAtMs() > System.currentTimeMillis());

        module.onTickTask();

        verify(auction, times(2)).update(0L);
    }

    @Test
    void captchaPausesTickAndKeepsPendingBids() throws Exception {
        when(auction.isSolvingCaptcha()).thenReturn(true);
        when(auction.bidItem(any(AuctionItems.class), anyLong())).thenReturn(true);
        AuctionItems live = item("item_hour_5", AuctionItems.Type.HOUR);
        data.getAuctionItems().put(live.getId(), live);
        CompletableFuture<Boolean> future = module.bid(AuctionItemInfo.of(live), 5_000L);

        module.onTickTask();

        assertEquals(BackpageModule.State.CAPTCHA, module.getState());
        assertFalse(future.isDone());
        verify(auction, never()).update(0L);
        verify(auction, never()).bidItem(any(AuctionItems.class), anyLong());

        when(auction.isSolvingCaptcha()).thenReturn(false);
        module.onTickTask();

        assertTrue(future.isDone());
        assertTrue(future.get());
        verify(auction).bidItem(live, 5_000L);
    }

    @Test
    void updateFailureSchedulesRetryWithStatusMessage() {
        when(auction.isSolvingCaptcha()).thenReturn(false);
        when(auction.update(0L)).thenReturn(false);

        module.refresh();
        module.onTickTask();

        assertEquals("Auction update failed", module.getStatusMessage());
        assertTrue(module.getReadyAtMs() > System.currentTimeMillis() + 20_000);
        assertEquals(BackpageModule.State.IDLE, module.getState());
    }

    @Test
    void updateCaptchaResultMarksCaptchaState() {
        when(auction.update(0L)).thenReturn(false);
        when(auction.isSolvingCaptcha()).thenReturn(false, true);

        module.refresh();
        module.onTickTask();

        assertEquals(BackpageModule.State.CAPTCHA, module.getState());
        assertEquals("Captcha required", module.getStatusMessage());
    }

    @Test
    void bidRejectsInvalidInput() {
        assertThrows(NullPointerException.class, () -> module.bid(null, 100));
        assertThrows(IllegalArgumentException.class, () -> module.bid(AuctionItemInfo.of(item("i", AuctionItems.Type.DAY)), 0));
    }

    @Test
    void bidOnKnownItemCompletesWithManagerResult() throws Exception {
        when(auction.isSolvingCaptcha()).thenReturn(false);
        AuctionItems live = item("item_hour_5", AuctionItems.Type.HOUR);
        data.getAuctionItems().put(live.getId(), live);
        when(auction.bidItem(live, 5_000L)).thenReturn(true);

        CompletableFuture<Boolean> future = module.bid(AuctionItemInfo.of(live), 5_000L);
        assertFalse(future.isDone());

        module.onTickTask();

        assertTrue(future.isDone());
        assertTrue(future.get());
        verify(auction).bidItem(live, 5_000L);
    }

    @Test
    void bidOnMissingItemCompletesFalse() throws Exception {
        when(auction.isSolvingCaptcha()).thenReturn(false);
        AuctionItems gone = item("item_day_9", AuctionItems.Type.DAY);

        CompletableFuture<Boolean> future = module.bid(AuctionItemInfo.of(gone), 5_000L);
        module.onTickTask();

        assertTrue(future.isDone());
        assertFalse(future.get());
        assertTrue(module.getStatusMessage().contains("no longer listed"));
    }

    @Test
    void getItemsSnapshotIsImmutableAndIsolated() {
        AuctionItems hour = item("item_hour_5", AuctionItems.Type.HOUR);
        AuctionItems day = item("item_day_7", AuctionItems.Type.DAY);
        data.getAuctionItems().put(hour.getId(), hour);
        data.getAuctionItems().put(day.getId(), day);

        var all = module.getItems();
        var hours = module.getItems(AuctionItems.Type.HOUR);

        assertEquals(2, all.size());
        assertEquals(1, hours.size());
        assertEquals("Item item_hour_5", hours.get(0).getName());
        assertThrows(UnsupportedOperationException.class, () -> all.add(AuctionItemInfo.of(hour)));

        hour.setName("changed");
        assertEquals("Item item_hour_5", all.get(0).getName());
    }

    @Test
    void cancelClearsPendingBidsAndRefresh() {
        when(auction.isSolvingCaptcha()).thenReturn(false);
        AuctionItems live = item("item_hour_5", AuctionItems.Type.HOUR);
        CompletableFuture<Boolean> future = module.bid(AuctionItemInfo.of(live), 5_000L);
        module.refresh();

        assertTrue(module.cancel());
        assertTrue(future.isCancelled());

        module.onTickTask();

        verify(auction, never()).update(0L);
        verify(auction, never()).bidItem(any(AuctionItems.class), anyLong());
        assertFalse(module.cancel());
    }

    @Test
    void setRefreshIntervalRejectsNonPositiveValues() {
        assertThrows(IllegalArgumentException.class, () -> module.setRefreshIntervalMs(0));
        assertThrows(IllegalArgumentException.class, () -> module.setRefreshIntervalMs(-1));
    }

    private static AuctionItems item(String id, AuctionItems.Type type) {
        AuctionItems item = new AuctionItems();
        item.setId(id);
        item.setAuctionType(type);
        item.setName("Item " + id);
        item.setItemType("laser");
        item.setLootId("loot-" + id);
        item.setCurrentBid(1_000L);
        item.setOwnBid(0L);
        item.setInstantBuy(10_000L);
        item.setHighestBidderId(42L);
        return item;
    }
}
