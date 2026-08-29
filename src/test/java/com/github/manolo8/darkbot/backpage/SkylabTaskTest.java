package com.github.manolo8.darkbot.backpage;

import eu.darkbot.api.extensions.backpage.BackpageModule;
import eu.darkbot.api.managers.SkylabAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkylabTaskTest {
    @Test
    void requestsModuleThroughPacketApiAndEntersCooldown() {
        SkylabAPI api = mock(SkylabAPI.class);
        when(api.viewModule("prometium")).thenReturn(true);
        SkylabTask task = new SkylabTask(api);

        task.viewModule(" prometium ");
        task.onTickTask();

        assertEquals(BackpageModule.State.COOLDOWN, task.getState());
        assertTrue(task.getStatusMessage().contains("prometium"));
        assertTrue(task.getReadyAtMs() > System.currentTimeMillis());
    }

    @Test
    void failedRequestIsVisibleAndCanBeCancelled() {
        SkylabAPI api = mock(SkylabAPI.class);
        when(api.viewModule("xeno")).thenReturn(false);
        SkylabTask task = new SkylabTask(api);

        task.viewModule("xeno");
        task.onTickTask();
        assertEquals(BackpageModule.State.ERROR, task.getState());
        assertTrue(task.getStatusMessage().contains("Unable"));
        assertFalse(task.cancel());
        assertEquals(BackpageModule.State.ERROR, task.getState());
        assertFalse(task.cancel());
    }

    @Test
    void refreshReportsAvailabilityWhenNoModuleIsSelected() {
        SkylabAPI api = mock(SkylabAPI.class);
        when(api.getSelectedModuleName()).thenReturn(java.util.Optional.of("prometium"));
        SkylabTask task = new SkylabTask(api);

        task.refresh();
        task.onTickTask();

        assertEquals(BackpageModule.State.IDLE, task.getState());
        assertEquals("Skylab state available", task.getStatusMessage());
    }
}
