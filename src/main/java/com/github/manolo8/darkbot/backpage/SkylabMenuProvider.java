package com.github.manolo8.darkbot.backpage;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.api.Capability;
import com.github.manolo8.darkbot.core.api.adapters.UnityPacketAdapter;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.extensions.ExtraMenus;
import eu.darkbot.api.managers.SkylabAPI;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import java.util.Collection;
import java.util.Collections;

/** Bot menu entry for the packet-backed Skylab functional task. */
public final class SkylabMenuProvider implements ExtraMenus {
    private static volatile SkylabWindow window;

    @Override
    public Collection<JComponent> getExtraMenuItems(PluginAPI api) {
        if (!(Main.API instanceof UnityPacketAdapter)
                || Main.API.hasCapability(Capability.LOGIN))
            return Collections.emptyList();

        SkylabAPI skylab = ((UnityPacketAdapter) Main.API).getSkylabManager();
        if (skylab == null) {
            JMenuItem unavailable = create("Skylab (session not ready)", e -> { });
            unavailable.setEnabled(false);
            return Collections.singletonList(unavailable);
        }

        // Do not resolve SkylabTask through the generic feature injector. The menu
        // is rebuilt independently and may run before feature registration finishes.
        SkylabTask task = new SkylabTask(skylab);
        return Collections.singletonList(create("Skylab", e -> show(task)));
    }

    static void show(SkylabTask task) {
        Runnable action = () -> {
            if (window == null) window = new SkylabWindow(task);
            window.show();
        };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) action.run();
        else javax.swing.SwingUtilities.invokeLater(action);
    }

    @Override
    public boolean autoSubmenu() {
        return false;
    }
}
