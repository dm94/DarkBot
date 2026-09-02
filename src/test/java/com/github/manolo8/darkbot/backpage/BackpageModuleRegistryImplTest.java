package com.github.manolo8.darkbot.backpage;

import eu.darkbot.api.extensions.backpage.BackpageModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackpageModuleRegistryImplTest {

    @Test
    void registersFunctionalTaskModulesByStableId() {
        BackpageModuleRegistryImpl registry = new BackpageModuleRegistryImpl();
        TestModule first = new TestModule("skylab");
        TestModule duplicate = new TestModule("skylab");

        assertTrue(registry.register(first));
        assertFalse(registry.register(duplicate));
        assertEquals(first, registry.getModule(" skylab ").orElseThrow());
        assertEquals(1, registry.getModules().size());
        assertTrue(registry.unregister(first));
        assertTrue(registry.getModules().isEmpty());
    }

    @Test
    void rejectsBlankIdsAndNullLookupsAreSafe() {
        BackpageModuleRegistryImpl registry = new BackpageModuleRegistryImpl();
        assertThrows(IllegalArgumentException.class, () -> registry.register(new TestModule(" ")));
        assertTrue(registry.getModule(null).isEmpty());
    }

    private static final class TestModule implements BackpageModule {
        private final String id;
        private State state = State.IDLE;

        private TestModule(String id) {
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
        public void onTickTask() {
        }

        @Override
        public void install(eu.darkbot.api.PluginAPI pluginAPI) {
        }
    }
}
