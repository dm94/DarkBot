package com.github.manolo8.darkbot.core.api.adapters;

import com.github.manolo8.darkbot.extensions.features.handlers.PetGearSelectorHandler;
import eu.darkbot.api.extensions.selectors.PetGearSupplier;
import eu.darkbot.api.managers.PetAPI;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void locatorSelectionDelegatesToTheActiveGearSupplier() {
        PetGearSelectorHandler handler = mock(PetGearSelectorHandler.class);
        PetGearSupplier supplier = mock(PetGearSupplier.class);
        PetAPI.LocatorPick advertised = mock(PetAPI.LocatorPick.class);
        PetAPI.LocatorPick selected = mock(PetAPI.LocatorPick.class);
        when(handler.getBestSupplier()).thenReturn(supplier);
        when(supplier.getNpcLocatorPick(any())).thenReturn(selected);

        assertSame(selected, UnityPacketAdapter.selectLocatorPick(handler, List.of(advertised)),
                "adapter uses the selected locator pick");
    }

    @Test
    void locatorSelectionFallsBackWhenFeaturesReload() {
        PetGearSelectorHandler handler = mock(PetGearSelectorHandler.class);
        when(handler.getBestSupplier()).thenThrow(new IllegalStateException("reloading"));

        assertNull(UnityPacketAdapter.selectLocatorPick(handler, List.of()),
                "packet UnityPetManager supplies the wire-target fallback");
    }
}
