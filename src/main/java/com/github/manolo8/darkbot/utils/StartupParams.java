package com.github.manolo8.darkbot.utils;

import com.github.manolo8.darkbot.gui.utils.Strings;
import eu.darkbot.api.API;
import eu.darkbot.util.function.ThrowingFunction;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class StartupParams implements API.Singleton {
    private static final String COMMAND_PREFIX = "-";

    public enum LaunchArg {
        /**
         * Auto-login without a login pop-up, requires a path to a properties file
         * with a username and either a password or a master-password.
         * Example usage: {@code -login C:\Users\Owner\login.properties}
         */
        LOGIN(AutoLoginProps::new),
        START,
        /** Auto-start the bot */
        NO_OP,
        /** Run the bot in no-op mode (no-op api) */
        CONFIG(s -> s),
        /** Start the bot with a specific config */
        HIDE,
        /** If the bot should hide api window on start */
        NO_WARN;

        /** Disable warnings about unsupported java version */

        private final ThrowingFunction<String, ?, Exception> parser;

        LaunchArg() {
            this(null);
        }

        LaunchArg(ThrowingFunction<String, ?, Exception> parser) {
            this.parser = parser;
        }

        public static LaunchArg of(String str) {
            while (str.startsWith(COMMAND_PREFIX))
                str = str.substring(1);
            try {
                return LaunchArg.valueOf(str.toUpperCase(Locale.ROOT).replace("-", "_"));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public Object parse(String param) {
            if (parser == null)
                return true;
            try {
                return parser.apply(param);
            } catch (Exception e) {
                System.err.println("Failed to parse parameter for argument: " + this);
                e.printStackTrace();
                return null;
            }
        }
    }

    public enum PropertyKey {
        USERNAME, PASSWORD, MASTER_PASSWORD, SERVER, SID, GAME_SID, USER_ID, INSTANCE,
        MINI_CLIENT, MAP_ID, CAPTURED_LOGIN_FILE, TRACE_OUTBOUND, DIAGNOSTIC_MOVE,
        DIAGNOSTIC_MOVE_DISTANCE, DIAGNOSTIC_PORTAL, CAPTURE_S2C, ALLOW_STORE_SID;

        @Override
        public String toString() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    private final Map<LaunchArg, Object> startupParams = new HashMap<>();

    public StartupParams(String[] args) throws IOException {
        for (int i = 0; i < args.length; i++) {
            String strArgument = args[i];
            LaunchArg arg = LaunchArg.of(strArgument);
            if (arg == null) {
                System.err.println("Unknown startup argument: " + strArgument + " , ignoring argument.");
                continue;
            }
            if (arg.parser != null)
                i++;
            if (i >= args.length) {
                System.err.println("Missing required argument for " + strArgument);
                break;
            }
            startupParams.put(arg, arg.parse(args[i]));
        }
    }

    public AutoLoginProps getAutoLoginProps() {
        return (AutoLoginProps) startupParams.getOrDefault(LaunchArg.LOGIN, null);
    }

    /* Other params */
    public boolean has(LaunchArg arg) {
        return startupParams.containsKey(arg);
    }

    public boolean getAutoLogin() {
        return has(LaunchArg.LOGIN);
    }

    public boolean getAutoStart() {
        return has(LaunchArg.START);
    }

    public boolean useNoOp() {
        return has(LaunchArg.NO_OP);
    }

    public String getStartConfig() {
        return (String) startupParams.getOrDefault(LaunchArg.CONFIG, null);
    }

    public boolean getAutoHide() {
        return has(LaunchArg.HIDE);
    }

    @Override
    public String toString() {
        return "StartupParams{" + startupParams.entrySet().stream()
                .map(e -> e.getKey().toString() + "= " + e.getValue().toString())
                .collect(Collectors.joining(","));
    }

    public static class AutoLoginProps {
        private final Properties prop;
        private final String path;

        private AutoLoginProps(String path) throws IOException {
            this.prop = new Properties();
            this.path = path;
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)) {
                prop.load(reader);
            }
            System.out.println("Loaded startup properties file");
        }

        private String getProperty(PropertyKey key) {
            return prop.getProperty(key.toString());
        }

        private String getProperty(PropertyKey key, String camelCaseAlias) {
            String value = getProperty(key);
            return value != null ? value : prop.getProperty(camelCaseAlias);
        }

        private void setProperty(PropertyKey key, String val) {
            prop.setProperty(key.toString(), val);
        }

        public String getUsername() {
            return getProperty(PropertyKey.USERNAME);
        }

        public String getPassword() {
            return getProperty(PropertyKey.PASSWORD);
        }

        public char[] getMasterPassword() {
            String masterPassword = getProperty(PropertyKey.MASTER_PASSWORD);
            return masterPassword == null ? null : masterPassword.toCharArray();
        }

        public String getServer() {
            return getProperty(PropertyKey.SERVER);
        }

        public String getSID() {
            return getProperty(PropertyKey.SID);
        }

        /** Raw map-server SID captured from the Unity client's LoginRequest. */
        public String getGameSID() {
            return getProperty(PropertyKey.GAME_SID, "gameSid");
        }

        /** Player id captured from the Unity client's LoginRequest. */
        public String getUserId() {
            return getProperty(PropertyKey.USER_ID, "userId");
        }

        /** Gameserver instance/pid captured from the Unity client's LoginRequest. */
        public String getInstance() {
            return getProperty(PropertyKey.INSTANCE, "instance");
        }

        /**
         * Whether the LoginRequest should identify this connection as a mini client.
         */
        public String getMiniClient() {
            return getProperty(PropertyKey.MINI_CLIENT, "miniClient");
        }

        /** Initial map id to resolve in maps.php. */
        public String getMapId() {
            return getProperty(PropertyKey.MAP_ID, "mapId");
        }

        /** Optional path to a harness captured-login.properties file. */
        public String getCapturedLoginFile() {
            return getProperty(PropertyKey.CAPTURED_LOGIN_FILE, "capturedLoginFile");
        }

        /** Enables logging of outbound packet names for a diagnostic run. */
        public String getTraceOutbound() {
            return getProperty(PropertyKey.TRACE_OUTBOUND, "traceOutbound");
        }

        /** Enables one controlled movement after the session reaches READY. */
        public String getDiagnosticMove() {
            return getProperty(PropertyKey.DIAGNOSTIC_MOVE, "diagnosticMove");
        }

        /** Distance in map units for the one-shot diagnostic movement. */
        public String getDiagnosticMoveDistance() {
            return getProperty(PropertyKey.DIAGNOSTIC_MOVE_DISTANCE, "diagnosticMoveDistance");
        }

        /**
         * Enables a one-shot diagnostic travel to the nearest portal and jump attempt.
         */
        public String getDiagnosticPortal() {
            return getProperty(PropertyKey.DIAGNOSTIC_PORTAL, "diagnosticPortal");
        }

        /**
         * Optional path for a raw server→client frame dump (3-byte framed fixture
         * format),
         * used to regenerate the packet dictionary with
         * {@code unity-harness:detectDefs}.
         */
        public String getCaptureS2C() {
            return getProperty(PropertyKey.CAPTURE_S2C, "captureS2C");
        }

        public boolean isAllowStoreSID() {
            return Boolean.parseBoolean(getProperty(PropertyKey.ALLOW_STORE_SID));
        }

        public boolean shouldSIDLogin() {
            return !Strings.isEmpty(getSID()) && !Strings.isEmpty(getServer());
        }

        public void setServer(String server) {
            setProperty(PropertyKey.SERVER, server);
        }

        public void setSID(String sid) {
            setProperty(PropertyKey.SID, sid);
        }

        public void updateLoginFile() {
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path),
                    StandardCharsets.UTF_8)) {
                prop.store(writer, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
