package com.github.manolo8.darkbot.modules;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.utils.I18n;
import eu.darkbot.api.game.entities.Portal;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.shared.modules.TemporalModule;
import eu.darkbot.shared.utils.PortalJumper;

public class TemporalPortalJumper extends TemporalModule {

    private final Main main;
    private final Portal target;
    private final PortalJumper portalJumper;

    public TemporalPortalJumper(Main main, Portal target) {
        super(main);
        this.main = main;
        this.target = target;
        this.portalJumper = new PortalJumper(main.pluginAPI);
    }

    @Override
    public boolean canRefresh() {
        return false;
    }

    @Override
    public String getStatus() {
        return I18n.get("module.portal_jumper.status", target.getTargetMap()
                .map(GameMap::getName)
                .orElse("(" + target.getLocationInfo().getLast().toString() + ")"));
    }

    @Override
    public String getStoppedStatus() {
        return getStatus();
    }

    @Override
    public void onTickModule() {
        // The Flash map panel records a recent click in Drive. Unity's packet-backed
        // portal selection comes from the live EntitiesAPI instead, so the native
        // movement-interruption flag is not meaningful there.
        boolean interrupted = !(Main.API instanceof com.github.manolo8.darkbot.core.api.adapters.UnityPacketAdapter)
                && main.hero.drive.movementInterrupted(500);
        if (interrupted || !target.isValid()) {
            goBack();
        } else portalJumper.travelAndJump(target);
    }

    @Override
    public void onTickStopped() {
        onTickModule();
    }
}
