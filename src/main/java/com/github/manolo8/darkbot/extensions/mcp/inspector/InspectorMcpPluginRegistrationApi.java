package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.github.manolo8.darkbot.Main;
import eu.darkbot.api.API;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InspectorMcpPluginRegistrationApi implements API.Singleton {
  private final InspectorMcpToolRegistry toolRegistry;

  public InspectorMcpPluginRegistrationApi() {
    this(Main.INSTANCE.pluginAPI.requireInstance(InspectorMcpToolRegistry.class));
  }

  InspectorMcpPluginRegistrationApi(InspectorMcpToolRegistry toolRegistry) {
    this.toolRegistry = toolRegistry;
  }

  public void registerReadSafeTool(String id, String description, List<InspectorToolFieldSnapshot> inputFields,
      List<InspectorToolFieldSnapshot> outputFields, InspectorMcpToolHandler handler) {
    registerTool(id, description, inputFields, outputFields, InspectorMcpToolPermission.READ_SAFE, handler);
  }

  public void registerReadPrivilegedTool(String id, String description, List<InspectorToolFieldSnapshot> inputFields,
      List<InspectorToolFieldSnapshot> outputFields, InspectorMcpToolHandler handler) {
    registerTool(id, description, inputFields, outputFields, InspectorMcpToolPermission.READ_PRIVILEGED, handler);
  }

  public void registerTool(String id, String description, List<InspectorToolFieldSnapshot> inputFields,
      List<InspectorToolFieldSnapshot> outputFields, InspectorMcpToolPermission permission,
      InspectorMcpToolHandler handler) {
    String toolId = Optional.ofNullable(id).orElse("").trim();
    if (toolId.isEmpty()) {
      throw new IllegalArgumentException("Tool id is required");
    }
    InspectorToolSchemaSnapshot schema = new InspectorToolSchemaSnapshot(
        toolId,
        Optional.ofNullable(description).orElse("").trim(),
        true,
        new ArrayList<>(Optional.ofNullable(inputFields).orElse(List.of())),
        new ArrayList<>(Optional.ofNullable(outputFields).orElse(List.of())));
    toolRegistry.registerTool(schema,
        Optional.ofNullable(permission).orElse(InspectorMcpToolPermission.WRITE_DISABLED), handler);
  }

  public boolean isToolRegistered(String toolId) {
    return toolRegistry.isRegistered(toolId);
  }
}
