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
import eu.darkbot.api.managers.AssemblyAPI;
import eu.darkbot.api.managers.ChatAPI;
import eu.darkbot.api.managers.GameLogAPI;
import eu.darkbot.api.managers.AttackAPI;
import eu.darkbot.api.managers.BoosterAPI;
import eu.darkbot.api.managers.DispatchAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.EventBrokerAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.GroupAPI;
import eu.darkbot.api.managers.HeroItemsAPI;
import eu.darkbot.api.managers.HangarAPI;
import eu.darkbot.api.managers.InventoryAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.PetAPI;
import eu.darkbot.api.managers.QuestAPI;
import eu.darkbot.api.managers.RepairAPI;
import eu.darkbot.api.managers.SessionAPI;
import eu.darkbot.api.managers.ShipWarpAPI;
import eu.darkbot.api.managers.SkylabAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.api.managers.StatsAPI;
import eu.darkbot.unity.codec.PacketDef;
import eu.darkbot.unity.chat.ICMessage;
import eu.darkbot.unity.chat.InfinicastConnection;
import eu.darkbot.unity.chat.InfinicastFrameCodec;
import eu.darkbot.unity.codec.PacketFieldReader;
import eu.darkbot.unity.codec.PacketRegistry;
import eu.darkbot.unity.game.EntitiesManager;
import eu.darkbot.unity.game.EventBroker;
import eu.darkbot.unity.game.UnityHeroManager;
import eu.darkbot.unity.game.UnityGroupManager;
import eu.darkbot.unity.game.InventoryManager;
import eu.darkbot.unity.game.OreManager;
import eu.darkbot.unity.game.UnityRepairManager;
import eu.darkbot.unity.game.StarSystemManager;
import eu.darkbot.unity.game.UnityStatsManager;
import eu.darkbot.unity.game.UnityGameState;
import eu.darkbot.unity.game.UnityAuctionManager;
import com.github.manolo8.darkbot.backpage.AuctionModule;
import com.github.manolo8.darkbot.backpage.UnityAuctionBackend;
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
import eu.darkbot.unity.session.SessionBlock;
import eu.darkbot.unity.session.VersionStore;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.function.LongPredicate;

/**
 * Packet-based API adapter (Camino A, Fase 4): runs the bot against the
 * official Unity
 * client through the {@code unity-transport}/{@code unity-game} protocol stack
 * instead of
 * reading the Flash player's memory.
 *
 * <p>
 * The adapter owns the whole Unity session: it builds a
 * {@link SessionConnector} (portal
 * login or saved SID → map server → {@link GameConnection}) and feeds every
 * server→client
 * frame into a {@link UnityGameState} pipeline. Outbound actions (movement,
 * lock, attack,
 * formation, box collection) flow back through a {@link PacketSender} wired to
 * the live
 * connection — the modules drive them through the swapped
 * {@code eu.darkbot.api.*} managers
 * (see the Fase 4 DI swap in {@code DarkBotPluginApiImpl}).
 *
 * <p>
 * <b>Validity.</b> The adapter is intentionally <i>not</i> background-only: it
 * publishes
 * the bot validity through {@link BotInstaller#invalid} so {@code Main}'s
 * normal
 * {@code validTick()} path runs and modules tick. The legacy memory managers
 * no-op on the
 * NoOp memory (their install never succeeds), and the seven address {@link
 * com.github.manolo8.darkbot.core.utils.Lazy}s are pinned to {@code 0} so
 * {@code
 * BotInstaller#isInvalid} does not dereference null. A daemon poller flips
 * {@code invalid}
 * to {@code false} once the session is {@link GameState#isMapActive() READY}
 * and the hero
 * snapshot arrived, and back to {@code true} when the session drops.
 *
 * <p>
 * <b>Credentials.</b> Either read from the {@code -login} properties file (see
 * {@link
 * StartupParams.AutoLoginProps}): {@code username+password}  portal login
 * ({@link BigPointPortalHandler}); {@code server+sid}  saved portal-cookie
 * exchange;
 * or {@code gameSid+server+userId+instance}  direct restore of a raw
 * gameserver SID
 * captured from the Unity client's LoginRequest (bypassing the portal/WAF).
 * When no
 * {@code -login} file is supplied, credentials are collected from the standard
 * {@code LoginForm} popup (portal credentials or a saved dosid). Without
 * either, the adapter
 * stays invalid and
 * logs the reason.
 */
public class UnityPacketAdapter extends
        GameAPIImpl<UnityPacketAdapter.NoOpWindow, UnityPacketAdapter.NoOpHandler, UnityPacketAdapter.NoOpMemory, UnityPacketAdapter.NoOpExtraMemoryReader, UnityPacketAdapter.NoOpInteraction, UnityPacketAdapter.UnityDirectInteraction> implements SessionAPI {

    /**
     * Fallback Unity client version hash, sent in the VersionRequest handshake and
     * the LoginRequest {@code version} field (the wire value is the
     * {@code packets.json → meta.versionHash} of the client build, not an
     * "x.y.z" string). Only used when neither {@code darkbot.unity.version} nor a
     * persisted negotiated value ({@link #VERSION_FILE}) is available.
     * <p>
     * When the game updates, the map server rejects this value with
     * "Version mismatch: server version=X"; the connector negotiates X automatically
     * and {@link VersionStore} persists it for the next launches.
     * (2026-08-19 update: previous build hash 0994fb6e… → e160dc30…, observed from
     * the live server's VersionCommand after the client update.)
     */
    public static final String UNITY_CLIENT_VERSION = "e160dc30295f509e2405309a9e4d50fb";
    /** Properties file where the negotiated handshake hash survives restarts. */
    private static final Path VERSION_FILE = Paths.get("unity-version.properties");
    /** Initial map id (portal jumps re-resolve in a later iteration). */
    public static final int MAP_ID = 1;
    /**
     * How often the session state is re-published to {@code BotInstaller.invalid}.
     */
    private static final long VALIDITY_POLL_MS = 500;
    /** Interval between unity revive attempts while the kill screen is up. */
    private static final long REVIVE_RETRY_MS = 10_000;

    private volatile SessionConnector connector;
    private volatile UnityGameState game;
    private volatile PacketFieldReader inboundReader;
    private volatile boolean traceOutbound;
    private volatile boolean diagnosticMove;
    private volatile int diagnosticMoveDistance;
    private volatile boolean diagnosticPortal;
    /**
     * Destination map name/id for portal travel ({@code targetMap} login property).
     */
    private volatile String targetMap;
    /** Opt-in raw server→client frame dump ({@code captureS2C} login property). */
    private volatile OutputStream s2cCapture;
    private volatile InfinicastConnection chatConnection;
    /**
     * Opt-in protocol-drift report destination ({@code driftReport} login property or
     * {@code darkbot.unity.driftReport} system property). Null when not configured.
     */
    private volatile Path driftReportFile;
    /** How often the drift report is flushed while the session runs. */
    private static final long DRIFT_FLUSH_MS = 30_000;
    /** Ensures the periodic drift-report log line is printed only once per session. */
    private volatile boolean driftWriteLogged;

    /** Persists the server-negotiated handshake hash across restarts. */
    private final VersionStore versionStore = new VersionStore(VERSION_FILE);
    /**
     * Effective handshake version in use: {@code darkbot.unity.version} system
     * property, then the persisted negotiated hash, then {@link #UNITY_CLIENT_VERSION}.
     * Updated again whenever the connector negotiates a new value mid-session.
     */
    private volatile String clientVersion = resolveClientVersion();

    private BotInstaller botInstaller;

    /**
     * Credentials/session values resolved before the asynchronous worker starts.
     */
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
        final boolean diagnosticPortal;
        final String targetMap;
        final String captureS2C;
        final String driftReport;

        SessionInput(String server, String username, String password, String dosid,
                String gameSid, int userId, String instance, boolean miniClient, int mapId,
                boolean traceOutbound, boolean diagnosticMove, int diagnosticMoveDistance,
                boolean diagnosticPortal, String targetMap, String captureS2C, String driftReport) {
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
            this.diagnosticPortal = diagnosticPortal;
            this.targetMap = targetMap == null || targetMap.trim().isEmpty()
                    ? null
                    : targetMap.trim();
            this.captureS2C = captureS2C == null || captureS2C.trim().isEmpty()
                    ? null
                    : captureS2C.trim();
            String drift = driftReport == null || driftReport.trim().isEmpty()
                    ? System.getProperty("darkbot.unity.driftReport", "")
                    : driftReport;
            this.driftReport = drift.trim().isEmpty() ? null : drift.trim();
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
                Capability.DIRECT_REFINE,
                Capability.DIRECT_USE_ITEM);

        // Wire the components that need the adapter instance. They are static (so they
        // can be
        // constructed in the super() call) and lazily read this reference; their
        // callbacks only
        // fire from the tick loop, long after this constructor completes.
        ((NoOpHandler) handler).adapter = this;
        ((UnityDirectInteraction) direct).adapter = this;

        // Pin the memory-install addresses so BotInstaller.isInvalid()'s non-null
        // branch
        // evaluates with zero addresses (readLong(1344) == mainAddress(0)) instead of
        // NPE-ing
        // on the null Lazy values.
        botInstaller = Main.INSTANCE.pluginAPI.requireInstance(BotInstaller.class);
        botInstaller.mainApplicationAddress.send(0L);
        botInstaller.mainAddress.send(0L);
        botInstaller.screenManagerAddress.send(0L);
        botInstaller.guiManagerAddress.send(0L);
        botInstaller.heroInfoAddress.send(0L);
        botInstaller.settingsAddress.send(0L);
        botInstaller.connectionManagerAddress.send(0L);

        // Build the game-state pipeline BEFORE Main's feature/drawable construction
        // (line
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
     * Builds the game-state pipeline (managers + event broker) and applies the Fase
     * 4 DI
     * swap, synchronously in the constructor. The Unity managers are registered
     * into the
     * plugin API singleton set so any later {@code getOrCreate}/{@code requireAPI}
     * of the
     * packet-backed APIs (hero, entities, star system, stats, repair, movement,
     * attack,
     * ore, inventory, event broker) deterministically resolves to them — "last
     * registered
     * wins" in {@code PluginApiImpl}'s scan. The listener decorator is re-pointed
     * at the
     * unity event broker, so module {@code @Subscribe} handlers receive
     * packet-derived
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
        // The core already owns the authoritative DarkBot map catalog (id, display
        // name,
        // short name and special-map flags). Reuse it so packet maps keep their wire id
        // while
        // presenting names such as 3-1 instead of the raw id 9.
        for (GameMap map : Main.INSTANCE.mapManager.getMaps()) {
            starSystem.registerMap(map);
            for (com.github.manolo8.darkbot.core.entities.Portal portal : StarManager.getInstance()
                    .getStaticPortals(map.getId())) {
                portal.getTargetMap().ifPresent(target -> starSystem.registerPortalRoute(
                        map.getId(), target.getId(), portal.getSearchType(),
                        portal.getSearchX(), portal.getSearchY()));
            }
        }
        UnityHeroManager hero = new UnityHeroManager(0, starSystem, eventBroker);
        EntitiesManager entities = new EntitiesManager(eventBroker);
        // The shared Unity modules use the public BoxInfo/NpcInfo contracts, but the
        // packet
        // module cannot depend on DarkBot's concrete ConfigEntity. Resolve every live
        // entity
        // through the active profile so collect/kill flags are applied before a module
        // can act.
        entities.setConfigResolvers(
                name -> ConfigEntity.INSTANCE.getOrCreateBoxInfo(name),
                name -> ConfigEntity.INSTANCE.getOrCreateNpcInfo(name));
        // Obstacle semantics (Fase 5): AVOID_MINES gates mine avoidance, AVOID_CBS
        // gates
        // enemy battle-station modules; the hero faction decides which CBS are enemies.
        entities.setAvoidFlags(
                () -> Main.INSTANCE.config.MISCELLANEOUS.AVOID_MINES,
                () -> Main.INSTANCE.config.MISCELLANEOUS.AVOID_CBS);
        entities.setHeroFaction(() -> hero.entityInfo().getFaction());
        UnityStatsManager stats = new UnityStatsManager(eventBroker);
        OreManager ores = new OreManager();
        InventoryManager inventory = new InventoryManager(ores);
        UnityRepairManager repair = new UnityRepairManager();
        this.game = new UnityGameState(registry, eventBroker, starSystem, hero, entities, 0,
                stats, repair, ores, inventory);
        game.getMovement().setAvoidRadiation(
                () -> Main.INSTANCE.config.MISCELLANEOUS.AVOID_RADIATION);
        game.getMovement().setPreferredZonePredicate(location -> {
            GameMap currentMap = game.getStarSystem().getCurrentMap();
            com.github.manolo8.darkbot.config.ZoneInfo preferred = Main.INSTANCE.config.PREFERRED
                    .get(currentMap.getId());
            // An empty Flash preferred-zone grid means "no restriction", not "nowhere".
            if (preferred == null || preferred.getZones().isEmpty())
                return true;
            eu.darkbot.api.game.other.Area.Rectangle bounds = game.getStarSystem().getCurrentMapBounds();
            double width = bounds.getX2() - bounds.getX();
            double height = bounds.getY2() - bounds.getY();
            return width <= 0 || height <= 0 || preferred.contains(
                    (location.getX() - bounds.getX()) / width,
                    (location.getY() - bounds.getY()) / height);
        });
        // Pet (U-013): the packet UnityPetManager reads the DarkBot PET config (enabled gate
        // +
        // configured gear) and falls back to it after a module gear override expires.
        game.getPet().setConfig(
                () -> Main.INSTANCE.config.PET.ENABLED,
                () -> Main.INSTANCE.config.PET.MODULE_ID);
        // Fuel purchase is part of the PET feature gate: when PET is enabled and the
        // server reports an empty tank, UnityPetManager sends the rate-limited hotkey
        // request.
        game.getPet().setAutoBuyFuel(() -> Main.INSTANCE.config.PET.ENABLED);
        // The native selector already applies plugin priority and PET_LOCATOR/NPC
        // priority
        // rules. Reuse only that public selector contract; unity-game remains
        // independent of
        // DarkBot's feature implementation and falls back to the first wire target if
        // the
        // selector is not ready yet.
        PetGearSelectorHandler petGearSelector = Main.INSTANCE.pluginAPI.requireInstance(PetGearSelectorHandler.class);
        game.getPet().setLocatorPicker(picks -> selectLocatorPick(petGearSelector, picks));
        configureGroupAutomation(game.getGroup());

        Main.INSTANCE.pluginAPI.registerUnityManagers(eventBroker,
                eventBroker, starSystem, hero, entities, stats, repair, ores, inventory,
                game.getItems(), game.getMovement(), game.getAttack(), game.getPet(), game.getGroup(),
                game.getHangar(), game.getQuests(),
                game.getBooster(), game.getDispatch(), game.getShipWarp(), game.getAssembly(),
                game.getUserMessages(), game.getLogs());
    }

    @Override
    public String getVersion() {
        return "unity-packets " + clientVersion;
    }

    /**
     * Resolves the handshake version to start with: the {@code darkbot.unity.version}
     * system property (explicit override), then the version negotiated with the map
     * server on a previous run ({@link #VERSION_FILE}), then the compiled-in fallback
     * {@link #UNITY_CLIENT_VERSION}.
     */
    private String resolveClientVersion() {
        String explicit = System.getProperty("darkbot.unity.version");
        if (explicit != null && !explicit.trim().isEmpty()) {
            System.out.println("[unity] Version override from darkbot.unity.version: " + explicit.trim());
            return explicit.trim();
        }
        return versionStore.load().map(persisted -> {
            System.out.println("[unity] Using persisted negotiated version " + persisted
                    + " (delete " + VERSION_FILE + " to fall back to " + UNITY_CLIENT_VERSION + ")");
            return persisted;
        }).orElse(UNITY_CLIENT_VERSION);
    }

    /**
     * Called by the connector when the map server rejects our handshake hash and a
     * retry adopts the advertised one: updates the in-flight version and persists it
     * so the next launch skips the failed handshake.
     */
    private void onVersionNegotiated(String advertised) {
        clientVersion = advertised;
        if (versionStore.save(advertised)) {
            System.out.println("[unity] Persisted negotiated version " + advertised + " -> " + VERSION_FILE.toAbsolutePath());
        } else {
            System.err.println("[unity] Negotiated version " + advertised
                    + " could not be persisted to " + VERSION_FILE.toAbsolutePath());
        }
    }

    /** Route a legacy {@code API.moveShip} call to the Unity movement manager. */
    private void moveShipUnity(Locatable destination) {
        UnityGameState g = game;
        if (g != null && isSessionReady()) {
            g.getMovement().moveTo(destination.getX(), destination.getY());
        }
    }

    /**
     * Route a click/drag from DarkBot's map interface without entering the Flash
     * Drive loop.
     */
    public void moveShipFromMapInterface(Locatable destination) {
        UnityGameState g = game;
        if (g != null && isSessionReady()) {
            g.getMovement().moveToFromMapInterface(destination.getX(), destination.getY());
        }
    }

    /**
     * Unity has no Flash map event manager, so GameAPIImpl's legacy mapClick gate
     * would
     * reject every movement before reaching DirectInteraction. Route direct
     * movement
     * straight to the packet manager instead of requiring a minimap click.
     */
    @Override
    public void moveShip(Locatable destination) {
        moveShipUnity(destination);
    }

    /**
     * Uses a menu item directly through the packet-backed HeroItemsManager. Unlike
     * the legacy
     * Flash implementation this does not require the item to have a standard or
     * premium
     * quick-slot: the server menu id is sufficient (category-bar activation,
     * sourceType=0).
     */
    @Override
    public boolean useItem(Item item) {
        UnityGameState g = game;
        if (item == null || item.getId() == null || g == null || !isSessionReady())
            return false;
        return g.getItems().useItemId(item.getId()).isSuccessful();
    }

    /**
     * The Unity packet path supports direct item activation once the map session is
     * ready.
     */
    @Override
    public boolean isUseItemSupported() {
        return game != null && isSessionReady();
    }

    /** True when the map session is live and the hero snapshot has arrived. */
    private boolean isSessionReady() {
        SessionConnector c = connector;
        if (c == null)
            return false;
        GameConnection conn = c.connection();
        UnityGameState g = game;
        return conn != null && conn.state().isMapActive() && g != null && g.getHero().isValid();
    }

    @Override
    public boolean isSessionValid() {
        return isSessionReady();
    }

    @Override
    public boolean requestRelogin() {
        SessionConnector c = connector;
        return c != null && c.requestRelogin();
    }

    @Override
    public boolean disconnect() {
        SessionConnector c = connector;
        return c != null && c.disconnect();
    }

    /**
     * Whether the normal DarkBot module loop may run. The legacy GUI manager checks
     * native
     * memory addresses and therefore always reports false for packet sessions;
     * using it here
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
     * routes {@code requireAPI} calls here (the singletons
     * {@link java.util.HashSet} scan
     * alone would be a coin-flip, since the memory managers are registered as
     * singletons
     * at {@code Main} init, before this adapter exists).
     *
     * @return the unity manager for {@code api}, or {@code null} if the session
     *         pipeline
     *         is not up yet or the API is not packet-backed (falls back to the
     *         memory impl)
     */
    @SuppressWarnings("unchecked")
    public com.github.manolo8.darkbot.backpage.AuctionBackend getAuctionBackend() {
        UnityGameState g = game;
        return g == null ? null : new UnityAuctionBackend(g.getAuctions());
    }

    public SkylabAPI getSkylabManager() {
        UnityGameState g = game;
        return g == null ? null : g.getSkylab();
    }

    public <T extends API> T getManager(Class<T> api) {
        if (api == SessionAPI.class)
            return (T) this;
        UnityGameState g = game;
        if (g == null)
            return null;
        if (api == HeroAPI.class)
            return (T) g.getHero();
        if (api == HeroItemsAPI.class)
            return (T) g.getItems();
        if (api == GroupAPI.class)
            return (T) g.getGroup();
        if (api == EntitiesAPI.class)
            return (T) g.getEntities();
        if (api == StarSystemAPI.class)
            return (T) g.getStarSystem();
        if (api == EventBrokerAPI.class)
            return (T) g.getEventBroker();
        if (api == StatsAPI.class)
            return (T) g.getStats();
        if (api == RepairAPI.class)
            return (T) g.getRepair();
        if (api == OreAPI.class)
            return (T) g.getOres();
        if (api == InventoryAPI.class)
            return (T) g.getInventory();
        if (api == HangarAPI.class)
            return (T) g.getHangar();
        if (api == SkylabAPI.class)
            return (T) g.getSkylab();
        if (api == MovementAPI.class)
            return (T) g.getMovement();
        if (api == AttackAPI.class)
            return (T) g.getAttack();
        if (api == PetAPI.class)
            return (T) g.getPet();
        if (api == QuestAPI.class)
            return (T) g.getQuests();
        if (api == BoosterAPI.class)
            return (T) g.getBooster();
        if (api == DispatchAPI.class)
            return (T) g.getDispatch();
        if (api == ShipWarpAPI.class)
            return (T) g.getShipWarp();
        if (api == AssemblyAPI.class)
            return (T) g.getAssembly();
        if (api == ChatAPI.class)
            return (T) g.getChat();
        if (api == GameLogAPI.class)
            return (T) g.getLogs();
        return null;
    }

    /**
     * Starts the Unity session on a daemon worker: resolves credentials (from the
     * {@code -login} properties or the Unity login popup, whichever the user
     * picked;
     * portal auto-detect may take several POSTs), builds the game-state pipeline
     * and
     * connector, then keeps {@code BotInstaller.invalid} in sync with the session
     * state.
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
        this.diagnosticPortal = input.diagnosticPortal;
        this.targetMap = input.targetMap;
        openS2cCapture(input.captureS2C);
        openDriftReport(input.driftReport);

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
                    SessionConnector.LoginProvider provider = new SavedSessionProvider(identity, account,
                            clientVersion,
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
                    System.out.println(
                            "[unity] No server known: set server=<universe> (login file or Unity login dialog).");
                    return;
                }
                String lang = BigPointPortalHandler.langFor(srv);
                int requestedMap = input.mapId > 0 ? input.mapId : MAP_ID;
                System.out.println("[unity] Connecting to " + srv + " (lang=" + lang + ", version="
                        + clientVersion + ", map " + requestedMap + ")");

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
                    provider = new PortalLoginProvider(portal, new SessionIdentity(), clientVersion,
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
     * Resolves the Unity session credentials: from the {@code -login} properties
     * file when
     * provided (headless/scripted runs), otherwise from the standard Flash login
     * form shown
     * on the EDT. The selected adapter then decides whether those credentials feed
     * the Flash
     * client or the Unity packet session.
     *
     * @return the resolved portal/direct-session input, or {@code null} if the user
     *         dismissed
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
                    parseInt(props.getDiagnosticMoveDistance(), 200),
                    parseBooleanInt(props.getDiagnosticPortal(), false),
                    props.getTargetMap(),
                    props.getCaptureS2C(),
                    props.getDriftReport());
        }

        LoginData login = LoginUtils.performUserLogin(params, true);
        if (login == null) {
            System.out.println("[unity] Login dialog was dismissed without logging in");
            return null;
        }

        return new SessionInput(serverFromUrl(login.getUrl()), login.getUsername(),
                login.getPassword(), login.getSid(), null, 0, null, false,
                MAP_ID, false, false, 200, false, null, null, null);
    }

    /**
     * Converts the Flash login form's saved universe URL to the maps/session server
     * name.
     */
    private static String serverFromUrl(String url) {
        if (url == null || url.trim().isEmpty())
            return null;
        String server = url.trim().toLowerCase();
        int scheme = server.indexOf("://");
        if (scheme >= 0)
            server = server.substring(scheme + 3);
        int slash = server.indexOf('/');
        if (slash >= 0)
            server = server.substring(0, slash);
        if (server.endsWith(".darkorbit.com"))
            server = server.substring(0, server.length() - ".darkorbit.com".length());
        return server.isEmpty() ? null : server;
    }

    private static String first(String value, String fallback, String defaultValue) {
        return value != null && !value.trim().isEmpty() ? value
                : (fallback != null && !fallback.trim().isEmpty() ? fallback : defaultValue);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.trim().isEmpty())
            return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBooleanInt(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty())
            return fallback;
        return "1".equals(value.trim()) || Boolean.parseBoolean(value.trim());
    }

    /**
     * Connects Flash's persisted group policy to the transport-neutral group
     * manager.
     */
    private static void configureGroupAutomation(UnityGroupManager group) {
        UnityGroupManager.Automation policy = new UnityGroupManager.Automation();
        policy.acceptInvites = () -> Main.INSTANCE.config.GROUP.ACCEPT_INVITES;
        policy.openInvites = () -> Main.INSTANCE.config.GROUP.OPEN_INVITES;
        policy.blockInvites = () -> Main.INSTANCE.config.GROUP.BLOCK_INVITES;
        policy.leaveNoWhitelisted = () -> Main.INSTANCE.config.GROUP.LEAVE_NO_WHITELISTED;
        policy.whitelistConfigured = () -> Main.INSTANCE.config.GROUP.WHITELIST_TAG != null;
        policy.whitelist = info -> {
            eu.darkbot.api.config.types.PlayerTag tag = Main.INSTANCE.config.GROUP.WHITELIST_TAG;
            return tag == null || tag.hasTag(info);
        };
        policy.invite = info -> {
            eu.darkbot.api.config.types.PlayerTag tag = Main.INSTANCE.config.GROUP.INVITE_TAG;
            return tag != null && tag.hasTag(info);
        };
        policy.knownPlayers = () -> Main.INSTANCE.config.getPlayerInfos();
        group.setAutomation(policy);
    }

    /**
     * Applies the active DarkBot selector to packet locator picks. Kept as a small
     * seam so
     * selector priority and the reload fallback can be tested without starting a
     * live session.
     */
    static PetAPI.LocatorPick selectLocatorPick(PetGearSelectorHandler handler,
            Collection<? extends PetAPI.LocatorPick> picks) {
        if (handler == null)
            return null;
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
     * Starts the map-server connection on the session worker and loops publishing
     * the
     * session state to {@code BotInstaller.invalid} until the JVM exits. The
     * game-state
     * pipeline is already built ({@link #buildPipeline()} runs in the constructor);
     * this
     * only resolves the map list, connects and feeds frames into it.
     */
    private void runSession(SessionHttpClient http, SessionConnector.LoginProvider provider,
            SessionConnector.LoginMethod method) throws IOException {
        // URL is resolved lazily at refresh time: the portal login (which sets the
        // server on
        // the provider's identity) runs only when the connector starts, after this
        // call.
        MapServerTable maps = new MapServerTable(http, () -> MapServerTable.mapsPhpUrl(serverOf(provider)));

        UnityGameState g = game;

        // The connector relays every server→client frame into the game-state pipeline;
        // UnityGameState learns the hero id itself (first ShipInitializationCommand).
        FrameListener listener = g;
        if (traceOutbound || s2cCapture != null) {
            listener = (clientToServer, payload) -> {
                if (!clientToServer) {
                    writeCapturedFrame(payload);
                    if (traceOutbound)
                        traceInboundFrame(payload);
                }
                g.onFrame(clientToServer, payload);
            };
        }
        SessionConnector c = new SessionConnector(http, maps, provider, statusListener(), listener);
        c.setLoginMethod(method);
        // Persist the server-advertised hash when a handshake mismatch is renegotiated.
        c.setVersionNegotiatedListener(this::onVersionNegotiated);
        c.start();
        this.connector = c;
        startUnityChat(c, g);
        // Portal jumps: on JumpInitiatedCommand the connector must reconnect to the
        // DESTINATION map (the real client's setReconnectMap), not the login map.
        g.onJumpConfirmed(c::requestMapOverride);

        // Outbound channel: every manager/entity sends through the live connection.
        g.setPacketSender(packet -> {
            GameConnection conn = c.connection();
            if (conn == null) {
                if (traceOutbound)
                    System.out.println("[unity-c2s] " + packet.name() + " skipped: no connection");
                return false;
            }
            try {
                conn.send(packet);
                g.onOutbound(packet);
                if ("ChannelCloseRequest".equals(packet.name())) {
                    // The real client tears the TCP channel down right after the close
                    // request (conn5 frame 98). Force the disconnect so the connector
                    // reconnects on the jump's destination map instead of idling.
                    Thread closer = new Thread(() -> {
                        try {
                            Thread.sleep(1_000);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        conn.close();
                    }, "unity-jump-teardown");
                    closer.setDaemon(true);
                    closer.start();
                }
                if (traceOutbound) {
                    String mode = ("MoveRequest".equals(packet.name()) || "JumpRequest".equals(packet.name()))
                            ? "[" + g.getMovement().getLastActionMode().name().toLowerCase() + "] "
                            : "";
                    String details = "MoveRequest".equals(packet.name()) ? " " + packet.values() : "";
                    System.out.println("[unity-c2s] " + mode + packet.name() + details + " sent");
                }
                return true;
            } catch (IOException | RuntimeException e) {
                if (traceOutbound)
                    System.out.println("[unity-c2s] " + packet.name() + " failed: " + e.getMessage());
                return false;
            }
        });
        // KillScreenRepairRequest contains the same LoginRequest module as the current
        // connection. Binding it here is essential: sending a null nested module is
        // rejected
        // by PacketWriter and the Unity server ignores a repair without the session
        // identity.
        g.getRepair().setLoginRequestSupplier(() -> {
            GameConnection connection = c.connection();
            return connection == null ? null : connection.toLoginRequest();
        });

        if (diagnosticMove)
            startDiagnosticMove(c, g);
        if (diagnosticPortal)
            startDiagnosticPortal(c, g);

        long nextMetricsLog = System.currentTimeMillis() + 10_000;
        long nextDriftFlush = System.currentTimeMillis() + DRIFT_FLUSH_MS;
        long nextUnityReviveAt = 0;
        try {
            while (true) {
                nextUnityReviveAt = tickUnityRepair(g, c, nextUnityReviveAt);
                g.getRepair().tryInstantRepair(g.getEntities().getStations(),
                        g.getHero().getHealth().getHp(), g.getHero().getHealth().getMaxHp(),
                        Main.INSTANCE.config.GENERAL.SAFETY.INSTANT_REPAIR);
                // The memory PetManager's GUI tick never runs in Unity mode (Main drives
                // tickLogic directly), so forward the module's intent (setEnabled/setGear
                // calls on guiManager.pet) to the packet pet manager and let it act.
                com.github.manolo8.darkbot.core.manager.PetManager memoryPet = Main.INSTANCE.guiManager.pet;
                g.getPet().setEnabled(memoryPet.isEnabled());
                g.getPet().setOverride(memoryPet.getGearOverride());
                g.getPet().tick();
                // The Flash group manager used its GUI window for these policies. Packet Unity
                // executes the same safe subset through GroupAPI requests instead.
                g.getGroup().tickAutomation();
                botInstaller.invalid.send(!isSessionReady());
                if (traceOutbound && System.currentTimeMillis() >= nextMetricsLog) {
                    String drift = g.getDriftReport().hasAnomalies()
                            ? " " + g.getDriftReport().summary()
                            : "";
                    System.out.println("[unity-metrics] " + g.getActionMetrics().status() + drift);
                    nextMetricsLog = System.currentTimeMillis() + 10_000;
                }
                if (System.currentTimeMillis() >= nextDriftFlush) {
                    flushDriftReport(g);
                    nextDriftFlush = System.currentTimeMillis() + DRIFT_FLUSH_MS;
                }
                Thread.sleep(VALIDITY_POLL_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (chatConnection != null) chatConnection.close();
            // Final flush so the report reflects the whole session even on shutdown.
            flushDriftReport(g);
        }
    }

    /** Starts the optional chat channel when the portal supplied chatHost. */
    private void startUnityChat(SessionConnector session, UnityGameState g) {
        try {
            SessionBlock block = session.activeSession().orElse(null);
            if (block == null) return;
            String hostSpec = block.chatHost();
            if (hostSpec == null || hostSpec.trim().isEmpty()) return;
            String host = hostSpec.trim();
            int port = 80;
            int colon = host.lastIndexOf(':');
            if (colon > 0) {
                try { port = Integer.parseInt(host.substring(colon + 1)); host = host.substring(0, colon); }
                catch (NumberFormatException ignored) { }
            }
            String handshakeHex = System.getProperty("darkbot.unity.chat.handshakeHex", "");
            if (handshakeHex.trim().isEmpty()) {
                System.out.println("[unity] Chat channel disabled: set darkbot.unity.chat.handshakeHex after capturing the production handshake");
                return;
            }
            InfinicastConnection channel = new InfinicastConnection(
                    new InfinicastFrameCodec(new byte[0], 4 * 1024 * 1024),
                    (in, out) -> writeHandshake(out, handshakeHex),
                    g::onChatMessage);
            channel.connect(host, port);
            chatConnection = channel;
            g.getChat().bind(channel);
        } catch (Exception e) {
            System.err.println("[unity] Chat channel unavailable: " + e.getMessage());
        }
    }

    private static void writeHandshake(java.io.DataOutputStream out, String hex) throws IOException {
        String value = hex.replaceAll("[^0-9a-fA-F]", "");
        if ((value.length() & 1) != 0) throw new IOException("chat handshakeHex must contain complete bytes");
        for (int i = 0; i < value.length(); i += 2) out.writeByte(Integer.parseInt(value.substring(i, i + 2), 16));
        out.flush();
    }

    /**
     * Runs the packet equivalent of GuiManager's death/revive branch. Unity
     * deliberately skips
     * the native GUI tick, so without this worker-side path a detected kill screen
     * would remain
     * destroyed forever and no KillScreenRepairRequest would be sent.
     *
     * @param nextAttemptAt earliest timestamp for the next revive attempt
     * @return the updated earliest next attempt time; 0 once the hero is alive
     *         again
     */
    private long tickUnityRepair(UnityGameState g, SessionConnector c, long nextAttemptAt) {
        if (!g.getRepair().isDestroyed())
            return 0;

        long now = System.currentTimeMillis();
        if (now < nextAttemptAt)
            return nextAttemptAt;

        GameConnection connection = c.connection();
        if (connection == null || !connection.state().isMapActive())
            return nextAttemptAt;

        long waitMs = Math.max(0, Main.INSTANCE.config.GENERAL.SAFETY.WAIT_BEFORE_REVIVE * 1000L);
        java.time.Instant death = g.getRepair().getLastDeathTime();
        if (death != null) {
            long elapsed = java.time.Duration.between(death, java.time.Instant.now()).toMillis();
            if (elapsed < waitMs)
                return nextAttemptAt;
        }

        com.github.manolo8.darkbot.config.types.suppliers.ReviveLocation configured = Main.INSTANCE.config.GENERAL.SAFETY.REVIVE;
        eu.darkbot.api.game.enums.ReviveLocation location = eu.darkbot.api.game.enums.ReviveLocation
                .valueOf(configured.name());
        boolean sent = g.getRepair().revive(location);
        if (traceOutbound || sent) {
            System.out.println("[unity] " + (sent ? "KillScreenRepairRequest sent" : "KillScreenRepairRequest not sent")
                    + " location=" + location);
        }
        // Re-evaluate periodically: a sent request stays pending until the server
        // confirms the revive with a fresh ship initialization, and a rejected one
        // (option cooldown) may succeed on a later attempt.
        return now + REVIVE_RETRY_MS;
    }

    /**
     * /**
     * Opens the opt-in raw server→client frame dump given by the {@code captureS2C}
     * login
     * property. Frames are appended in the harness fixture format (3-byte
     * big-endian length
     * + payload), so the file feeds {@code unity-harness:detectDefs} directly to
     * regenerate
     * the packet dictionary after a server build rotates wire ids.
     */
    private void openS2cCapture(String path) {
        if (path == null)
            return;
        try {
            Path file = Paths.get(path);
            if (file.getParent() != null)
                Files.createDirectories(file.getParent());
            s2cCapture = Files.newOutputStream(file, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            System.out.println("[unity] Raw S2C capture -> " + file.toAbsolutePath()
                    + " (append; feed it to unity-harness:detectDefs)");
        } catch (IOException e) {
            System.err.println("[unity] Could not open S2C capture " + path + ": " + e.getMessage());
        }
    }

    /**
     * Appends one payload to the raw capture; one failed write disables the dump,
     * never the session.
     */
    private void writeCapturedFrame(byte[] payload) {
        OutputStream out = s2cCapture;
        if (out == null)
            return;
        try {
            out.write((payload.length >>> 16) & 0xFF);
            out.write((payload.length >>> 8) & 0xFF);
            out.write(payload.length & 0xFF);
            out.write(payload);
            out.flush();
        } catch (IOException e) {
            s2cCapture = null;
            System.err.println("[unity] S2C capture write failed, capture disabled: " + e.getMessage());
        }
    }

    /**
     * Resolves the opt-in drift-report destination ({@code driftReport} login property,
     * else {@code darkbot.unity.driftReport}); without it drift telemetry stays in memory
     * only.
     */
    private void openDriftReport(String path) {
        if (path == null)
            return;
        driftReportFile = Paths.get(path);
        System.out.println("[unity] Drift telemetry -> " + driftReportFile.toAbsolutePath()
                + " (periodic drift-report.json: unknown ids, decode failures, dispatch errors)");
    }

    /**
     * Dumps the game state's drift report if a destination is configured. Best effort:
     * {@link eu.darkbot.unity.codec.telemetry.DriftReport#writeTo} already swallows IO
     * errors, and a missing destination is a no-op.
     */
    private void flushDriftReport(UnityGameState g) {
        Path file = driftReportFile;
        if (file == null)
            return;
        if (g.getDriftReport().writeTo(file) && !driftWriteLogged) {
            driftWriteLogged = true;
            System.out.println("[unity] Drift report written -> " + file.toAbsolutePath());
        }
    }

    /**
     * Logs movement and action confirmations without dumping session or unrelated
     * packet data.
     */
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
                    if (value instanceof Number)
                        oreTotal += Math.max(0, ((Number) value).longValue());
                    if (ores.length() > 0)
                        ores.append(',');
                    ores.append(type).append('=').append(value);
                }
                System.out.println("[unity-s2c] AttributeOreCountUpdateCommand oreTotal="
                        + oreTotal + " values=" + ores);
            } else if ("LMCollectResourcesCommand".equals(name)) {
                long collected = 0;
                for (Map<String, Object> elem : inboundReader.listElements("contentList")) {
                    Object value = elem.get("contentList.elem.count");
                    if (value instanceof Number)
                        collected += Math.max(0, ((Number) value).longValue());
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
                // Diagnostic mode logs packet names only; never dump arbitrary fields or SID
                // data.
                System.out.println("[unity-s2c] packet=" + name);
            }
        } catch (IllegalArgumentException ignored) {
            // The game-state pipeline owns malformed-frame handling; tracing must not
            // affect it.
        }
    }

    /**
     * Action confirmations whose fields are useful for a packet-only live
     * diagnosis.
     */
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
     * Sends one controlled movement after the live hero snapshot arrives. This is
     * deliberately
     * opt-in and exists only to separate packet transport from module
     * target-selection logic.
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
            if (!g.getMovement().canMove(targetX, targetY))
                targetX = from.getX() - distance;
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

    /**
     * Opt-in portal travel ({@code diagnosticPortal} login property). With a
     * {@code targetMap} configured it routes every hop through
     * {@link StarSystemAPI#findNext} and repeats walk-jump-confirm until the
     * destination is reached — it never guesses among unvisited gates, which is
     * what previously landed runs on unintended maps. Only without a target does
     * it keep the one-shot "jump the nearest gate" mechanism diagnostic.
     */
    private void startDiagnosticPortal(SessionConnector c, UnityGameState g) {
        Thread t = new Thread(() -> runPortalTravel(c, g), "darkbot-unity-portal-travel");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Entry point of the travel thread: waits for READY, then travels or diagnoses.
     */
    private void runPortalTravel(SessionConnector c, UnityGameState g) {
        if (!awaitSessionReady(c))
            return;
        GameMap target = resolveTravelTarget(g);
        if (target != null)
            travelToMap(c, g, target);
        else
            jumpNearestPortalOnce(c, g);
    }

    /**
     * Blocks until the map session is READY (up to 30s). @return false when it
     * never was.
     */
    private boolean awaitSessionReady(SessionConnector c) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline && !isSessionReady()) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        if (!isSessionReady() || c.connection() == null) {
            System.out.println("[unity-travel] skipped: session did not become ready");
            return false;
        }
        return true;
    }

    /**
     * Resolves the configured destination, or {@code null} when travelling is not
     * requested.
     */
    private GameMap resolveTravelTarget(UnityGameState g) {
        String requested = targetMap;
        if (requested == null)
            return null;
        GameMap target = g.getStarSystem().getOrCreateMap(requested);
        System.out.println("[unity-travel] destination '" + requested + "' resolved to map id "
                + target.getId());
        return target;
    }

    /**
     * Repeats resolve-gate, walk, jump, confirm until the destination map is the
     * current one. When {@code findNext} cannot pick a gate yet (portals still
     * loading or no known route) it waits and retries instead of jumping blindly.
     */
    private void travelToMap(SessionConnector c, UnityGameState g, GameMap target) {
        long travelDeadline = System.currentTimeMillis() + 15 * 60_000;
        while (System.currentTimeMillis() < travelDeadline) {
            int currentId = g.getStarSystem().getCurrentMap().getId();
            if (currentId == target.getId()) {
                System.out.println("[unity-travel] arrived at map " + target.getId());
                return;
            }
            eu.darkbot.api.game.entities.Portal next = g.getStarSystem().findNext(target);
            if (next == null) {
                System.out.println("[unity-travel] no gate towards map " + target.getId()
                        + " from map " + currentId + " (visible portals: "
                        + visiblePortalCount(g) + "); waiting for route knowledge,"
                        + " NOT jumping blindly");
                dumpUnroutableState(g, currentId, target.getId());
                if (!sleep(5_000))
                    return;
                continue;
            }
            System.out.println("[unity-travel] map " + currentId + " -> gate " + next.getId()
                    + (next.getLocationInfo() == null ? ""
                            : " at " + (int) next.getLocationInfo().getX() + ","
                                    + (int) next.getLocationInfo().getY()));
            if (!walkToPortal(g, next, 120_000))
                return;
            int landedOn = jumpPortalUntilMapChanges(c, g, next);
            if (landedOn < 0)
                return;
        }
        System.out.println("[unity-travel] travel deadline reached on map "
                + g.getStarSystem().getCurrentMap().getId());
    }

    /**
     * One-shot mechanism diagnostic kept for gate testing: nearest gate, one jump.
     */
    private void jumpNearestPortalOnce(SessionConnector c, UnityGameState g) {
        eu.darkbot.api.game.entities.Portal portal = awaitNearestPortal(g, 30_000);
        if (portal == null) {
            System.out.println("[unity-travel] portal skipped: no portals visible");
            return;
        }
        if (!walkToPortal(g, portal, 120_000))
            return;
        jumpPortalUntilMapChanges(c, g, portal);
    }

    /**
     * Waits for portals to appear and returns the nearest initialized one, or null.
     */
    private static eu.darkbot.api.game.entities.Portal awaitNearestPortal(UnityGameState g,
            long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            eu.darkbot.api.game.entities.Portal nearest = nearestPortal(g);
            if (nearest != null)
                return nearest;
            if (!sleep(500))
                return null;
        }
        return null;
    }

    private static eu.darkbot.api.game.entities.Portal nearestPortal(UnityGameState g) {
        eu.darkbot.api.game.other.Location here = g.getMovement().getCurrentLocation();
        double best = Double.MAX_VALUE;
        eu.darkbot.api.game.entities.Portal nearest = null;
        for (eu.darkbot.api.game.entities.Portal p : g.getEntities().getPortals()) {
            if (p.getLocationInfo() == null || !p.getLocationInfo().isInitialized())
                continue;
            double d = here.distanceTo(p.getLocationInfo());
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        return nearest;
    }

    private static int visiblePortalCount(UnityGameState g) {
        Collection<? extends eu.darkbot.api.game.entities.Portal> portals = g.getEntities()
                .getPortals();
        return portals == null ? 0 : portals.size();
    }

    /**
     * Logs why no gate was selected on {@code fromMapId}: the routing graph state
     * plus
     * every visible portal's learned/static resolution, so a mismatch between the
     * live
     * gate data and the route catalog becomes visible instead of silent.
     */
    private void dumpUnroutableState(UnityGameState g, int fromMapId, int toMapId) {
        System.out.println("[unity-travel] route state: "
                + g.getStarSystem().describeRouteState(fromMapId, toMapId));
        Collection<? extends eu.darkbot.api.game.entities.Portal> portals = g.getEntities()
                .getPortals();
        if (portals == null)
            return;
        for (eu.darkbot.api.game.entities.Portal portal : portals) {
            if (portal == null)
                continue;
            eu.darkbot.api.game.other.LocationInfo loc = portal.getLocationInfo();
            String learned = g.getStarSystem().findPortalTarget(fromMapId, portal.getId())
                    .map(map -> "->" + map.getId()).orElse("-");
            String resolved = loc == null || !loc.isInitialized() ? "uninitialized"
                    : g.getStarSystem()
                            .findStaticPortalTarget(fromMapId, portal.getTypeId(),
                                    (int) loc.getX(), (int) loc.getY())
                            .map(map -> "->" + map.getId()).orElse("-");
            System.out.println("[unity-travel]   portal " + portal.getId()
                    + " type=" + portal.getTypeId()
                    + " pos=" + (loc == null ? "?" : (int) loc.getX() + "," + (int) loc.getY())
                    + " learned=" + learned + " static=" + resolved);
        }
    }

    /**
     * Walks until within 150 units of the portal. @return false when the gate is
     * unreachable, the session is gone or the thread was interrupted.
     */
    private boolean walkToPortal(UnityGameState g,
            eu.darkbot.api.game.entities.Portal portal, long timeoutMs) {
        eu.darkbot.api.game.other.LocationInfo loc = portal.getLocationInfo();
        if (loc == null || !loc.isInitialized()) {
            System.out.println("[unity-travel] gate " + portal.getId() + " has no location yet");
            return false;
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            eu.darkbot.api.game.other.Location here = g.getMovement().getCurrentLocation();
            if (here.distanceTo(loc) <= 150)
                return true;
            g.getMovement().moveTo(loc.getX(), loc.getY());
            if (!sleep(2_000))
                return false;
        }
        double remaining = g.getMovement().getCurrentLocation().distanceTo(loc);
        System.out.println("[unity-travel] gate " + portal.getId() + " unreachable: still "
                + (int) remaining + " units away");
        return remaining <= 400;
    }

    /**
     * Sends the jump repeatedly (the server drops it until its authoritative hero
     * position reaches the gate) until the current map changes. Survives the
     * mid-jump reconnect: {@code isSessionReady()} goes down while the connector
     * re-logs into the destination map, so only a stopped connector aborts.
     *
     * @return the landed map id, or -1 when the jump was never confirmed
     */
    private int jumpPortalUntilMapChanges(SessionConnector c, UnityGameState g,
            eu.darkbot.api.game.entities.Portal portal) {
        int mapBefore = g.getStarSystem().getCurrentMap().getId();
        long deadline = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < deadline) {
            System.out.println("[unity-travel] jumping gate " + portal.getId()
                    + " from map " + mapBefore);
            g.getMovement().jumpPortal(portal);
            if (!sleep(1_500))
                return -1;
            int mapNow = g.getStarSystem().getCurrentMap().getId();
            if (mapNow != mapBefore) {
                System.out.println("[unity-travel] jump confirmed, now on map " + mapNow);
                return mapNow;
            }
            if (!c.isRunning()) {
                System.out.println("[unity-travel] session ended during portal jump retry");
                return -1;
            }
        }
        System.out.println("[unity-travel] gate " + portal.getId()
                + " jump not confirmed after retries");
        return -1;
    }

    /**
     * Sleeps uninterruptibly-enough: restores the flag and reports interruption.
     */
    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Derives the game server for the maps URL from the provider's session
     * identity.
     */
    private static String serverOf(SessionConnector.LoginProvider provider) {
        // SavedSessionProvider and PortalLoginProvider both keep the server in the
        // identity;
        // fall back to a placeholder only if the session block never surfaced it.
        SessionIdentity identity = provider instanceof SavedSessionProvider || provider instanceof PortalLoginProvider
                ? identityOf(provider)
                : null;
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

    /*
     * ------------------------------ no-op components
     * ------------------------------
     */

    public static class NoOpWindow implements GameAPI.Window {
        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public void createWindow() {
        }
    }

    /**
     * Handler validity = the Unity map session being live and the hero snapshot
     * present.
     */
    public static class NoOpHandler implements GameAPI.Handler {
        /**
         * The owning adapter; assigned right after construction. Null-checked on every
         * use.
         */
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
            UnityPacketAdapter a = adapter;
            if (a != null) a.requestRelogin();
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
        public void readBytes(long address, byte[] buff, int length) {
        }

        @Override
        public void replaceInt(long address, int oldValue, int newValue) {
        }

        @Override
        public void replaceLong(long address, long oldValue, long newValue) {
        }

        @Override
        public void replaceDouble(long address, double oldValue, double newValue) {
        }

        @Override
        public void replaceBoolean(long address, boolean oldValue, boolean newValue) {
        }

        @Override
        public void writeInt(long address, int value) {
        }

        @Override
        public void writeLong(long address, long value) {
        }

        @Override
        public void writeDouble(long address, double value) {
        }

        @Override
        public void writeBoolean(long address, boolean value) {
        }

        @Override
        public void writeBytes(long address, byte... bytes) {
        }

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
        public void resetCache() {
        }
    }

    public static class NoOpInteraction implements GameAPI.Interaction {
        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public void keyClick(int keyCode) {
        }

        @Override
        public void sendText(String text) {
        }

        @Override
        public void mouseMove(int x, int y) {
        }

        @Override
        public void mouseDown(int x, int y) {
        }

        @Override
        public void mouseUp(int x, int y) {
        }

        @Override
        public void mouseClick(int x, int y) {
        }
    }

    /**
     * The legacy memory action seam: movement routes to the Unity movement manager.
     */
    public static class UnityDirectInteraction implements GameAPI.DirectInteraction {
        /**
         * The owning adapter; assigned right after construction. Null-checked on every
         * use.
         */
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
            if (a != null)
                a.moveShipUnity(destination);
        }

        @Override
        public void collectBox(Box box) {
        }

        @Override
        public void refine(long refineUtilAddress, OreAPI.Ore oreType, int amount) {
            UnityPacketAdapter a = adapter;
            if (a != null && a.isSessionReady() && a.game != null) {
                a.game.getOres().refine(oreType, amount);
            }
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
