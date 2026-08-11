package com.github.manolo8.darkbot.core.api.adapters;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnityPacketAdapterTest {

    @Test
    void modulesTickOnlyOnReadyLiveSessions() {
        assertTrue(UnityPacketAdapter.canTickModule(true, false));
        assertFalse(UnityPacketAdapter.canTickModule(false, false));
        assertFalse(UnityPacketAdapter.canTickModule(true, true));
    }
}
