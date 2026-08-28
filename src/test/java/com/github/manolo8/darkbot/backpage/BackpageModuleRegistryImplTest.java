package com.github.manolo8.darkbot.backpage;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.extensions.backpage.BackpageModule;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BackpageModuleRegistryImplTest {

    private final BackpageModuleRegistryImpl registry = new BackpageModuleRegistryImpl();

    @Test
    void registersAndLooksUpById() {
        FakeModule module = new FakeModule("auction");

        assertTrue(registry.register(module));
        assertTrue(registry.getModule("auction").isPresent());
        assertSame(module, registry.getModule("auction").orElse(null));
        assertEquals(1, registry.getModules().size());
    }

    @Test
    void duplicateIdIsRejectedAndFirstModuleWins() {
        FakeModule first = new FakeModule("auction");
        FakeModule second = new FakeModule("auction");

        assertTrue(registry.register(first));
        assertFalse(registry.register(second));
        assertSame(first, registry.getModule("auction").orElse(null));
        assertEquals(1, registry.getModules().size());
    }

    @Test
    void unregisterRemovesOnlyTheMatchingModule() {
        FakeModule module = new FakeModule("skylab");
        FakeModule other = new FakeModule("auction");

        registry.register(module);
        registry.register(other);

        assertFalse(registry.unregister(new FakeModule("skylab")));
        assertTrue(registry.unregister(module));
        assertFalse(registry.getModule("skylab").isPresent());
        assertTrue(registry.getModule("auction").isPresent());
        assertFalse(registry.unregister(module));
    }

    @Test
    void getModulesSnapshotIsImmutableAndUnaffectedByLaterChanges() {
        FakeModule module = new FakeModule("auction");
        registry.register(module);

        Collection<BackpageModule> snapshot = registry.getModules();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(new FakeModule("other")));

        registry.unregister(module);
        assertEquals(1, snapshot.size());
        assertTrue(registry.getModules().isEmpty());
    }

    @Test
    void ticksRegisteredModulesOnBothTaskMethods() {
        FakeModule module = new FakeModule("auction");
        registry.register(module);

        await(() -> module.ticks > 0);

        assertTrue(module.backgroundTicks > 0);
        assertNotSame(BackpageModule.State.ERROR, module.state);
    }

    @Test
    void throttlesModulesUntilReadyAtMs() throws InterruptedException {
        FakeModule module = new FakeModule("auction");
        module.readyAtMs = System.currentTimeMillis() + 400;
        registry.register(module);

        Thread.sleep(300);
        assertEquals(0, module.ticks);

        await(() -> module.ticks > 0);
    }

    @Test
    void failingModuleIsIsolatedAndMarkedAsError() {
        FakeModule failing = new FakeModule("failing");
        failing.failure = new IllegalStateException("boom");
        FakeModule healthy = new FakeModule("healthy");
        registry.register(failing);
        registry.register(healthy);

        await(() -> failing.ticks > 0 && healthy.ticks > 0);
        await(() -> healthy.ticks > 1);

        assertEquals(BackpageModule.State.ERROR, failing.state);
        assertNotSame(BackpageModule.State.ERROR, healthy.state);
    }

    @Test
    void registerRejectsNullOrMissingId() {
        assertThrows(NullPointerException.class, () -> registry.register(null));

        FakeModule noId = new FakeModule(null);
        assertThrows(NullPointerException.class, () -> registry.register(noId));
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("condition not met before deadline");
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for condition");
            }
        }
    }

    private static final class FakeModule implements BackpageModule {

        private final String id;
        private State state = State.IDLE;
        private long readyAtMs = 0;
        private int ticks;
        private int backgroundTicks;
        private RuntimeException failure;
        private boolean hasThrown;

        private FakeModule(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
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
        public void install(PluginAPI pluginAPI) {
        }

        @Override
        public void onTickTask() {
            ticks++;
            if (failure != null && !hasThrown) {
                hasThrown = true;
                throw failure;
            }
        }

        @Override
        public void onBackgroundTick() {
            backgroundTicks++;
        }
    }
}
