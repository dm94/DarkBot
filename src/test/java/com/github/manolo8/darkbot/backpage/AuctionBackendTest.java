package com.github.manolo8.darkbot.backpage;

import eu.darkbot.unity.game.UnityAuctionManager;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AuctionBackendTest {
    @Test
    void unityBackendDoesNotUseHttpAndIsUnavailableWithoutSender() {
        UnityAuctionManager manager = new UnityAuctionManager();
        UnityAuctionBackend backend = new UnityAuctionBackend(manager);
        assertFalse(backend.isSolvingCaptcha());
        assertFalse(backend.update(0));
        assertTrue(backend.getData().getAuctionItems().isEmpty());
    }

    @Test
    void nullUnityBackendFailsSafely() {
        UnityAuctionBackend backend = new UnityAuctionBackend(null);
        assertFalse(backend.update(0));
        assertFalse(backend.bidItem(null, 1));
        assertTrue(backend.getData().getAuctionItems().isEmpty());
    }
}
