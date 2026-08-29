package com.github.manolo8.darkbot.backpage;

import eu.darkbot.api.managers.SkylabAPI;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkylabPanelTest {
    @Test
    void refreshViewDisplaysModulesAndStorage() throws Exception {
        SkylabAPI api = new SkylabAPI() {
            @Override
            public java.util.Collection<SkylabModule> getModules() {
                return List.of(new SkylabModule("prometium", "running", 3, 20, 75, 1, 2));
            }

            @Override
            public java.util.Collection<OreStorage> getOreStorages() {
                return List.of(new OreStorage(0, "prometium", 12, 100));
            }
        };
        SkylabPanel[] panel = new SkylabPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new SkylabPanel(new SkylabTask(api)));

        assertEquals(1, panel[0].getModulesModel().getSize());
        assertEquals(1, panel[0].getStorageModel().getSize());
        assertEquals("prometium", panel[0].getModulesModel().getElementAt(0));
        assertEquals("prometium", panel[0].getStorageModel().getElementAt(0));
    }
}
