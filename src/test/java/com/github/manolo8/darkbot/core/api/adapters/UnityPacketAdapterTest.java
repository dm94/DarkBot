package com.github.manolo8.darkbot.core.api.adapters;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnityPacketAdapterTest {

    @Test
    void actionConfirmationsAreTraceableWithoutTracingUnrelatedPackets() {
        assertTrue(UnityPacketAdapter.isTraceableInboundAction("RemoveCollectableCommand"));
        assertTrue(UnityPacketAdapter.isTraceableInboundAction("AttackHitCommand"));
        assertTrue(UnityPacketAdapter.isTraceableInboundAction("ShipDestroyedCommand"));
        assertFalse(UnityPacketAdapter.isTraceableInboundAction("ShipCreateCommand"));
        assertFalse(UnityPacketAdapter.isTraceableInboundAction("LoginResponse"));
    }

    @Test
    void modulesTickOnlyOnReadyLiveSessions() {
        assertTrue(UnityPacketAdapter.canTickModule(true, false));
        assertFalse(UnityPacketAdapter.canTickModule(false, false));
        assertFalse(UnityPacketAdapter.canTickModule(true, true));
    }
}
