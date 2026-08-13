package com.github.manolo8.darkbot.core.api.adapters;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.ConfigEntity;
import com.github.manolo8.darkbot.core.BotInstaller;
import com.github.manolo8.darkbot.core.api.Capability;
import com.github.manolo8.darkbot.core.api.GameAPI;
import com.github.manolo8.darkbot.core.api.GameAPIImpl;
import com.github.manolo8.darkbot.core.entities.Box;
import com.github.manolo8.darkbot.core.manager.StarManager;
import com.github.manolo8.darkbot.core.entities.Entity;
import com.github.manolo8.darkbot.core.objects.slotbars.Item;
import com.github.manolo8.darkbot.extensions.features.handlers.PetGearSelectorHandler;
import com.github.manolo8.darkbot.utils.StartupParams;
import com.github.manolo8.darkbot.utils.login.LoginData;
import com.github.manolo8.darkbot.utils.login.LoginUtils;
import eu.darkbot.api.API;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.extensions.selectors.PetGearSupplier;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.EventBrokerAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.InventoryAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.api.managers.RepairAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.api.managers.StatsAPI;
import eu.darkbot.unity.codec.PacketDef;
import eu.darkbot.unity.codec.PacketFieldReader;
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
import eu.darkbot.unity.net.FrameListener;
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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
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
 * <p><b>Credentials.</b> Either read from the {@code -login} properties file (see {@link
 * StartupParams.AutoLoginProps}): {@code username+password}  portal login
 * ({@link BigPointPortalHandler}); {@code server+sid}  saved portal-cookie exchange;
 * or {@code gameSid+server+userId+instance}  direct restore of a raw gameserver SID
 * captured from the Unity client's LoginRequest (bypassing the portal/WAF). When no
 * {@code -login} file is supplied, credentials are collected from the standard
 * {@code LoginForm} popup (portal credentials or a saved dosid). Without either, the adapter
 * stays invalid and
 * logs the reason.
 */
public class UnityPacketAdapter extends GameAPIImpl<
        UnityPacketAdapter.NoOpWindow,
        UnityPacketAdapter.NoOpHandler,
        UnityPacketAdapter.NoOpMemory,
        UnityPacketAdapter.NoOpExtraMemoryReader,
        UnityPacketAdapter.NoOpInteraction,
        UnityPacketAdapter.UnityDirectInteraction> {

    /**
     * Unity client version hash sent in the VersionRequest handshake and the LoginRequest
     * {@code version} field (the wire value is the {@code packets.json → meta.versionHash}
     * of the client build, not a "x.y.z" string). When the game updates, the map server
     * rejects the old value with "Version mismatch: server version=X" — copy that X here.
     * (2026-08-12 update: previous build hash 8cf182a3… → 0994fb6e…, observed from the
     * live server's VersionCommand after the client update.)
     */
    public static final String UNITY_CLIENT_VERSION = "0994fb6ea86f9b16058e2e9284c16608";
    /** Initial map id (portal jumps re-resolve in a later iteration). */
    public static final int MAP_ID = 1;
    /** How often the session state is re-published to {@code BotInstaller.invalid}. */
    private static final long VALIDITY_POLL_MS = 500;

    private volatile SessionConnector connector;
    private volatile UnityGameState game;
    private volatile PacketFieldReader inboundReader;
    private volatile boolean traceOutbound;
    private volatile boolean diagnosticMove;
    private volatile int diagnosticMoveDistance;

    private BotInstaller botInstaller;

    /** Credentials/session values resolved before the asynchronous worker starts. */
    private static final class SessionInput {
        final String server;
        final String username;
        final String password;
        final String dosid;
        final String gameSid;
        final int userId;
        final String instance;
        final boolean miniClient;
        final int mapId;
        final boolean traceOutbound;
        final boolean diagnosticMove;
        final int diagnosticMoveDistance;

        SessionInput(String server, String username, String password, String dosid,
                     String gameSid, int userId, String instance, boolean miniClient, int mapId,
                     boolean traceOutbound, boolean diagnosticMove, int diagnosticMoveDistance) {
            this.server = server;
            this.username = username;
            this.password = password;
            this.dosid = dosid;
            this.gameSid = gameSid;
            this.userId = userId;
            this.instance = instance;
            this.miniClient = miniClient;
            this.mapId = mapId;
            this.traceOutbound = traceOutbound;
            this.diagnosticMove = diagnosticMove;
            this.diagnosticMoveDistance = diagnosticMoveDistance;
        }
    }

    public UnityPacketAdapter(StartupParams params) {
        super(params,
                new NoOpWindow(),
                new NoOpHandler(),
                new NoOpMemory(),
                new NoOpExtraMemoryReader(),
                new NoOpInteraction(),
                new UnityDirectInteraction(),
                Capability.DIRECT_MOVE_SHIP,
                Capability.DIRECT_USE_ITEM);

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

        // Build the game-state pipeline BEFORE Main's feature/drawable construction (line
        // ~182 of Main.<init>) so GUI drawables injected via getOrCreate resolve to the
        // packet-backed managers instead of the memory ones (the DI scan gives the last
        // registered instance precedence; the Unity managers are registered right here,
        // after the memory managers registered during Main init). If this ran in the
        // session worker instead, the login (~seconds) would race the GUI construction
        // and the drawables would capture dead memory managers — the empty GUI of the
        // first live run.
        buildPipeline();

        startSession();
    }

    /**
     * Builds the game-state pipeline (managers + event broker) and applies the Fase 4 DI
     * swap, synchronously in the constructor. The Unity managers are registered into the
     * plugin API singleton set so any later {@code getOrCreate}/{@code requireAPI} of the
     * packet-backed APIs (hero, entities, star system, stats, repair, movement, attack,
     * ore, inventory, event broker) deterministically resolves to them — "last registered
     * wins" in {@code PluginApiImpl}'s scan. The listener decorator is re-pointed at the
     * unity event broker, so module {@code @Subscribe} handlers receive packet-derived
     * events.
     */
    private void buildPipeline() {
        final PacketRegistry registry;
        try {
            registry = PacketRegistry.loadDefault();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load the unity packets.json dictionary", e);
        }
        this.inboundReader = new PacketFieldReader(registry);
        EventBroker eventBroker = new EventBroker();
        StarSystemManager starSystem = new StarSystemManager();
        // The core already owns the authoritative DarkBot map catalog (id, display name,
        // short name and special-map flags). Reuse it so packet maps keep their wire id while
        // presenting names such as 3-1 instead of the raw id 9.
        for (GameMap map : Main.INSTANCE.mapManager.getMaps()) {
            starSystem.registerMap(map);
            for (com.github.manolo8.darkbot.core.entities.Portal portal
                    : StarManager.getInstance().getStaticPortals(map.getId())) {
                portal.getTargetMap().ifPresent(target -> starSystem.registerPortalRoute(
                        map.getId(), target.getId(), portal.getSearchType(),
                        portal.getSearchX(), portal.getSearchY()));
            }
        }
        HeroManager hero = new HeroManager(0, starSystem, eventBroker);
        EntitiesManager entities = new EntitiesManager(eventBroker);
        // The shared Unity modules use the public BoxInfo/NpcInfo contracts, but the packet
        // module cannot depend on DarkBot's concrete ConfigEntity. Resolve every live entity
        // through the active profile so collect/kill flags are applied before a module can act.
        entities.setConfigResolvers(
                name -> ConfigEntity.INSTANCE.getOrCreateBoxInfo(name),
                name -> ConfigEntity.INSTANCE.getOrCreateNpcInfo(name));
        // Obstacle semantics (Fase 5): AVOID_MINES gates mine avoidance, AVOID_CBS gates
        // enemy battle-station modules; the hero faction decides which CBS are enemies.
        entities.setAvoidFlags(
                () -> Main.INSTANCE.config.MISCELLANEOUS.AVOID_MINES,
                () -> Main.INSTANCE.config.MISCELLANEOUS.AVOID_CBS);
        entities.setHeroFaction(() -> hero.entityInfo().getFaction());
        StatsManager stats = new StatsManager(eventBroker);
        OreManager ores = new OreManager();
        InventoryManager inventory = new InventoryManager(ores);
        RepairManager repair = new RepairManager();
        this.game = new UnityGameState(registry, eventBroker, starSystem, hero, entities, 0,
                stats, repair, ores, inventory);
        // Pet (U-013): the packet PetManager reads the DarkBot PET config (enabled gate +
        // configured gear) and falls back to it after a module gear override expires.
        game.getPet().setConfig(
                () -> Main.INSTANCE.config.PET.ENABLED,
                () -> Main.INSTANCE.config.PET.MODULE_ID);
        // Fuel purchase is part of the PET feature gate: when PET is enabled and the
        // server reports an empty tank, PetManager sends the rate-limited hotkey request.
        game.getPet().setAutoBuyFuel(() -> Main.INSTANCE.config.PET.ENABLED);
        // The native selector already applies plugin priority and PET_LOCATOR/NPC priority
        // rules. Reuse only that public selector contract; unity-game remains independent of
        // DarkBot's feature implementation and falls back to the first wire target if the
        // selector is not ready yet.
        PetGearSelectorHandler petGearSelector =
                Main.INSTANCE.pluginAPI.requireInstance(PetGearSelectorHandler.class);
        game.getPet().setLocatorPicker(picks -> selectLocatorPick(petGearSelector, picks));

        Main.INSTANCE.pluginAPI.registerUnityManagers(eventBroker,
                eventBroker, starSystem, hero, entities, stats, repair, ores, inventory,
                game.getItems(), game.getMovement(), game.getAttack(), game.getPet());
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

    /** Route a click/drag from DarkBot's map interface without entering the Flash Drive loop. */
    public void moveShipFromMapInterface(Locatable destination) {
        UnityGameState g = game;
        if (g != null && isSessionReady()) {
            g.getMovement().moveToFromMapInterface(destination.getX(), destination.getY());
        }
    }

    /**
     * Unity has no Flash map event manager, so GameAPIImpl's legacy mapClick gate would
     * reject every movement before reaching DirectInteraction. Route direct movement
     * straight to the packet manager instead of requiring a minimap click.
     */
    @Override
    public void moveShip(Locatable destination) {
        moveShipUnity(destination);
    }

    /**
     * Uses a menu item directly through the packet-backed HeroItemsManager. Unlike the legacy
     * Flash implementation this does not require the item to have a standard or premium
     * quick-slot: the server menu id is sufficient (category-bar activation, sourceType=0).
     */
    @Override
    public boolean useItem(Item item) {
        UnityGameState g = game;
        if (item == null || item.getId() == null || g == null || !isSessionReady()) return false;
        return g.getItems().useItemId(item.getId()).isSuccessful();
    }

    /** The Unity packet path supports direct item activation once the map session is ready. */
    @Override
    public boolean isUseItemSupported() {
        return game != null && isSessionReady();
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
     * Whether the normal DarkBot module loop may run. The legacy GUI manager checks native
     * memory addresses and therefore always reports false for packet sessions; using it here
     * would leave the bot connected but permanently stopped after READY.
     */
    public boolean canTickModule() {
        return canTickModule(isSessionReady(), game.getRepair().isDestroyed());
    }

    static boolean canTickModule(boolean sessionReady, boolean destroyed) {
        return sessionReady && !destroyed;
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
        if (api == HeroItemsAPI.class) return (T) g.getItems();
        if (api == EntitiesAPI.class) return (T) g.getEntities();
        if (api == StarSystemAPI.class) return (T) g.getStarSystem();
        if (api == EventBrokerAPI.class) return (T) g.getEventBroker();
        if (api == StatsAPI.class) return (T) g.getStats();
        if (api == RepairAPI.class) return (T) g.getRepair();
        if (api == OreAPI.class) return (T) g.getOres();
        if (api == InventoryAPI.class) return (T) g.getInventory();
        if (api == MovementAPI.class) return (T) g.getMovement();
        if (api == AttackAPI.class) return (T) g.getAttack();
        if (api == PetAPI.class) return (T) g.getPet();
        return null;
    }

    /**
     * Starts the Unity session on a daemon worker: resolves credentials (from the
     * {@code -login} properties or the Unity login popup, whichever the user picked;
     * portal auto-detect may take several POSTs), builds the game-state pipeline and
     * connector, then keeps {@code BotInstaller.invalid} in sync with the session state.
     */
    private void startSession() {
        SessionInput input = resolveCredentials();
        if (input == null) {
            System.out.println("[unity] No credentials given: the Unity session will not start."
                    + " Supply a -login properties file or use the Unity login dialog.");
            return;
        }

        this.traceOutbound = input.traceOutbound
                || Boolean.getBoolean("darkbot.unity.traceOutbound")
                || "1".equals(System.getenv("DARKBOT_UNITY_TRACE_OUTBOUND"));
        this.diagnosticMove = input.diagnosticMove;
        this.diagnosticMoveDistance = Math.max(1, input.diagnosticMoveDistance);

        Thread worker = new Thread(() -> {
            try {
                // Mutable copies: the lambda body reassigns server during auto-detect.
                String srv = input.server;
                String usr = input.username;
                String pwd = input.password;
                String dsd = input.dosid;
                String rawGameSid = input.gameSid;

                SessionHttpClient http = new SessionHttpClient();
                http.setUnityMode(true, UNITY_CLIENT_VERSION);
                if (rawGameSid != null && !rawGameSid.isEmpty()) {
                    if (srv == null || srv.isEmpty()) {
                        throw new IOException("gameSid login requires server=<universe>");
                    }
                    if (input.userId <= 0 || input.instance == null || input.instance.isEmpty()) {
                        throw new IOException("gameSid login requires userId=<id> and instance=<pid>");
                    }
                    int mapId = input.mapId > 0 ? input.mapId : MAP_ID;
                    SessionIdentity identity = new SessionIdentity();
                    identity.setServer(srv);
                    identity.setSid(rawGameSid);
                    identity.setPlatform(SessionConnector.PLATFORM_UNITY);
                    if (input.instance != null && !input.instance.isEmpty()) {
                        identity.setInstance(input.instance);
                    }
                    SavedAccount account = new SavedAccount();
                    account.server = srv;
                    account.userId = input.userId;
                    account.lastMethod = "GAME_SID";
                    SessionConnector.LoginProvider provider =
                            new SavedSessionProvider(identity, account, UNITY_CLIENT_VERSION,
                                    mapId, input.miniClient);
                    System.out.println("[unity] Using captured gameSid directly (server=" + srv
                            + ", userId=" + input.userId + ", instance=" + input.instance
                            + ", map=" + mapId + ")");
                    runSession(http, provider, SessionConnector.LoginMethod.SID);
                    return;
                }

                if ((srv == null || srv.isEmpty()) && usr != null && !usr.isEmpty()) {
                    System.out.println("[unity] Detecting account server (one POST per known portal)…");
                    srv = BigPointPortalHandler.detectServer(http, usr, pwd, null);
                }
                if (srv == null || srv.isEmpty()) {
                    System.out.println("[unity] No server known: set server=<universe> (login file or Unity login dialog).");
                    return;
                }
                String lang = BigPointPortalHandler.langFor(srv);
                int requestedMap = input.mapId > 0 ? input.mapId : MAP_ID;
                System.out.println("[unity] Connecting to " + srv + " (lang=" + lang + ", version="
                        + UNITY_CLIENT_VERSION + ", map " + requestedMap + ")");

                SessionConnector.LoginProvider provider;
                SessionConnector.LoginMethod method;
                if (dsd != null && !dsd.isEmpty()) {
                    String sid = BigPointPortalHandler.sidFromDosid(http, srv, lang, dsd);
                    SessionIdentity identity = new SessionIdentity();
                    identity.setServer(srv);
                    identity.setPlatform(SessionConnector.PLATFORM_UNITY);
                    identity.setSid(sid);
                    SavedAccount account = new SavedAccount();
                    account.server = srv;
                    account.dosid = dsd;
                    account.userId = input.userId;
                    account.lastMethod = "SID";
                    int mapId = input.mapId > 0 ? input.mapId : MAP_ID;
                    provider = new SavedSessionProvider(identity, account, UNITY_CLIENT_VERSION, mapId,
                            input.miniClient);
                    method = SessionConnector.LoginMethod.SID;
                } else if (usr != null && !usr.isEmpty()) {
                    BigPointPortalHandler portal = new BigPointPortalHandler(http, srv, lang, usr, pwd);
                    provider = new PortalLoginProvider(portal, new SessionIdentity(), UNITY_CLIENT_VERSION,
                            input.mapId > 0 ? input.mapId : MAP_ID);
                    method = SessionConnector.LoginMethod.UNITY;
                } else {
                    System.out.println("[unity] No credentials: set username/password or server+sid"
                            + " (in the login properties file or the Unity login dialog).");
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
     * Resolves the Unity session credentials: from the {@code -login} properties file when
     * provided (headless/scripted runs), otherwise from the standard Flash login form shown
     * on the EDT. The selected adapter then decides whether those credentials feed the Flash
     * client or the Unity packet session.
     *
     * @return the resolved portal/direct-session input, or {@code null} if the user dismissed
     *         the dialog without providing credentials
     */
    private SessionInput resolveCredentials() {
        StartupParams.AutoLoginProps props = params.getAutoLoginProps();
        if (props != null) {
            String gameSid = props.getGameSID();
            String userId = props.getUserId();
            String instance = props.getInstance();
            String miniClient = props.getMiniClient();
            String captureFile = props.getCapturedLoginFile();
            if (captureFile != null && !captureFile.trim().isEmpty()) {
                try {
                    Properties captured = new Properties();
                    try (FileInputStream in = new FileInputStream(captureFile.trim())) {
                        captured.load(in);
                    }
                    gameSid = first(captured.getProperty("gameSid"),
                            captured.getProperty("sessionID"), gameSid);
                    userId = first(captured.getProperty("userId"),
                            captured.getProperty("userID"), userId);
                    instance = first(captured.getProperty("instance"),
                            captured.getProperty("instanceId"), instance);
                    miniClient = first(captured.getProperty("miniClient"),
                            captured.getProperty("isMiniClient"), miniClient);
                    System.out.println("[unity] Loaded captured login from " + captureFile.trim());
                } catch (IOException e) {
                    System.err.println("[unity] Could not read captured login file "
                            + captureFile.trim() + ": " + e.getMessage());
                }
            }
            return new SessionInput(
                    props.getServer(), props.getUsername(), props.getPassword(), props.getSID(),
                    gameSid, parseInt(userId, 0), instance,
                    parseBooleanInt(miniClient, false), parseInt(props.getMapId(), MAP_ID),
                    parseBooleanInt(props.getTraceOutbound(), false),
                    parseBooleanInt(props.getDiagnosticMove(), false),
                    parseInt(props.getDiagnosticMoveDistance(), 200));
        }

        LoginData login = LoginUtils.performUserLogin(params, true);
        if (login == null) {
            System.out.println("[unity] Login dialog was dismissed without logging in");
            return null;
        }

        return new SessionInput(serverFromUrl(login.getUrl()), login.getUsername(),
                login.getPassword(), login.getSid(), null, 0, null, false,
                MAP_ID, false, false, 200);
    }

    /** Converts the Flash login form's saved universe URL to the maps/session server name. */
    private static String serverFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String server = url.trim().toLowerCase();
        int scheme = server.indexOf("://");
        if (scheme >= 0) server = server.substring(scheme + 3);
        int slash = server.indexOf('/');
        if (slash >= 0) server = server.substring(0, slash);
        if (server.endsWith(".darkorbit.com"))
            server = server.substring(0, server.length() - ".darkorbit.com".length());
        return server.isEmpty() ? null : server;
    }

    private static String first(String value, String fallback, String defaultValue) {
        return value != null && !value.trim().isEmpty() ? value :
                (fallback != null && !fallback.trim().isEmpty() ? fallback : defaultValue);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBooleanInt(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return "1".equals(value.trim()) || Boolean.parseBoolean(value.trim());
    }

    /**
     * Applies the active DarkBot selector to packet locator picks. Kept as a small seam so
     * selector priority and the reload fallback can be tested without starting a live session.
     */
    static PetAPI.LocatorPick selectLocatorPick(PetGearSelectorHandler handler,
                                                 Collection<? extends PetAPI.LocatorPick> picks) {
        if (handler == null) return null;
        try {
            PetGearSupplier supplier = handler.getBestSupplier();
            return supplier == null ? null : supplier.getNpcLocatorPick(picks);
        } catch (RuntimeException ignored) {
            // Feature reloads can briefly leave the handler without a supplier. Keeping
            // the first server target is safer than suppressing PetGearActivationRequest.
            return null;
        }
    }

    /**
     * Starts the map-server connection on the session worker and loops publishing the
     * session state to {@code BotInstaller.invalid} until the JVM exits. The game-state
     * pipeline is already built ({@link #buildPipeline()} runs in the constructor); this
     * only resolves the map list, connects and feeds frames into it.
     */
    private void runSession(SessionHttpClient http, SessionConnector.LoginProvider provider,
                            SessionConnector.LoginMethod method) throws IOException {
        // URL is resolved lazily at refresh time: the portal login (which sets the server on
        // the provider's identity) runs only when the connector starts, after this call.
        MapServerTable maps = new MapServerTable(http, () -> MapServerTable.mapsPhpUrl(serverOf(provider)));

        UnityGameState g = game;

        // The connector relays every server→client frame into the game-state pipeline;
        // UnityGameState learns the hero id itself (first ShipInitializationCommand).
        FrameListener listener = g;
        if (traceOutbound) {
            listener = (clientToServer, payload) -> {
                if (!clientToServer) traceInboundFrame(payload);
                g.onFrame(clientToServer, payload);
            };
        }
        SessionConnector c = new SessionConnector(http, maps, provider, statusListener(), listener);
        c.setLoginMethod(method);
        c.start();
        this.connector = c;

        // Outbound channel: every manager/entity sends through the live connection.
        g.setPacketSender(packet -> {
            GameConnection conn = c.connection();
            if (conn == null) {
                if (traceOutbound) System.out.println("[unity-c2s] " + packet.name() + " skipped: no connection");
                return false;
            }
            try {
                conn.send(packet);
                g.onOutbound(packet);
                if (traceOutbound) {
                    String mode = ("MoveRequest".equals(packet.name()) || "JumpRequest".equals(packet.name()))
                            ? "[" + g.getMovement().getLastActionMode().name().toLowerCase() + "] " : "";
                    String details = "MoveRequest".equals(packet.name()) ? " " + packet.values() : "";
                    System.out.println("[unity-c2s] " + mode + packet.name() + details + " sent");
                }
                return true;
            } catch (IOException | RuntimeException e) {
                if (traceOutbound) System.out.println("[unity-c2s] " + packet.name() + " failed: " + e.getMessage());
                return false;
            }
        });
        // KillScreenRepairRequest contains the same LoginRequest module as the current
        // connection. Binding it here is essential: sending a null nested module is rejected
        // by PacketWriter and the Unity server ignores a repair without the session identity.
        g.getRepair().setLoginRequestSupplier(() -> {
            GameConnection connection = c.connection();
            return connection == null ? null : connection.toLoginRequest();
        });

        if (diagnosticMove) startDiagnosticMove(c, g);

        long nextMetricsLog = System.currentTimeMillis() + 10_000;
        long nextUnityReviveAt = 0;
        try {
            while (true) {
                tickUnityRepair(g, c, nextUnityReviveAt);
                if (g.getRepair().isDestroyed()) nextUnityReviveAt = System.currentTimeMillis() + 10_000;
                else nextUnityReviveAt = 0;
                // The memory PetManager's GUI tick never runs in Unity mode (Main drives
                // tickLogic directly), so forward the module's intent (setEnabled/setGear
                // calls on guiManager.pet) to the packet pet manager and let it act.
                com.github.manolo8.darkbot.core.manager.PetManager memoryPet =
                        Main.INSTANCE.guiManager.pet;
                g.getPet().setEnabled(memoryPet.isEnabled());
                g.getPet().setOverride(memoryPet.getGearOverride());
                g.getPet().tick();
                botInstaller.invalid.send(!isSessionReady());
                if (traceOutbound && System.currentTimeMillis() >= nextMetricsLog) {
                    System.out.println("[unity-metrics] " + g.getActionMetrics().status());
                    nextMetricsLog = System.currentTimeMillis() + 10_000;
                }
                Thread.sleep(VALIDITY_POLL_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Runs the packet equivalent of GuiManager's death/revive branch. Unity deliberately skips
     * the native GUI tick, so without this worker-side path a detected kill screen would remain
     * destroyed forever and no KillScreenRepairRequest would be sent.
     */
    private void tickUnityRepair(UnityGameState g, SessionConnector c, long nextAttemptAt) {
        if (!g.getRepair().isDestroyed() || System.currentTimeMillis() < nextAttemptAt) return;
        GameConnection connection = c.connection();
        if (connection == null || !connection.state().isMapActive()) return;

        long waitMs = Math.max(0, Main.INSTANCE.config.GENERAL.SAFETY.WAIT_BEFORE_REVIVE * 1000L);
        java.time.Instant death = g.getRepair().getLastDeathTime();
        if (death != null) {
            long elapsed = java.time.Duration.between(death, java.time.Instant.now()).toMillis();
            if (elapsed < waitMs) return;
        }

        com.github.manolo8.darkbot.config.types.suppliers.ReviveLocation configured =
                Main.INSTANCE.config.GENERAL.SAFETY.REVIVE;
        eu.darkbot.api.game.enums.ReviveLocation location =
                eu.darkbot.api.game.enums.ReviveLocation.valueOf(configured.name());
        boolean sent = g.getRepair().revive(location);
        if (traceOutbound || sent) {
            System.out.println("[unity] " + (sent ? "KillScreenRepairRequest sent" :
                    "KillScreenRepairRequest not sent") + " location=" + location);
        }
    }

    /** Logs movement and action confirmations without dumping session or unrelated packet data. */
    private void traceInboundFrame(byte[] payload) {
        try {
            PacketDef def = inboundReader.read(payload);
            String name = def.name();
            if ("MoveCommand".equals(name)) {
                System.out.println("[unity-s2c] MoveCommand userId=" + inboundReader.intValue("userId")
                        + " x=" + inboundReader.intValue("x") + " y=" + inboundReader.intValue("y")
                        + " timeToTarget=" + inboundReader.intValue("timeToTarget"));
            } else if ("HeroMoveCommand".equals(name)) {
                System.out.println("[unity-s2c] HeroMoveCommand x=" + inboundReader.intValue("x")
                        + " y=" + inboundReader.intValue("y"));
            } else if ("BeaconCommand".equals(name)) {
                System.out.println("[unity-s2c] BeaconCommand x=" + inboundReader.intValue("positionX")
                        + " y=" + inboundReader.intValue("positionY") + " aheadX="
                        + inboundReader.intValue("positionAheadX") + " aheadY="
                        + inboundReader.intValue("positionAheadY"));
            } else if ("HitpointInfoCommand".equals(name)) {
                System.out.println("[unity-s2c] HitpointInfoCommand hp="
                        + inboundReader.values().get("hitpoints") + " hpMax="
                        + inboundReader.values().get("hitpointsMax") + " nanoHull="
                        + inboundReader.intValue("nanoHull"));
            } else if ("ShipSelectionCommand".equals(name)) {
                System.out.println("[unity-s2c] ShipSelectionCommand userId="
                        + inboundReader.intValue("userId") + " hp="
                        + inboundReader.values().get("hitpoints") + " hpMax="
                        + inboundReader.values().get("hitpointsMax") + " shield="
                        + inboundReader.intValue("shield"));
            } else if ("ShipInitializationCommand".equals(name)) {
                System.out.println("[unity-s2c] ShipInitializationCommand userId="
                        + inboundReader.intValue("userId") + " cargoFree="
                        + inboundReader.intValue("cargoSpace") + " cargoMax="
                        + inboundReader.intValue("cargoSpaceMax"));
            } else if ("LegacyModule".equals(name)) {
                // Unity currently delivers rewards through this legacy text envelope. Keep
                // only stat messages in the trace; the map also emits thousands of unrelated
                // legacy UI/settings updates during a session.
                String message = inboundReader.stringValue("message");
                if (message != null && message.startsWith("0|LM|ST|")) {
                    System.out.println("[unity-s2c] LegacyModule reward=" + message);
                }
            } else if ("AttributeSpaceUpdateCommand".equals(name)) {
                System.out.println("[unity-s2c] AttributeSpaceUpdateCommand spaceType="
                        + inboundReader.intValue("spaceType") + " spaceLeft="
                        + inboundReader.intValue("spaceLeft"));
            } else if ("UpdateCargoSpaceCommand".equals(name)) {
                System.out.println("[unity-s2c] UpdateCargoSpaceCommand cargoMax="
                        + inboundReader.intValue("cargoSpaceMax"));
            } else if ("AttributeOreCountUpdateCommand".equals(name)) {
                StringBuilder ores = new StringBuilder();
                long oreTotal = 0;
                for (Map<String, Object> elem : inboundReader.listElements("oreCountList")) {
                    Object type = elem.get("oreCountList.elem.oreType.typeValue");
                    Object value = elem.get("oreCountList.elem.count");
                    if (value instanceof Number) oreTotal += Math.max(0, ((Number) value).longValue());
                    if (ores.length() > 0) ores.append(',');
                    ores.append(type).append('=').append(value);
                }
                System.out.println("[unity-s2c] AttributeOreCountUpdateCommand oreTotal="
                        + oreTotal + " values=" + ores);
            } else if ("LMCollectResourcesCommand".equals(name)) {
                long collected = 0;
                for (Map<String, Object> elem : inboundReader.listElements("contentList")) {
                    Object value = elem.get("contentList.elem.count");
                    if (value instanceof Number) collected += Math.max(0, ((Number) value).longValue());
                }
                System.out.println("[unity-s2c] LMCollectResourcesCommand collected=" + collected);
            } else if (isTraceableInboundAction(name)) {
                switch (name) {
                    case "RemoveCollectableCommand":
                        System.out.println("[unity-s2c] RemoveCollectableCommand hash="
                                + inboundReader.stringValue("hash") + " collected="
                                + inboundReader.intValue("collected"));
                        break;
                    case "CollectionBeamStartCommand":
                        System.out.println("[unity-s2c] CollectionBeamStartCommand mapObjectId="
                                + inboundReader.intValue("mapObjectId") + " duration="
                                + inboundReader.intValue("duration"));
                        break;
                    case "CollectionBeamStopCommand":
                        System.out.println("[unity-s2c] CollectionBeamStopCommand mapObjectId="
                                + inboundReader.intValue("mapObjectId"));
                        break;
                    case "AttackLaserRunCommand":
                        System.out.println("[unity-s2c] AttackLaserRunCommand attackerId="
                                + inboundReader.intValue("attackerId") + " targetId="
                                + inboundReader.intValue("targetId"));
                        break;
                    case "AttackAbortLaserCommand":
                        System.out.println("[unity-s2c] AttackAbortLaserCommand uid="
                                + inboundReader.intValue("uid"));
                        break;
                    case "AttackHitCommand":
                    case "AttackHitNoLockCommand":
                        System.out.println("[unity-s2c] " + name + " attackerId="
                                + inboundReader.intValue("attackerId") + " victimId="
                                + inboundReader.intValue("victimId") + " damage="
                                + inboundReader.intValue("damage") + " victimHitpoints="
                                + inboundReader.values().get("victimHitpoints"));
                        break;
                    case "ShipDestroyedCommand":
                        System.out.println("[unity-s2c] ShipDestroyedCommand destroyedUserId="
                                + inboundReader.intValue("destroyedUserId"));
                        break;
                    case "KillScreenPostCommand":
                        System.out.println("[unity-s2c] KillScreenPostCommand killer="
                                + inboundReader.stringValue("killerName"));
                        break;
                    default:
                        break;
                }
            } else {
                // Diagnostic mode logs packet names only; never dump arbitrary fields or SID data.
                System.out.println("[unity-s2c] packet=" + name);
            }
        } catch (IllegalArgumentException ignored) {
            // The game-state pipeline owns malformed-frame handling; tracing must not affect it.
        }
    }

    /** Action confirmations whose fields are useful for a packet-only live diagnosis. */
    static boolean isTraceableInboundAction(String name) {
        switch (name) {
            case "RemoveCollectableCommand":
            case "CollectionBeamStartCommand":
            case "CollectionBeamStopCommand":
            case "AttackLaserRunCommand":
            case "AttackAbortLaserCommand":
            case "AttackHitCommand":
            case "AttackHitNoLockCommand":
            case "ShipDestroyedCommand":
            case "KillScreenPostCommand":
                return true;
            default:
                return false;
        }
    }

    /**
     * Sends one controlled movement after the live hero snapshot arrives. This is deliberately
     * opt-in and exists only to separate packet transport from module target-selection logic.
     */
    private void startDiagnosticMove(SessionConnector c, UnityGameState g) {
        Thread move = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline && !isSessionReady()) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!isSessionReady() || c.connection() == null) {
                System.out.println("[unity-diagnostic] move skipped: session did not become ready");
                return;
            }
            eu.darkbot.api.game.other.Location from = g.getMovement().getCurrentLocation();
            double distance = Math.max(1, diagnosticMoveDistance);
            double targetX = from.getX() + distance;
            double targetY = from.getY();
            if (!g.getMovement().canMove(targetX, targetY)) targetX = from.getX() - distance;
            if (!g.getMovement().canMove(targetX, targetY)) {
                System.out.println("[unity-diagnostic] move skipped: no valid target from " + from);
                return;
            }
            System.out.println("[unity-diagnostic] move " + (int) from.getX() + "," + (int) from.getY()
                    + " -> " + (int) targetX + "," + (int) targetY);
            g.getMovement().moveTo(targetX, targetY);
        }, "darkbot-unity-diagnostic-move");
        move.setDaemon(true);
        move.start();
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
