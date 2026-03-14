package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.github.manolo8.darkbot.Main;
import eu.darkbot.api.API;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class InspectorMcpPermissionService implements API.Singleton {
  private static final String PRIVILEGED_CLIENTS_PROPERTY = "darkbot.mcp.privileged.clients";
  private static final String PRIVILEGED_CLIENTS_ENV = "DARKBOT_MCP_PRIVILEGED_CLIENTS";
  private final InspectorMcpToolRegistry toolRegistry;
  private final Set<String> privilegedClients;

  public InspectorMcpPermissionService() {
    this(Main.INSTANCE.pluginAPI.requireInstance(InspectorMcpToolRegistry.class), loadPrivilegedClients());
  }

  InspectorMcpPermissionService(InspectorMcpToolRegistry toolRegistry, Set<String> privilegedClients) {
    this.toolRegistry = toolRegistry;
    this.privilegedClients = Collections.unmodifiableSet(Optional.ofNullable(privilegedClients).orElse(Set.of()));
  }

  public boolean isAllowed(String clientId, String toolId) {
    Optional<InspectorMcpToolPermission> permission = toolRegistry.getPermission(toolId);
    if (permission.isEmpty()) {
      return false;
    }
    switch (permission.get()) {
      case READ_SAFE:
        return true;
      case READ_PRIVILEGED:
        return privilegedClients.contains(normalizeClientId(clientId));
      default:
        return false;
    }
  }

  private static Set<String> loadPrivilegedClients() {
    String configured = Optional.ofNullable(System.getProperty(PRIVILEGED_CLIENTS_PROPERTY))
        .orElse(Optional.ofNullable(System.getenv(PRIVILEGED_CLIENTS_ENV)).orElse(""));
    return Arrays.stream(configured.split(","))
        .map(String::trim)
        .map(InspectorMcpPermissionService::normalizeClientId)
        .filter(value -> !value.isEmpty())
        .collect(Collectors.toSet());
  }

  private static String normalizeClientId(String clientId) {
    return Optional.ofNullable(clientId).orElse("").trim().toLowerCase(Locale.ROOT);
  }
}
