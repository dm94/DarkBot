package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.github.manolo8.darkbot.Main;
import eu.darkbot.api.API;

import java.util.Map;
import java.util.Optional;

public class InspectorMcpToolExecutor implements API.Singleton {
  private final InspectorContract inspectorContract;
  private final InspectorJsonSerializer serializer;

  public InspectorMcpToolExecutor() {
    this.inspectorContract = Main.INSTANCE.pluginAPI.requireInstance(InspectorContract.class);
    this.serializer = Main.INSTANCE.pluginAPI.requireInstance(InspectorJsonSerializer.class);
  }

  public InspectorToolExecutionResult execute(String toolId, Map<String, Object> arguments) {
    if (toolId == null || toolId.trim().isEmpty()) {
      return errorResult("", "toolId is required");
    }
    String normalizedToolId = toolId.trim();
    if (!inspectorContract.getSupportedTools().contains(normalizedToolId)) {
      return errorResult(normalizedToolId, "Unsupported tool: " + normalizedToolId);
    }
    Map<String, Object> safeArguments = Optional.ofNullable(arguments).orElse(Map.of());
    switch (normalizedToolId) {
      case "list_roots":
        return successResult(normalizedToolId, serializer.toJsonRoots(inspectorContract.listRoots()));
      case "inspect_object":
        return successResult(normalizedToolId, serializer.toJsonObject(
            inspectorContract.inspectObject(getLong(safeArguments, "address", 0L),
                getInt(safeArguments, "slotLimit", 250))));
      case "snapshot_root":
        return successResult(normalizedToolId, serializer.toJsonObject(
            inspectorContract.snapshotRoot(getString(safeArguments, "rootId", ""),
                getInt(safeArguments, "slotLimit", 250))));
      case "read_slot":
        return successResult(normalizedToolId, serializer.toJsonSlot(
            inspectorContract.readSlot(getLong(safeArguments, "address", 0L),
                getString(safeArguments, "slotName", ""))));
      case "search_slot":
        return successResult(normalizedToolId, serializer.toJsonSlots(
            inspectorContract.searchSlots(getLong(safeArguments, "address", 0L),
                getString(safeArguments, "query", ""), getInt(safeArguments, "limit", 100))));
      default:
        return errorResult(normalizedToolId, "Unsupported tool: " + normalizedToolId);
    }
  }

  public String executeAsJson(String toolId, Map<String, Object> arguments) {
    return serializer.toJsonExecutionResult(execute(toolId, arguments));
  }

  public String listToolSchemasAsJson() {
    return serializer.toJsonToolSchemas(inspectorContract.getToolSchemas());
  }

  private InspectorToolExecutionResult successResult(String toolId, String payload) {
    return new InspectorToolExecutionResult(toolId, true, payload, "");
  }

  private InspectorToolExecutionResult errorResult(String toolId, String error) {
    return new InspectorToolExecutionResult(toolId, false, "", error);
  }

  private long getLong(Map<String, Object> arguments, String key, long defaultValue) {
    Object value = arguments.get(key);
    if (value instanceof Number)
      return ((Number) value).longValue();
    if (value instanceof String) {
      try {
        return Long.parseLong(((String) value).trim());
      } catch (NumberFormatException ignored) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  private int getInt(Map<String, Object> arguments, String key, int defaultValue) {
    Object value = arguments.get(key);
    if (value instanceof Number)
      return ((Number) value).intValue();
    if (value instanceof String) {
      try {
        return Integer.parseInt(((String) value).trim());
      } catch (NumberFormatException ignored) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  private String getString(Map<String, Object> arguments, String key, String defaultValue) {
    return Optional.ofNullable(arguments.get(key)).map(String::valueOf).map(String::trim).orElse(defaultValue);
  }
}
