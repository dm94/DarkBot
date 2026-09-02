package com.github.manolo8.darkbot.backpage;

import com.github.manolo8.darkbot.backpage.auction.AuctionItemInfo;
import com.github.manolo8.darkbot.backpage.auction.AuctionItems;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.extensions.backpage.BackpageModule;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class AuctionModule implements BackpageModule {

    public static final String MODULE_ID = "auction";

    private static final long DEFAULT_REFRESH_INTERVAL_MS = 60_000;
    private static final long FAILURE_RETRY_MS = 30_000;
    private static final long CAPTCHA_RETRY_MS = 15_000;

    private final AuctionBackend auctionManager;
    private final ConcurrentLinkedQueue<BidRequest> pendingBids = new ConcurrentLinkedQueue<>();

    private volatile State state = State.IDLE;
    private volatile boolean autoRefresh = false;
    private volatile boolean refreshRequested = false;
    private volatile long refreshIntervalMs = DEFAULT_REFRESH_INTERVAL_MS;
    private volatile long readyAtMs = 0;
    private volatile String statusMessage = "";

    public AuctionModule(BackpageManager backpageManager) {
        this(backpageManager == null ? null : backpageManager.getAuctionManager());
    }

    public AuctionModule(AuctionBackend auctionBackend, boolean unityBackend) {
        this(auctionBackend);
    }

    AuctionModule(AuctionBackend auctionManager) {
        this.auctionManager = auctionManager;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void setState(State state) {
        this.state = state;
    }

    @Override
    public long getReadyAtMs() {
        return readyAtMs;
    }

    @Override
    public String getStatusMessage() {
        return statusMessage;
    }

    @Override
    public void install(PluginAPI pluginAPI) {
    }

    /** Exposes whether this functional task has a usable HTTP backend. */
    public boolean isAvailable() {
        return auctionManager != null;
    }

    @Override
    public void onTickTask() {
        if (auctionManager == null) {
            setState(State.IDLE);
            statusMessage = "Auction unavailable without a browser session";
            return;
        }
        if (auctionManager.isSolvingCaptcha()) {
            setState(State.CAPTCHA);
            statusMessage = "Waiting for captcha solve";
            return;
        }
        executePendingBids();
        if (refreshRequested || autoRefresh) {
            refreshRequested = false;
            updateData();
        } else if (state != State.IDLE) {
            setState(State.IDLE);
        }
    }

    public boolean isAutoRefresh() {
        return autoRefresh;
    }

    public void setAutoRefresh(boolean autoRefresh) {
        this.autoRefresh = autoRefresh;
    }

    public long getRefreshIntervalMs() {
        return refreshIntervalMs;
    }

    public void setRefreshIntervalMs(long refreshIntervalMs) {
        if (refreshIntervalMs <= 0) throw new IllegalArgumentException("refreshIntervalMs must be positive");
        this.refreshIntervalMs = refreshIntervalMs;
    }

    public void refresh() {
        refreshRequested = true;
        readyAtMs = 0;
    }

    public CompletableFuture<Boolean> bid(AuctionItemInfo item, long amount) {
        Objects.requireNonNull(item, "item");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingBids.add(new BidRequest(item, amount, future));
        readyAtMs = 0;
        return future;
    }

    public List<AuctionItemInfo> getItems() {
        if (auctionManager == null) return Collections.emptyList();
        return auctionManager.getData().getAuctionItems().values().stream()
                .map(AuctionItemInfo::of)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    public List<AuctionItemInfo> getItems(AuctionItems.Type type) {
        Objects.requireNonNull(type, "type");
        if (auctionManager == null) return Collections.emptyList();
        return auctionManager.getData().getAuctionItems().values().stream()
                .filter(item -> item.getAuctionType() == type)
                .map(AuctionItemInfo::of)
                .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    @Override
    public boolean cancel() {
        boolean cancelled = false;
        BidRequest request;
        while ((request = pendingBids.poll()) != null) {
            request.future.cancel(true);
            cancelled = true;
        }
        refreshRequested = false;
        return cancelled;
    }

    private void executePendingBids() {
        BidRequest request;
        while ((request = pendingBids.poll()) != null) {
            request.future.complete(bidItem(request));
        }
    }

    private boolean bidItem(BidRequest request) {
        if (auctionManager == null) return false;
        AuctionItems live = auctionManager.getData().getAuctionItems().get(request.item.getId());
        if (live == null) {
            statusMessage = "Item no longer listed: " + request.item.getId();
            return false;
        }
        boolean placed = auctionManager.bidItem(live, request.amount);
        statusMessage = (placed ? "Bid placed: " : "Bid failed: ") + live.getName();
        return placed;
    }

    private void updateData() {
        Boolean result = auctionManager.update(0L);
        if (Boolean.TRUE.equals(result)) {
            setState(autoRefresh ? State.RUNNING : State.IDLE);
            statusMessage = "Auction updated";
            readyAtMs = System.currentTimeMillis() + refreshIntervalMs;
        } else if (auctionManager.isSolvingCaptcha()) {
            setState(State.CAPTCHA);
            statusMessage = "Captcha required";
            readyAtMs = System.currentTimeMillis() + CAPTCHA_RETRY_MS;
        } else {
            statusMessage = "Auction update failed";
            readyAtMs = System.currentTimeMillis() + FAILURE_RETRY_MS;
        }
    }

    private static final class BidRequest {

        private final AuctionItemInfo item;
        private final long amount;
        private final CompletableFuture<Boolean> future;

        private BidRequest(AuctionItemInfo item, long amount, CompletableFuture<Boolean> future) {
            this.item = item;
            this.amount = amount;
            this.future = future;
        }
    }
}
