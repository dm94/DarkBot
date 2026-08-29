package com.github.manolo8.darkbot.backpage;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.extensions.backpage.BackpageModule;
import eu.darkbot.api.managers.SkylabAPI;

import java.util.Objects;

/**
 * Functional Skylab task usable with the Unity packet API. It deliberately does
 * not open a client window or require BackpageManager.
 */
public final class SkylabTask implements BackpageModule {
    public static final String MODULE_ID = "skylab";
    private static final long RETRY_MS = 5_000;

    private final SkylabAPI skylab;
    private volatile State state = State.IDLE;
    private volatile long readyAtMs;
    private volatile String statusMessage = "";
    private volatile String moduleToView;
    private volatile boolean refreshRequested;
    private volatile Runnable stateListener = () -> {};

    public SkylabTask(SkylabAPI skylab) {
        this.skylab = Objects.requireNonNull(skylab, "skylab");
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public String getName() {
        return "Skylab";
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void setState(State state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    @Override
    public long getReadyAtMs() {
        return readyAtMs;
    }

    @Override
    public String getStatusMessage() {
        return statusMessage;
    }

    public SkylabAPI getSkylab() {
        return skylab;
    }

    public void setStateListener(Runnable listener) {
        stateListener = listener == null ? () -> {} : listener;
    }

    private void changed() {
        stateListener.run();
    }

    /** Requests the server to send the main Skylab view. */
    public void refresh() {
        refreshRequested = true;
        readyAtMs = 0;
    }

    /** Requests the detail view for a module on the next task tick. */
    public void viewModule(String moduleName) {
        if (moduleName == null || moduleName.trim().isEmpty())
            throw new IllegalArgumentException("moduleName must not be blank");
        moduleToView = moduleName.trim();
        readyAtMs = 0;
    }

    @Override
    public void install(PluginAPI pluginAPI) {
        // The SkylabAPI dependency is injected by the plugin/feature constructor.
    }

    @Override
    public void onTickTask() {
        if (moduleToView != null) {
            String requested = moduleToView;
            moduleToView = null;
            if (skylab.viewModule(requested)) {
                setState(State.COOLDOWN);
                statusMessage = "Requested module " + requested;
                readyAtMs = System.currentTimeMillis() + RETRY_MS;
                changed();
            } else {
                setState(State.ERROR);
                statusMessage = "Unable to request module " + requested;
                readyAtMs = System.currentTimeMillis() + RETRY_MS;
                changed();
            }
            return;
        }

        if (refreshRequested) {
            refreshRequested = false;
            // The packet API has no separate main-view request in the public contract;
            // querying a known module is the portable way to refresh its state.
            String selected = skylab.getSelectedModuleName().orElse(null);
            if (selected != null && skylab.viewModule(selected)) {
                setState(State.COOLDOWN);
                statusMessage = "Refreshing " + selected;
                readyAtMs = System.currentTimeMillis() + RETRY_MS;
                changed();
            } else {
                setState(State.IDLE);
                statusMessage = hasState() ? "Skylab state available" : "Waiting for Skylab state";
                changed();
            }
        } else if (state == State.COOLDOWN && System.currentTimeMillis() >= readyAtMs) {
            setState(State.IDLE);
        }
    }

    private boolean hasState() {
        return !skylab.getModules().isEmpty()
                || !skylab.getOreStorages().isEmpty()
                || skylab.getSelectedModuleName().isPresent();
    }

    @Override
    public boolean cancel() {
        boolean changed = moduleToView != null || refreshRequested;
        moduleToView = null;
        refreshRequested = false;
        readyAtMs = 0;
        if (changed) {
            setState(State.IDLE);
            changed();
        }
        return changed;
    }
}
