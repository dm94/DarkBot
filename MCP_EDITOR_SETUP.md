# DarkBot MCP Setup for Major Editors

This guide explains how to connect DarkBot's MCP server to common editors using stdio mode.

## 1) Prerequisites

- Java installed and available in `PATH`
- DarkBot built locally
- A valid path to the DarkBot JAR

Build the project:

```bash
./gradlew jar
```

On Windows:

```powershell
.\gradlew.bat jar
```

## 2) DarkBot MCP Runtime Flags

DarkBot supports:

- Socket mode on localhost (default)
- Stdio mode (recommended for editor MCP integration)

Enable stdio with one of these:

- JVM property: `-Ddarkbot.mcp.stdio.enabled=true`
- Environment variable: `DARKBOT_MCP_STDIO_ENABLED=true`

Recommended launch command:

```bash
java -Ddarkbot.mcp.stdio.enabled=true -jar /absolute/path/to/DarkBot-<version>.jar
```

Windows example:

```powershell
java -Ddarkbot.mcp.stdio.enabled=true -jar D:\Github\DarkBot\build\libs\DarkBot-1.131.jar
```

## 3) Base MCP Server Definition

Most editors use an MCP JSON schema with `mcpServers`.

```json
{
  "mcpServers": {
    "darkbot": {
      "command": "java",
      "args": [
        "-Ddarkbot.mcp.stdio.enabled=true",
        "-jar",
        "D:\\Github\\DarkBot\\build\\libs\\DarkBot-1.131.jar"
      ],
      "env": {
        "DARKBOT_MCP_STDIO_ENABLED": "true"
      }
    }
  }
}
```

Use either the JVM property or the env variable. Keeping both is fine.

## 4) Cursor

Open Cursor MCP settings and add the `darkbot` server using the base definition above.

Typical config file on Windows:

- `%USERPROFILE%\.cursor\mcp.json`

Typical config file on Linux/macOS:

- `~/.cursor/mcp.json`

After saving, restart Cursor and check MCP tools list.

## 5) VS Code (MCP-compatible extensions)

Use an MCP-compatible extension (for example Cline/Continue MCP-capable versions) and paste the same `mcpServers` block in that extension's MCP config file.

Common location for Cline:

- `.vscode/cline_mcp_settings.json`

Then restart VS Code and confirm the `darkbot` server appears as connected.

## 6) Windsurf

Open Windsurf MCP configuration and add the same `mcpServers.darkbot` entry.

Typical user-level path:

- `%USERPROFILE%\.codeium\windsurf\mcp_config.json` (Windows)
- `~/.codeium/windsurf/mcp_config.json` (Linux/macOS)

Restart Windsurf after saving.

## 7) Quick Validation

After connecting from your editor:

1. Call `initialize`
2. Call `tools/list`
3. Ensure tools like `list_roots` are returned
4. Call `tools/call` with `list_roots`

If these work, your MCP integration is ready.

## 8) Troubleshooting

- If the editor cannot connect, verify Java and JAR paths.
- If no tools appear, restart editor and DarkBot process.
- If stdio fails, test launching DarkBot manually with `-Ddarkbot.mcp.stdio.enabled=true`.
- If your editor uses a different MCP config path or key names, keep the same command/args/env values and adapt only the wrapper format.
