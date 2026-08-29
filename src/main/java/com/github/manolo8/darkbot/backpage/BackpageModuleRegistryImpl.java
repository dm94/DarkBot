package com.github.manolo8.darkbot.backpage;

import eu.darkbot.api.extensions.backpage.BackpageModule;
import eu.darkbot.api.extensions.backpage.BackpageModuleRegistry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackpageModuleRegistryImpl implements BackpageModuleRegistry {

    private static final long INITIAL_DELAY_MS = 100;
    private static final long TICK_PERIOD_MS = 100;

    private final Map<String, BackpageModule> modules = new LinkedHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "backpage-modules");
        thread.setDaemon(true);
        return thread;
    });

    public BackpageModuleRegistryImpl() {
        scheduler.scheduleWithFixedDelay(this::tickModules, INITIAL_DELAY_MS, TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean register(BackpageModule module) {
        Objects.requireNonNull(module, "module");
        String id = Objects.requireNonNull(module.getId(), "module id").trim();
        if (id.isEmpty()) throw new IllegalArgumentException("module id must not be empty");
        synchronized (modules) {
            if (modules.containsKey(id)) return false;
            modules.put(id, module);
            return true;
        }
    }

    @Override
    public boolean unregister(BackpageModule module) {
        if (module == null) return false;
        synchronized (modules) {
            return modules.remove(module.getId(), module);
        }
    }

    @Override
    public Collection<BackpageModule> getModules() {
        synchronized (modules) {
            return List.copyOf(modules.values());
        }
    }

    @Override
    public Optional<BackpageModule> getModule(String id) {
        if (id == null) return Optional.empty();
        synchronized (modules) {
            return Optional.ofNullable(modules.get(id.trim()));
        }
    }

    private void tickModules() {
        Collection<BackpageModule> snapshot;
        synchronized (modules) {
            snapshot = List.copyOf(modules.values());
        }
        for (BackpageModule module : snapshot) {
            tickModule(module);
        }
    }

    private void tickModule(BackpageModule module) {
        if (System.currentTimeMillis() < module.getReadyAtMs()) return;
        try {
            module.onBackgroundTick();
            module.onTickTask();
        } catch (Throwable t) {
            module.setState(BackpageModule.State.ERROR);
            t.printStackTrace();
        }
    }
}
