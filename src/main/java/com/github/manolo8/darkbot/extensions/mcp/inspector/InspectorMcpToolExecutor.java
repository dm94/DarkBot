package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.github.manolo8.darkbot.Main;
import eu.darkbot.api.API;

import java.util.Map;
import java.util.Optional;

public class InspectorMcpToolExecutor implements API.Singleton {
  private static final int DEFAULT_SLOT_LIMIT = 250;
  private static final int DEFAULT_SEARCH_LIMIT = 100;
  private static final int MAX_PAYLOAD_LENGTH = 150_000;
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
        return executeListRoots(normalizedToolId);
      case "inspect_object":
        return executeInspectObject(normalizedToolId, safeArguments);
      case "snapshot_root":
        return executeSnapshotRoot(normalizedToolId, safeArguments);
      case "read_slot":
        return executeReadSlot(normalizedToolId, safeArguments);
      case "search_slot":
        return executeSearchSlot(normalizedToolId, safeArguments);
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

  private InspectorToolExecutionResult executeListRoots(String toolId) {
    return successResult(toolId, serializer.toJsonRoots(inspectorContract.listRoots()));
  }

  private InspectorToolExecutionResult executeInspectObject(String toolId, Map<String, Object> arguments) {
    Optional<Long> address = readPositiveLong(arguments, "address");
    if (address.isEmpty()) {
      return errorResult(toolId, "address must be a positive number");
    }
    int slotLimit = readInt(arguments, "slotLimit").orElse(DEFAULT_SLOT_LIMIT);
    return successResult(toolId, serializer.toJsonObject(inspectorContract.inspectObject(address.get(), slotLimit)));
  }

  private InspectorToolExecutionResult executeSnapshotRoot(String toolId, Map<String, Object> arguments) {
    Optional<String> rootId = readRequiredString(arguments, "rootId");
    if (rootId.isEmpty()) {
      return errorResult(toolId, "rootId is required");
    }
    int slotLimit = readInt(arguments, "slotLimit").orElse(DEFAULT_SLOT_LIMIT);
    return successResult(toolId, serializer.toJsonObject(inspectorContract.snapshotRoot(rootId.get(), slotLimit)));
  }

  private InspectorToolExecutionResult executeReadSlot(String toolId, Map<String, Object> arguments) {
    Optional<Long> address = readPositiveLong(arguments, "address");
    if (address.isEmpty()) {
      return errorResult(toolId, "address must be a positive number");
    }
    Optional<String> slotName = readRequiredString(arguments, "slotName");
    if (slotName.isEmpty()) {
      return errorResult(toolId, "slotName is required");
    }
    return successResult(toolId, serializer.toJsonSlot(inspectorContract.readSlot(address.get(), slotName.get())));
  }

  private InspectorToolExecutionResult executeSearchSlot(String toolId, Map<String, Object> arguments) {
    Optional<Long> address = readPositiveLong(arguments, "address");
    if (address.isEmpty()) {
      return errorResult(toolId, "address must be a positive number");
    }
    Optional<String> query = readRequiredString(arguments, "query");
    if (query.isEmpty()) {
      return errorResult(toolId, "query is required");
    }
    int limit = readInt(arguments, "limit").orElse(DEFAULT_SEARCH_LIMIT);
    return successResult(toolId, serializer.toJsonSlots(
        inspectorContract.searchSlots(address.get(), query.get(), limit)));
  }

  private InspectorToolExecutionResult successResult(String toolId, String payload) {
    String safePayload = Optional.ofNullable(payload).orElse("");
    int payloadLength = safePayload.length();
    boolean truncated = payloadLength > MAX_PAYLOAD_LENGTH;
    String boundedPayload = truncated ? safePayload.substring(0, MAX_PAYLOAD_LENGTH) : safePayload;
    return new InspectorToolExecutionResult(toolId, true, boundedPayload, "", truncated, payloadLength);
  }

  private InspectorToolExecutionResult errorResult(String toolId, String error) {
    return new InspectorToolExecutionResult(toolId, false, "", error, false, 0);
  }

  private Optional<Long> readPositiveLong(Map<String, Object> arguments, String key) {
    return readLong(arguments, key).filter(value -> value > 0);
  }

  private Optional<Long> readLong(Map<String, Object> arguments, String key) {
    Object value = arguments.get(key);
    if (value instanceof Number) {
      return Optional.of(((Number) value).longValue());
    }
    if (value instanceof String) {
      try {
        return Optional.of(Long.parseLong(((String) value).trim()));
      } catch (NumberFormatException ignored) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private Optional<Integer> readInt(Map<String, Object> arguments, String key) {
    Object value = arguments.get(key);
    if (value instanceof Number) {
      return Optional.of(((Number) value).intValue());
    }
    if (value instanceof String) {
      try {
        return Optional.of(Integer.parseInt(((String) value).trim()));
      } catch (NumberFormatException ignored) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private Optional<String> readRequiredString(Map<String, Object> arguments, String key) {
    return Optional.ofNullable(arguments.get(key))
        .map(String::valueOf)
        .map(String::trim)
        .filter(value -> !value.isEmpty());
  }
}
