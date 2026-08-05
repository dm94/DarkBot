package com.github.manolo8.darkbot.core.api.adapters;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.BotInstaller;
import com.github.manolo8.darkbot.core.api.GameAPI;
import com.github.manolo8.darkbot.core.api.GameAPIImpl;
import com.github.manolo8.darkbot.core.entities.Box;
import com.github.manolo8.darkbot.core.entities.Entity;
import com.github.manolo8.darkbot.utils.StartupParams;
import eu.darkbot.api.API;
import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.EventBrokerAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.InventoryAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.RepairAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.api.managers.StatsAPI;
import eu.darkbot.unity.codec.PacketRegistry;
import eu.darkbot.unity.game.EntitiesManager;
import eu.darkbot.unity.game.EventBroker;
import eu.darkbot.unity.game.HeroManager;
import eu.darkbot.unity.game.InventoryManager;
import eu.darkbot.unity.game.OreManager;
import eu.darkbot.unity.game.RepairManager;
import eu.darkbot.unity.game.StarSystemManager;
import eu.darkbot.unity.game.StatsManager;
import eu.darkbot.unity.game.UnityGameState;
import eu.darkbot.unity.net.PacketSender;
import eu.darkbot.unity.session.BigPointPortalHandler;
import eu.darkbot.unity.session.GameConnection;
import eu.darkbot.unity.session.MapServerTable;
import eu.darkbot.unity.session.PortalLoginProvider;
import eu.darkbot.unity.session.SavedAccount;
import eu.darkbot.unity.session.SavedSessionProvider;
import eu.darkbot.unity.session.SessionConnector;
import eu.darkbot.unity.session.SessionHttpClient;
import eu.darkbot.unity.session.SessionIdentity;

import java.io.IOException;
import java.util.function.LongPredicate;

/**
 * Packet-based API adapter (Camino A, Fase 4): runs the bot against the official Unity
 * client through the {@code unity-transport}/{@code unity-game} protocol stack instead of
 * reading the Flash player's memory.
 *
 * <p>The adapter owns the whole Unity session: it builds a {@link SessionConnector} (portal
 * login or saved SID → map server → {@link GameConnection}) and feeds every server→client
 * frame into a {@link UnityGameState} pipeline. Outbound actions (movement, lock, attack,
 * formation, box collection) flow back through a {@link PacketSender} wired to the live
 * connection — the modules drive them through the swapped {@code eu.darkbot.api.*} managers
 * (see the Fase 4 DI swap in {@code DarkBotPluginApiImpl}).
 *
 * <p><b>Validity.</b> The adapter is intentionally <i>not</i> background-only: it publishes
 * the bot validity through {@link BotInstaller#invalid} so {@code Main}'s normal
 * {@code validTick()} path runs and modules tick. The legacy memory managers no-op on the
 * NoOp memory (their install never succeeds), and the seven address {@link
 * com.github.manolo8.darkbot.core.utils.Lazy}s are pinned to {@code 0} so {@code
 * BotInstaller#isInvalid} does not dereference null. A daemon poller flips {@code invalid}
 * to {@code false} once the session is {@link GameState#isMapActive() READY} and the hero
 * snapshot arrived, and back to {@code true} when the session drops.
 *
 * <p><b>Credentials.</b> Read from the {@code -login} properties file (see {@link
 * StartupParams.AutoLoginProps}): {@code username+password} → portal login
 * ({@link BigPointPortalHandler}); {@code server+sid} → saved-session reconnect
 * ({@link SavedSessionProvider}). Without them the adapter stays invalid and logs the
 * reason. The Flash login dialog / preloader flow is deliberately not reused.
 */
public class UnityPacketAdapter extends GameAPIImpl<
        UnityPacketAdapter.NoOpWindow,
        UnityPacketAdapter.NoOpHandler,
        UnityPacketAdapter.NoOpMemory,
        UnityPacketAdapter.NoOpExtraMemoryReader,
        UnityPacketAdapter.NoOpInteraction,
        UnityPacketAdapter.UnityDirectInteraction> {

    /** Unity client version sent in the VersionRequest handshake (harness default). */
    public static final String UNITY_CLIENT_VERSION = "1.1.106";
    /** Initial map id (portal jumps re-resolve in a later iteration). */
    public static final int MAP_ID = 1;
    /** How often the session state is re-published to {@code BotInstaller.invalid}. */
    private static final long VALIDITY_POLL_MS = 500;

    private volatile SessionConnector connector;
    private volatile UnityGameState game;

    private BotInstaller botInstaller;

    public UnityPacketAdapter(StartupParams params) {
        super(params,
                new NoOpWindow(),
                new NoOpHandler(),
                new NoOpMemory(),
                new NoOpExtraMemoryReader(),
                new NoOpInteraction(),
                new UnityDirectInteraction());

        // Wire the components that need the adapter instance. They are static (so they can be
        // constructed in the super() call) and lazily read this reference; their callbacks only
        // fire from the tick loop, long after this constructor completes.
        ((NoOpHandler) handler).adapter = this;
        ((UnityDirectInteraction) direct).adapter = this;

        // Pin the memory-install addresses so BotInstaller.isInvalid()'s non-null branch
        // evaluates with zero addresses (readLong(1344) == mainAddress(0)) instead of NPE-ing
        // on the null Lazy values.
        botInstaller = Main.INSTANCE.pluginAPI.requireInstance(BotInstaller.class);
        botInstaller.mainApplicationAddress.send(0L);
        botInstaller.mainAddress.send(0L);
        botInstaller.screenManagerAddress.send(0L);
        botInstaller.guiManagerAddress.send(0L);
        botInstaller.heroInfoAddress.send(0L);
        botInstaller.settingsAddress.send(0L);
        botInstaller.connectionManagerAddress.send(0L);

        startSession();
    }

    @Override
    public String getVersion() {
        return "unity-packets " + UNITY_CLIENT_VERSION;
    }

    /** Route a legacy {@code API.moveShip} call to the Unity movement manager. */
    private void moveShipUnity(Locatable destination) {
        UnityGameState g = game;
        if (g != null && isSessionReady()) {
            g.getMovement().moveTo(destination.getX(), destination.getY());
        }
    }

    /** True when the map session is live and the hero snapshot has arrived. */
    private boolean isSessionReady() {
        SessionConnector c = connector;
        if (c == null) return false;
        GameConnection conn = c.connection();
        UnityGameState g = game;
        return conn != null && conn.state().isMapActive() && g != null && g.getHero().isValid();
    }

    /**
     * The Fase 4 DI swap: in Unity mode, modules requesting these APIs get the
     * packet-backed manager instead of the memory one. {@code DarkBotPluginApiImpl}
     * routes {@code requireAPI} calls here (the singletons {@link java.util.HashSet} scan
     * alone would be a coin-flip, since the memory managers are registered as singletons
     * at {@code Main} init, before this adapter exists).
     *
     * @return the unity manager for {@code api}, or {@code null} if the session pipeline
     *         is not up yet or the API is not packet-backed (falls back to the memory impl)
     */
    @SuppressWarnings("unchecked")
    public <T extends API> T getManager(Class<T> api) {
        UnityGameState g = game;
        if (g == null) return null;
        if (api == HeroAPI.class) return (T) g.getHero();
        if (api == EntitiesAPI.class) return (T) g.getEntities();
        if (api == StarSystemAPI.class) return (T) g.getStarSystem();
        if (api == EventBrokerAPI.class) return (T) g.getEventBroker();
        if (api == StatsAPI.class) return (T) g.getStats();
        if (api == RepairAPI.class) return (T) g.getRepair();
        if (api == OreAPI.class) return (T) g.getOres();
        if (api == InventoryAPI.class) return (T) g.getInventory();
        if (api == MovementAPI.class) return (T) g.getMovement();
        if (api == AttackAPI.class) return (T) g.getAttack();
        return null;
    }

    /**
     * Starts the Unity session on a daemon worker: resolves credentials (portal auto-detect
     * may take several POSTs), builds the game-state pipeline and connector, then keeps
     * {@code BotInstaller.invalid} in sync with the session state.
     */
    private void startSession() {
        StartupParams.AutoLoginProps props = params.getAutoLoginProps();
        if (props == null) {
            System.out.println("[unity] No -login properties found: UnityPacketAdapter needs username/password"
                    + " (or server+sid) in a login properties file to connect.");
            return;
        }

        Thread worker = new Thread(() -> {
            try {
                String server = props.getServer();
                String user = props.getUsername();
                String pass = props.getPassword();
                String dosid = props.getSID();

                SessionHttpClient http = new SessionHttpClient();
                http.setUnityMode(true, UNITY_CLIENT_VERSION);

                if ((server == null || server.isEmpty()) && user != null && !user.isEmpty()) {
                    System.out.println("[unity] Detecting account server (one POST per known portal)…");
                    server = BigPointPortalHandler.detectServer(http, user, pass, null);
                }
                if (server == null || server.isEmpty()) {
                    System.out.println("[unity] No server known: set server=<universe> in the login properties file.");
                    return;
                }
                String lang = BigPointPortalHandler.langFor(server);
                System.out.println("[unity] Connecting to " + server + " (lang=" + lang + ", version="
                        + UNITY_CLIENT_VERSION + ", map " + MAP_ID + ")");

                SessionConnector.LoginProvider provider;
                SessionConnector.LoginMethod method;
                if (dosid != null && !dosid.isEmpty()) {
                    String sid = BigPointPortalHandler.sidFromDosid(http, server, lang, dosid);
                    SessionIdentity identity = new SessionIdentity();
                    identity.setServer(server);
                    identity.setPlatform(SessionConnector.PLATFORM_UNITY);
                    identity.setSid(sid);
                    SavedAccount account = new SavedAccount();
                    account.server = server;
                    account.dosid = dosid;
                    account.lastMethod = "SID";
                    provider = new SavedSessionProvider(identity, account, UNITY_CLIENT_VERSION, MAP_ID);
                    method = SessionConnector.LoginMethod.SID;
                } else if (user != null && !user.isEmpty()) {
                    BigPointPortalHandler portal = new BigPointPortalHandler(http, server, lang, user, pass);
                    provider = new PortalLoginProvider(portal, new SessionIdentity(), UNITY_CLIENT_VERSION, MAP_ID);
                    method = SessionConnector.LoginMethod.UNITY;
                } else {
                    System.out.println("[unity] No credentials: set username/password or server+sid"
                            + " in the login properties file.");
                    return;
                }

                runSession(http, provider, method);
            } catch (Throwable e) {
                System.err.println("[unity] Session setup failed: " + e);
            }
        }, "darkbot-unity-session");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Builds the pipeline and connector, starts the session, then loops publishing the
     * session state to {@code BotInstaller.invalid} until the JVM exits.
     */
    private void runSession(SessionHttpClient http, SessionConnector.LoginProvider provider,
                            SessionConnector.LoginMethod method) throws IOException {
        // URL is resolved lazily at refresh time: the portal login (which sets the server on
        // the provider's identity) runs only when the connector starts, after this call.
        MapServerTable maps = new MapServerTable(http, () -> MapServerTable.mapsPhpUrl(serverOf(provider)));

        PacketRegistry registry = PacketRegistry.loadDefault();
        EventBroker eventBroker = new EventBroker();
        StarSystemManager starSystem = new StarSystemManager();
        HeroManager hero = new HeroManager(0, starSystem, eventBroker);
        EntitiesManager entities = new EntitiesManager(eventBroker);
        StatsManager stats = new StatsManager(eventBroker);
        OreManager ores = new OreManager();
        InventoryManager inventory = new InventoryManager(ores);
        RepairManager repair = new RepairManager();
        UnityGameState g = new UnityGameState(registry, eventBroker, starSystem, hero, entities, 0,
                stats, repair, ores, inventory);
        this.game = g;

        // Fase 4 DI swap, applied as soon as the pipeline is live (before the connector
        // starts, so the managers are resolvable once the session goes READY):
        //  - addInstance makes the packet-backed managers win the singleton scan (movement,
        //    ores and inventory have no memory counterpart registered at this point);
        //  - the requireAPI override in DarkBotPluginApiImpl routes the APIs that DO have
        //    a memory counterpart (hero/entities/starSystem/stats/repair/eventBroker);
        //  - the listener decorator is re-pointed at the unity event broker, so module
        //    @Subscribe handlers receive packet-derived events.
        Main.INSTANCE.pluginAPI.registerUnityManagers(eventBroker,
                eventBroker, starSystem, hero, entities, stats, repair, ores, inventory,
                g.getMovement(), g.getAttack());

        // The connector relays every server→client frame into the game-state pipeline;
        // UnityGameState learns the hero id itself (first ShipInitializationCommand).
        SessionConnector c = new SessionConnector(http, maps, provider, statusListener(), g);
        c.setLoginMethod(method);
        c.start();
        this.connector = c;

        // Outbound channel: every manager/entity sends through the live connection.
        g.setPacketSender(packet -> {
            GameConnection conn = c.connection();
            if (conn == null) return false;
            try {
                conn.send(packet);
                return true;
            } catch (IOException e) {
                return false;
            }
        });

        try {
            while (true) {
                botInstaller.invalid.send(!isSessionReady());
                Thread.sleep(VALIDITY_POLL_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Derives the game server for the maps URL from the provider's session identity. */
    private static String serverOf(SessionConnector.LoginProvider provider) {
        // SavedSessionProvider and PortalLoginProvider both keep the server in the identity;
        // fall back to a placeholder only if the session block never surfaced it.
        SessionIdentity identity = provider instanceof SavedSessionProvider || provider instanceof PortalLoginProvider
                ? identityOf(provider) : null;
        return identity != null && identity.getServer() != null ? identity.getServer() : "s1";
    }

    private static SessionIdentity identityOf(SessionConnector.LoginProvider provider) {
        try {
            java.lang.reflect.Field f = provider.getClass().getDeclaredField("identity");
            f.setAccessible(true);
            return (SessionIdentity) f.get(provider);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private SessionConnector.StatusListener statusListener() {
        return new SessionConnector.StatusListener() {
            @Override
            public void onStatus(String message) {
                System.out.println("[unity-session] " + message);
            }

            @Override
            public void onDisconnected(IOException cause) {
                System.out.println("[unity-session] disconnected: " + cause);
            }
        };
    }

    /* ------------------------------ no-op components ------------------------------ */

    public static class NoOpWindow implements GameAPI.Window {
        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public void createWindow() {
        }
    }

    /** Handler validity = the Unity map session being live and the hero snapshot present. */
    public static class NoOpHandler implements GameAPI.Handler {
        /** The owning adapter; assigned right after construction. Null-checked on every use. */
        volatile UnityPacketAdapter adapter;

        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public boolean isValid() {
            UnityPacketAdapter a = adapter;
            return a != null && a.isSessionReady();
        }

        @Override
        public long getMemoryUsage() {
            return 0; // no game process memory to measure
        }

        @Override
        public void reload() {
            System.out.println("[unity] refresh requested — the packet session reconnects on its own");
        }

        @Override
        public void setSize(int width, int height) {
        }

        @Override
        public void setVisible(boolean visible) {
        }

        @Override
        public void setMinimized(boolean minimized) {
        }
    }

    public static class NoOpMemory implements GameAPI.Memory {
        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public int readInt(long address) {
            return 0;
        }

        @Override
        public long readLong(long address) {
            return 0;
        }

        @Override
        public double readDouble(long address) {
            return 0;
        }

        @Override
        public boolean readBoolean(long address) {
            return false;
        }

        @Override
        public byte[] readBytes(long address, int length) {
            return new byte[0];
        }

        @Override
        public void readBytes(long address, byte[] buff, int length) {}

        @Override
        public void replaceInt(long address, int oldValue, int newValue) {}

        @Override
        public void replaceLong(long address, long oldValue, long newValue) {}

        @Override
        public void replaceDouble(long address, double oldValue, double newValue) {}

        @Override
        public void replaceBoolean(long address, boolean oldValue, boolean newValue) {}

        @Override
        public void writeInt(long address, int value) {}

        @Override
        public void writeLong(long address, long value) {}

        @Override
        public void writeDouble(long address, double value) {}

        @Override
        public void writeBoolean(long address, boolean value) {}

        @Override
        public void writeBytes(long address, byte... bytes) {}

        @Override
        public long[] queryInt(int value, int maxSize) {
            return new long[0];
        }

        @Override
        public long[] queryLong(long value, int maxSize) {
            return new long[0];
        }

        @Override
        public long[] queryBytes(byte[] pattern, int maxSize) {
            return new long[0];
        }
    }

    public static class NoOpExtraMemoryReader implements GameAPI.ExtraMemoryReader {
        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public long searchClassClosure(LongPredicate pattern) {
            return 0;
        }

        @Override
        public String readString(long address) {
            return null;
        }

        @Override
        public void resetCache() {}
    }

    public static class NoOpInteraction implements GameAPI.Interaction {
        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public void keyClick(int keyCode) {}

        @Override
        public void sendText(String text) {}

        @Override
        public void mouseMove(int x, int y) {}

        @Override
        public void mouseDown(int x, int y) {}

        @Override
        public void mouseUp(int x, int y) {}

        @Override
        public void mouseClick(int x, int y) {}
    }

    /** The legacy memory action seam: movement routes to the Unity movement manager. */
    public static class UnityDirectInteraction implements GameAPI.DirectInteraction {
        /** The owning adapter; assigned right after construction. Null-checked on every use. */
        volatile UnityPacketAdapter adapter;

        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public void setMaxFps(int maxFps) {
        }

        @Override
        public void lockEntity(int id) {
        }

        @Override
        public void selectEntity(Entity entity) {
        }

        @Override
        public void moveShip(Locatable destination) {
            UnityPacketAdapter a = adapter;
            if (a != null) a.moveShipUnity(destination);
        }

        @Override
        public void collectBox(Box box) {
        }

        @Override
        public void refine(long refineUtilAddress, OreAPI.Ore oreType, int amount) {
        }

        @Override
        public long callMethod(int index, long... arguments) {
            return 0;
        }

        @Override
        public boolean callMethodChecked(boolean checkName, String signature, int index, long... arguments) {
            return false;
        }

        @Override
        public boolean callMethodAsync(int index, long... arguments) {
            return false;
        }

        @Override
        public int checkMethodSignature(long obj, int methodIdx, boolean includeMethodName, String signature) {
            return 0;
        }
    }
}
