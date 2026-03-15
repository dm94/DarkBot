package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.github.manolo8.darkbot.Main;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import eu.darkbot.api.API;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InspectorMcpToolExecutor implements API.Singleton {
  private static final int DEFAULT_SLOT_LIMIT = 250;
  private static final int DEFAULT_SEARCH_LIMIT = 100;
  private static final int MAX_PAYLOAD_LENGTH = 150_000;
  private static final int MAX_JSON_DEPTH = 8;
  private final InspectorContract inspectorContract;
  private final InspectorJsonSerializer serializer;
  private final InspectorMcpToolRegistry toolRegistry;
  private final InspectorMcpMetrics metrics;

  public InspectorMcpToolExecutor() {
    this.inspectorContract = Main.INSTANCE.pluginAPI.requireInstance(InspectorContract.class);
    this.serializer = Main.INSTANCE.pluginAPI.requireInstance(InspectorJsonSerializer.class);
    this.toolRegistry = Main.INSTANCE.pluginAPI.requireInstance(InspectorMcpToolRegistry.class);
    this.metrics = Main.INSTANCE.pluginAPI.requireInstance(InspectorMcpMetrics.class);
    registerBuiltInTools();
  }

  public InspectorToolExecutionResult execute(String toolId, Map<String, Object> arguments) {
    return toolRegistry.execute(toolId, arguments);
  }

  public String executeAsJson(String toolId, Map<String, Object> arguments) {
    return serializer.toJsonExecutionResult(execute(toolId, arguments));
  }

  public String listToolSchemasAsJson() {
    return serializer.toJsonToolSchemas(toolRegistry.listSchemas());
  }

  public List<InspectorToolSchemaSnapshot> listToolSchemas() {
    return toolRegistry.listSchemas();
  }

  private void registerBuiltInTools() {
    registerTool("list_roots", "Lista raíces de memoria conocidas para inspección", List.of(),
        List.of(field("roots", "InspectorRootSnapshot[]", true, "Raíces disponibles con id, label y address")),
        InspectorMcpToolPermission.READ_SAFE, arguments -> executeListRoots("list_roots"));
    registerTool("inspect_object", "Inspecciona un objeto por dirección y devuelve slots",
        List.of(
            field("address", "long", true, "Dirección base del objeto"),
            field("slotLimit", "int", false, "Límite máximo de slots")),
        List.of(field("object", "InspectorObjectSnapshot", true, "Nombre del objeto, dirección y slots")),
        InspectorMcpToolPermission.READ_SAFE, arguments -> executeInspectObject("inspect_object", arguments));
    registerTool("snapshot_root", "Obtiene snapshot de una raíz conocida",
        List.of(
            field("rootId", "string", true, "Identificador de la raíz"),
            field("slotLimit", "int", false, "Límite máximo de slots")),
        List.of(field("object", "InspectorObjectSnapshot", true, "Snapshot del objeto raíz")),
        InspectorMcpToolPermission.READ_SAFE, arguments -> executeSnapshotRoot("snapshot_root", arguments));
    registerTool("read_slot", "Lee un slot exacto por nombre desde una dirección",
        List.of(
            field("address", "long", true, "Dirección base del objeto"),
            field("slotName", "string", true, "Nombre exacto del slot")),
        List.of(field("slot", "InspectorSlotSnapshot", true, "Slot con metadatos y valor")),
        InspectorMcpToolPermission.READ_SAFE, arguments -> executeReadSlot("read_slot", arguments));
    registerTool("search_slot", "Busca slots por nombre o tipo",
        List.of(
            field("address", "long", true, "Dirección base del objeto"),
            field("query", "string", true, "Texto de búsqueda"),
            field("limit", "int", false, "Límite máximo de resultados")),
        List.of(field("slots", "InspectorSlotSnapshot[]", true, "Resultados de búsqueda")),
        InspectorMcpToolPermission.READ_SAFE, arguments -> executeSearchSlot("search_slot", arguments));
    registerTool("mcp_stats", "Métricas MCP de uso y errores por tool", List.of(),
        List.of(field("metrics", "InspectorMcpMetricsSnapshot", true, "Totales y métricas por tool")),
        InspectorMcpToolPermission.READ_PRIVILEGED, arguments -> executeMcpStats("mcp_stats"));
  }

  private void registerTool(String id, String description, List<InspectorToolFieldSnapshot> inputFields,
      List<InspectorToolFieldSnapshot> outputFields, InspectorMcpToolPermission permission,
      InspectorMcpToolHandler handler) {
    if (toolRegistry.isRegistered(id)) {
      return;
    }
    toolRegistry.registerTool(new InspectorToolSchemaSnapshot(id, description, true, new ArrayList<>(inputFields),
        new ArrayList<>(outputFields)), permission, handler);
  }

  private InspectorToolFieldSnapshot field(String name, String type, boolean required, String description) {
    return new InspectorToolFieldSnapshot(name, type, required, description);
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

  private InspectorToolExecutionResult executeMcpStats(String toolId) {
    return successResult(toolId, serializer.toJsonMetrics(metrics.snapshot()));
  }

  private InspectorToolExecutionResult successResult(String toolId, String payload) {
    String safePayload = Optional.ofNullable(payload).orElse("");
    String boundedByDepth = enforceDepthLimit(safePayload);
    int payloadLength = boundedByDepth.length();
    boolean truncated = payloadLength > MAX_PAYLOAD_LENGTH;
    String boundedPayload = truncated ? boundedByDepth.substring(0, MAX_PAYLOAD_LENGTH) : boundedByDepth;
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

  private String enforceDepthLimit(String payload) {
    try {
      JsonElement element = JsonParser.parseString(payload);
      JsonElement limited = limitDepth(element, 0);
      return limited.toString();
    } catch (JsonSyntaxException | IllegalStateException e) {
      return payload;
    }
  }

  private JsonElement limitDepth(JsonElement element, int depth) {
    if (depth >= MAX_JSON_DEPTH) {
      return new JsonPrimitive("[depth-limited]");
    }
    if (element.isJsonArray()) {
      return limitArray(element.getAsJsonArray(), depth + 1);
    }
    if (element.isJsonObject()) {
      return limitObject(element.getAsJsonObject(), depth + 1);
    }
    return element;
  }

  private JsonElement limitArray(JsonArray source, int depth) {
    JsonArray limited = new JsonArray();
    for (JsonElement child : source) {
      limited.add(limitDepth(child, depth));
    }
    return limited;
  }

  private JsonElement limitObject(JsonObject source, int depth) {
    JsonObject limited = new JsonObject();
    for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
      limited.add(entry.getKey(), limitDepth(entry.getValue(), depth));
    }
    return limited;
  }
}
