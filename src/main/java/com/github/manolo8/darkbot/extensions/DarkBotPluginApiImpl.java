package com.github.manolo8.darkbot.extensions;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.backpage.BackpageManager;
import com.github.manolo8.darkbot.backpage.BackpageModuleRegistryImpl;
import com.github.manolo8.darkbot.backpage.FlashResManager;
import com.github.manolo8.darkbot.backpage.NativeBrowserImpl;
import com.github.manolo8.darkbot.config.ConfigHandler;
import com.github.manolo8.darkbot.core.api.adapters.UnityPacketAdapter;
import com.github.manolo8.darkbot.core.manager.HeroManager;
import com.github.manolo8.darkbot.core.manager.MapManager;
import com.github.manolo8.darkbot.core.manager.PetManager;
import com.github.manolo8.darkbot.core.manager.RepairManager;
import com.github.manolo8.darkbot.core.manager.StarManager;
import com.github.manolo8.darkbot.core.manager.StatsManager;
import com.github.manolo8.darkbot.core.objects.facades.SlotBarsProxy;
import com.github.manolo8.darkbot.core.utils.Drive;
import com.github.manolo8.darkbot.core.utils.EntityList;
import com.github.manolo8.darkbot.extensions.features.FeatureRegistry;
import com.github.manolo8.darkbot.modules.utils.AttackAPIImpl;
import com.github.manolo8.darkbot.utils.LegacyModules;
import eu.darkbot.api.API;
import eu.darkbot.api.managers.EventBrokerAPI;
import eu.darkbot.impl.PluginApiImpl;
import eu.darkbot.impl.decorators.ListenerDecorator;
import eu.darkbot.impl.managers.EventBroker;
import eu.darkbot.impl.managers.I18n;
import org.jetbrains.annotations.NotNull;

public class DarkBotPluginApiImpl extends PluginApiImpl {

    public DarkBotPluginApiImpl(Main main) {
        addInstance(main, main.params, StarManager.getInstance(), main.configManager);
        addImplementations(
                BackpageManager.class,
                BackpageModuleRegistryImpl.class,
                EntityList.class,
                EventBroker.class,
                FeatureRegistry.class,
                FlashResManager.class,
                HeroManager.class,
                SlotBarsProxy.class,
                Drive.class,
                PetManager.class,
                RepairManager.class,
                MapManager.class,
                StatsManager.class,
                AttackAPIImpl.class,
                LegacyModules.class,
                I18n.class,
                ConfigHandler.class,
                NativeBrowserImpl.class);
        addDecorator(requireInstance(ListenerDecorator.class));
    }

    /**
     * Fase 4 DI swap: when the Unity packet adapter is the active API, {@code requireAPI}
     * for packet-backed managers resolves to the unity implementation instead of the memory
     * one. The direct lookup is deterministic (the {@code HashSet} singleton scan alone
     * would be a coin-flip, since the memory managers are registered as singletons during
     * {@code Main} init, before the adapter exists). Non-packet APIs and Flash mode fall
     * through to the normal resolution.
     */
    @Override
    public @NotNull <T extends API> T requireAPI(@NotNull Class<T> api) {
        if (Main.API instanceof UnityPacketAdapter) {
            UnityPacketAdapter unityAdapter = (UnityPacketAdapter) Main.API;
            T unity = unityAdapter.getManager(api);
            if (unity != null) return unity;
            // APIs without a Unity equivalent still resolve through the common API
            // registry. They remain explicitly unsupported by their Unity adapter and
            // never become packet state accidentally. Flash-only facades are blocked
            // explicitly so Unity cannot silently read native memory.
            if (api == eu.darkbot.api.managers.BackpageAPI.class
                    || api == eu.darkbot.api.managers.NativeBrowserAPI.class) {
                throw new UnsupportedOperationException("" + api.getSimpleName()
                        + " is unavailable in Unity packet mode");
            }
        }
        return super.requireAPI(api);
    }

    /**
     * Fase 4 DI swap, applied by the Unity adapter as soon as its packet pipeline is live:
     * registers the packet-backed managers for the singleton scan and re-points the feature
     * {@link ListenerDecorator} at the unity event broker, so module {@code @Subscribe}
     * handlers receive packet-derived events (the decorator would otherwise stay bound to
     * the memory broker resolved during {@code Main} init).
     *
     * <p>Synchronized on the DI creation lock ({@code getOrCreate} is synchronized on this
     * instance) so registering cannot race concurrent feature construction.
     */
    public synchronized void registerUnityManagers(EventBrokerAPI unityBroker,
                                                   API.Singleton... unityManagers) {
        addInstance(unityManagers);
        decorators.removeIf(ListenerDecorator.class::isInstance);
        decorators.add(new ListenerDecorator(unityBroker));
    }

}
