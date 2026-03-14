package com.github.manolo8.darkbot.extensions.mcp.inspector;

import eu.darkbot.api.API;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InspectorMcpToolRegistry implements API.Singleton {
  private final Map<String, InspectorToolSchemaSnapshot> schemasById = new LinkedHashMap<>();
  private final Map<String, InspectorMcpToolPermission> permissionsById = new LinkedHashMap<>();
  private final Map<String, InspectorMcpToolHandler> handlersById = new LinkedHashMap<>();

  public synchronized void registerTool(InspectorToolSchemaSnapshot schema, InspectorMcpToolPermission permission,
      InspectorMcpToolHandler handler) {
    String toolId = Optional.ofNullable(schema).map(InspectorToolSchemaSnapshot::getId).orElse("").trim();
    if (toolId.isEmpty()) {
      throw new IllegalArgumentException("Tool id is required");
    }
    if (schemasById.containsKey(toolId)) {
      throw new IllegalStateException("MCP tool already registered: " + toolId);
    }
    schemasById.put(toolId, schema);
    permissionsById.put(toolId, Optional.ofNullable(permission).orElse(InspectorMcpToolPermission.WRITE_DISABLED));
    handlersById.put(toolId, Optional.ofNullable(handler).orElse(arguments ->
        new InspectorToolExecutionResult(toolId, false, "", "Missing tool handler", false, 0)));
  }

  public synchronized boolean isRegistered(String toolId) {
    return schemasById.containsKey(Optional.ofNullable(toolId).orElse("").trim());
  }

  public synchronized Optional<InspectorMcpToolPermission> getPermission(String toolId) {
    String normalizedToolId = Optional.ofNullable(toolId).orElse("").trim();
    return Optional.ofNullable(permissionsById.get(normalizedToolId));
  }

  public synchronized Optional<InspectorToolSchemaSnapshot> getSchema(String toolId) {
    String normalizedToolId = Optional.ofNullable(toolId).orElse("").trim();
    return Optional.ofNullable(schemasById.get(normalizedToolId));
  }

  public synchronized List<InspectorToolSchemaSnapshot> listSchemas() {
    return new ArrayList<>(schemasById.values());
  }

  public InspectorToolExecutionResult execute(String toolId, Map<String, Object> arguments) {
    String normalizedToolId = Optional.ofNullable(toolId).orElse("").trim();
    if (normalizedToolId.isEmpty()) {
      return new InspectorToolExecutionResult("", false, "", "toolId is required", false, 0);
    }
    InspectorMcpToolHandler handler;
    synchronized (this) {
      handler = handlersById.get(normalizedToolId);
    }
    if (handler == null) {
      return new InspectorToolExecutionResult(normalizedToolId, false, "", "Unsupported tool: " + normalizedToolId,
          false, 0);
    }
    Map<String, Object> safeArguments = Optional.ofNullable(arguments).orElse(Map.of());
    return Optional.ofNullable(handler.execute(safeArguments))
        .orElse(new InspectorToolExecutionResult(normalizedToolId, false, "", "Tool returned empty result", false, 0));
  }
}
