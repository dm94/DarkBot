package com.github.manolo8.darkbot.extensions;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.api.adapters.UnityPacketAdapter;
import eu.darkbot.api.API;
import eu.darkbot.api.events.Listener;
import eu.darkbot.api.managers.EventBrokerAPI;
import eu.darkbot.impl.decorators.ListenerDecorator;
import eu.darkbot.impl.managers.EventBroker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the Fase 4 DI swap in {@link DarkBotPluginApiImpl}: in Unity mode the packet-backed
 * managers must win the resolution for {@code eu.darkbot.api.*} APIs (deterministic routing
 * through the adapter, not the unordered singleton-set scan), and the feature
 * {@link ListenerDecorator} must be re-pointed at the unity event broker once the packet
 * pipeline is live.
 */
class DarkBotPluginApiImplTest {

    /** Exposes the protected DI internals (singletons/decorators) for assertions. */
    static class ExposedPluginApi extends DarkBotPluginApiImpl {
        ExposedPluginApi(Main main) {
            super(main);
        }

        boolean hasManager(API.Singleton manager) {
            return singletons.contains(manager);
        }

        ListenerDecorator listenerDecorator() {
            return decorators.stream()
                    .filter(ListenerDecorator.class::isInstance)
                    .map(ListenerDecorator.class::cast)
                    .findFirst()
                    .orElseThrow(AssertionError::new);
        }
    }

    @AfterEach
    void resetMainStatics() {
        Main.INSTANCE = null;
        Main.API = null;
    }

    @Test
    void requireAPIRoutesToUnityManagersWhenUnityAdapterIsActive() {
        UnityPacketAdapter adapter = mock(UnityPacketAdapter.class);
        EventBrokerAPI unityBroker = mock(EventBrokerAPI.class);
        when(adapter.getManager(EventBrokerAPI.class)).thenReturn(unityBroker);
        Main.API = adapter;

        ExposedPluginApi api = new ExposedPluginApi(mock(Main.class));

        assertSame(unityBroker, api.requireAPI(EventBrokerAPI.class));
        verify(adapter).getManager(EventBrokerAPI.class);
    }

    @Test
    void requireAPIFallsBackToMemoryResolutionOutsideUnityMode() {
        ExposedPluginApi api = new ExposedPluginApi(mock(Main.class));

        EventBrokerAPI broker = api.requireAPI(EventBrokerAPI.class);

        assertNotNull(broker);
        assertInstanceOf(EventBroker.class, broker);
    }

    @Test
    void registerUnityManagersRegistersManagersAndRebindsListenerDecorator() {
        ExposedPluginApi api = new ExposedPluginApi(mock(Main.class));
        EventBrokerAPI unityBroker = mock(EventBrokerAPI.class);
        API.Singleton fakeManager = mock(API.Singleton.class);

        api.registerUnityManagers(unityBroker, fakeManager);

        // The packet-backed manager becomes resolvable through the singleton scan.
        assertTrue(api.hasManager(fakeManager));

        // The (single) ListenerDecorator is now bound to the unity broker: decorating a
        // listener registers it with the broker that receives packet-derived events.
        Listener listener = mock(Listener.class);
        api.listenerDecorator().load(listener);
        verify(unityBroker).registerListener(listener);
    }

}
