# AGENTS.md - DarkBot Project Context

## Project Overview

DarkBot is a bot automation client for DarkOrbit (MMO space shooter game). It reads game process memory via JNI to automate gameplay: collecting resources, attacking NPCs, navigating maps, repairing ships, etc.

- **Language:** Java 11
- **Build System:** Gradle 8.6 (Kotlin DSL)
- **UI Framework:** Swing with FlatLaf (dark theme)
- **Architecture:** Plugin-based modular system with JNI native interop
- **Version:** 1.131.7

## Setup Commands

### First Time Setup
```bash
# Clone already done, but ensure Gradle wrapper is executable
gradlew.bat --version

# Build the project
gradlew.bat clean build
```

### Build Commands
```bash
# Full build (compiles + creates JAR)
gradlew.bat build

# Clean build (removes old artifacts first)
gradlew.bat clean build

# Build with ProGuard (optimized JAR)
gradlew.bat proguard

# Run tests only
gradlew.bat test
```

### Run the Bot
```bash
# The bot requires a release build from Discord to run
# 1. Get latest release from https://discord.gg/uXHnZJ9
# 2. Unzip release to a folder outside the project
# 3. Configure IDE:
#    - Main class: com.github.manolo8.darkbot.Bot
#    - Working directory: path to unzipped release
```

## Project Structure

```
src/main/java/
├── eu/darkbot/              # API layer (native JNI bindings)
│   ├── api/                 # DarkMem, DarkInput, DarkHook (native)
│   └── hook/               # Native callback system
├── com/github/manolo8/darkbot/
│   ├── Bot.java            # Entry point (main class)
│   ├── Main.java           # Core loop, managers initialization
│   ├── core/               # Core game API & entities
│   │   ├── api/            # GameAPI interface & implementation
│   │   ├── entities/       # Ship, Npc, Player, Box, Portal, etc.
│   │   └── manager/        # HeroManager, MapManager, StatsManager, etc.
│   ├── modules/            # Bot behavior modules
│   │   ├── LootModule.java
│   │   ├── CollectorModule.java
│   │   ├── MapModule.java
│   │   └── utils/          # SafetyFinder, NpcAttacker, MapTraveler
│   ├── config/             # Configuration system
│   │   ├── Config.java     # Main config (zones, NPCs, players)
│   │   └── tree/           # Config tree UI
│   ├── gui/                # Swing UI
│   │   ├── MainGui.java
│   │   └── MapDrawer.java
│   ├── extensions/         # Plugin system
│   │   └── plugins/        # PluginHandler, PluginClassLoader
│   ├── backpage/           # Game backpage/API integration
│   └── utils/              # Utilities (I18n, Discord, HTTP)
└── test/                   # Unit tests (JUnit 5 + Mockito)
```

## Key Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `eu.darkbot.DarkBotAPI:darkbot-impl` | 0.9.9 | Shared DarkBot API |
| `com.google.code.gson:gson` | 2.8.9 | JSON config serialization |
| `com.formdev:flatlaf` | 3.4 | Modern Swing UI |
| `org.jgrapht:jgrapht-core` | 1.5.2 | Graph algorithms (map navigation) |
| `it.unimi.dsi:fastutil-core` | 8.5.13 | High-performance collections |

## Testing Instructions

```bash
# Run all tests
gradlew.bat test

# Run specific test class
gradlew.bat test --tests "eu.darkbot.util.RecyclingQueueTest"

# Run with coverage (if configured)
gradlew.bat jacocoTestReport
```

- Test framework: JUnit 5 + Mockito
- Test location: `src/test/java/`
- Currently has minimal tests - contribution welcome

## Code Style Guidelines

- **Java version:** 11 (no var, no modules)
- **Encoding:** UTF-8 (enforced in build)
- **Lombok:** Used via `io.freefair.lombok` plugin
- **Naming:** Standard Java conventions
  - Classes: PascalCase (`HeroManager`)
  - Methods: camelCase (`getShip`)
  - Constants: UPPER_SNAKE_CASE (`MAX_SPEED`)
- **Imports:** Organized, no wildcards
- **Packages:** `eu.darkbot.*` for API, `com.github.manolo8.darkbot.*` for implementation

## Plugin System

DarkBot supports plugins loaded from `plugins/` directory:
- Plugins are JAR files with `PluginDefinition`
- Loaded via `PluginClassLoader` (custom classloader)
- Can hook into the bot's event system
- See `src/main/java/eu/darkbot/extensions/plugins/` for API

## Important Notes

1. **Native Libraries:** The bot uses JNI for memory reading (DarkMemAPI), input simulation (DarkInputAPI), and Flash hooking (DarkHookAPI). These are loaded from `lib/` directory at runtime.

2. **Game Client Required:** The bot cannot run standalone - it requires a DarkOrbit game client (Tanos or KekkaPlayer) running.

3. **Config Files:** 
   - `config.json` - Main bot configuration
   - `credentials.json` - Encrypted credentials (never commit)
   - Both are in `.gitignore`

4. **Memory Reading:** The bot reads game memory directly. This is version-specific - game updates may break functionality until the API is updated.

## Build Output

- Compiled classes: `build/classes/`
- Fat JAR: `build/libs/DarkBot.jar` (after `proguard` task)
- ProGuard output: `build/DarkBot.jar`

## Common Issues

1. **Build fails with "Could not resolve eu.darkbot"** - JitPack may be slow, try `--refresh-dependencies`
2. **Native library errors** - Ensure `lib/` directory has the required DLLs
3. **Version mismatch** - Bot version must match game client version

## Development Tips

- Use `Main.java` as entry point for understanding bot flow
- `HeroManager` controls the player's ship
- `MapManager` handles map navigation and portals
- `SafetyFinder` evaluates safe zones (configured in `config.json`)
- Plugin development: extend `Plugin` class and implement `PluginDefinition`
