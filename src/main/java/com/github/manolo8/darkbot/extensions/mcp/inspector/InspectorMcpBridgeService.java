package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.github.manolo8.darkbot.Main;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import eu.darkbot.api.API;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InspectorMcpBridgeService implements API.Singleton {
  private static final String MCP_PROTOCOL_VERSION = "2024-11-05";
  private static final String MCP_SERVER_NAME = "darkbot-mcp";
  private final InspectorMcpToolExecutor executor;
  private final InspectorMcpPermissionService permissionService;
  private final InspectorMcpRateLimiter rateLimiter;
  private final InspectorMcpMetrics metrics;
  private final Gson gson;

  public InspectorMcpBridgeService() {
    this(Main.INSTANCE.pluginAPI.requireInstance(InspectorMcpToolExecutor.class),
        Main.INSTANCE.pluginAPI.requireInstance(InspectorMcpPermissionService.class),
        Main.INSTANCE.pluginAPI.requireInstance(InspectorMcpRateLimiter.class),
        Main.INSTANCE.pluginAPI.requireInstance(InspectorMcpMetrics.class),
        new Gson());
  }

  InspectorMcpBridgeService(InspectorMcpToolExecutor executor, InspectorMcpPermissionService permissionService,
      InspectorMcpRateLimiter rateLimiter, InspectorMcpMetrics metrics, Gson gson) {
    this.executor = executor;
    this.permissionService = permissionService;
    this.rateLimiter = rateLimiter;
    this.metrics = metrics;
    this.gson = gson;
  }

  public String handleRequest(String requestJson) {
    return handleRequest(requestJson, "unknown");
  }

  public String handleRequest(String requestJson, String clientId) {
    String normalizedClientId = readClientId(clientId);
    if (!rateLimiter.allow(normalizedClientId)) {
      metrics.recordRateLimited();
      return toErrorResponse(null, "Rate limit exceeded");
    }
    metrics.recordRequest();
    if (requestJson == null || requestJson.trim().isEmpty()) {
      metrics.recordProtocolError();
      return toErrorResponse(null, "Invalid request: empty body");
    }
    JsonObject request;
    try {
      request = JsonParser.parseString(requestJson).getAsJsonObject();
    } catch (IllegalStateException | JsonSyntaxException e) {
      metrics.recordProtocolError();
      return toErrorResponse(null, "Invalid request: malformed JSON");
    }

    JsonElement id = request.get("id");
    String method = readString(request, "method");
    if (method.isEmpty()) {
      metrics.recordProtocolError();
      return toErrorResponse(id, "Invalid request: method is required");
    }

    if ("tools/list".equals(method)) {
      return handleToolsList(id);
    }
    if ("initialize".equals(method)) {
      return handleInitialize(id);
    }
    if ("notifications/initialized".equals(method)) {
      return "";
    }
    if ("tools/call".equals(method)) {
      return handleToolCall(id, request.getAsJsonObject("params"), normalizedClientId);
    }
    metrics.recordProtocolError();
    return toErrorResponse(id, "Method not found: " + method);
  }

  private String handleToolCall(JsonElement id, JsonObject params, String clientId) {
    if (params == null) {
      metrics.recordProtocolError();
      return toErrorResponse(id, "Invalid params: object required");
    }
    String toolName = readString(params, "name");
    if (toolName.isEmpty()) {
      metrics.recordProtocolError();
      return toErrorResponse(id, "Invalid params: name is required");
    }
    if (!permissionService.isAllowed(clientId, toolName)) {
      metrics.recordToolCall(toolName, false, false, 0L);
      return toErrorResponse(id, "Permission denied for tool: " + toolName);
    }
    Map<String, Object> arguments = readArguments(params.getAsJsonObject("arguments"));
    long start = System.nanoTime();
    InspectorToolExecutionResult result = executor.execute(toolName, arguments);
    metrics.recordToolCall(toolName, result.isSuccess(), result.isTruncated(), System.nanoTime() - start);
    return toSuccessResponse(id, toMcpToolCallResult(toolName, result));
  }

  private String readClientId(String clientId) {
    return Optional.ofNullable(clientId).orElse("").trim();
  }

  private String handleToolsList(JsonElement id) {
    long start = System.nanoTime();
    JsonObject result = new JsonObject();
    result.add("tools", toMcpToolsArray(executor.listToolSchemas()));
    metrics.recordToolCall("tools/list", true, false, System.nanoTime() - start);
    return toSuccessResponse(id, result);
  }

  private String handleInitialize(JsonElement id) {
    JsonObject result = new JsonObject();
    result.addProperty("protocolVersion", MCP_PROTOCOL_VERSION);
    JsonObject capabilities = new JsonObject();
    capabilities.add("tools", new JsonObject());
    result.add("capabilities", capabilities);
    JsonObject serverInfo = new JsonObject();
    serverInfo.addProperty("name", MCP_SERVER_NAME);
    serverInfo.addProperty("version", Main.VERSION.toString());
    result.add("serverInfo", serverInfo);
    return toSuccessResponse(id, result);
  }

  private JsonObject toMcpToolCallResult(String toolName, InspectorToolExecutionResult result) {
    JsonObject response = new JsonObject();
    boolean success = result.isSuccess();
    response.addProperty("isError", !success);
    response.addProperty("success", success);
    response.addProperty("toolId", toolName);
    response.addProperty("payload", result.getPayload());
    response.addProperty("error", result.getError());
    response.addProperty("truncated", result.isTruncated());
    response.addProperty("payloadLength", result.getPayloadLength());
    response.add("content", toMcpContent(result));
    JsonElement structuredContent = readStructuredContent(result.getPayload());
    if (!structuredContent.isJsonNull()) {
      response.add("structuredContent", structuredContent);
    }
    return response;
  }

  private JsonElement toMcpToolsArray(List<InspectorToolSchemaSnapshot> schemas) {
    com.google.gson.JsonArray tools = new com.google.gson.JsonArray();
    for (InspectorToolSchemaSnapshot schema : schemas) {
      tools.add(toMcpTool(schema));
    }
    return tools;
  }

  private JsonObject toMcpTool(InspectorToolSchemaSnapshot schema) {
    JsonObject tool = new JsonObject();
    tool.addProperty("name", schema.getId());
    tool.addProperty("description", schema.getDescription());
    tool.add("inputSchema", toInputSchema(schema.getInputFields()));
    JsonObject annotations = new JsonObject();
    annotations.addProperty("readOnlyHint", schema.isReadOnly());
    tool.add("annotations", annotations);
    return tool;
  }

  private JsonObject toInputSchema(List<InspectorToolFieldSnapshot> inputFields) {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject properties = new JsonObject();
    List<String> required = new ArrayList<>();
    for (InspectorToolFieldSnapshot field : inputFields) {
      properties.add(field.getName(), toFieldSchema(field));
      if (field.isRequired()) {
        required.add(field.getName());
      }
    }
    schema.add("properties", properties);
    com.google.gson.JsonArray requiredFields = new com.google.gson.JsonArray();
    for (String fieldName : required) {
      requiredFields.add(fieldName);
    }
    schema.add("required", requiredFields);
    schema.addProperty("additionalProperties", false);
    return schema;
  }

  private JsonObject toFieldSchema(InspectorToolFieldSnapshot field) {
    String normalizedType = Optional.ofNullable(field.getType()).orElse("object").trim();
    JsonObject fieldSchema = new JsonObject();
    if (normalizedType.endsWith("[]")) {
      fieldSchema.addProperty("type", "array");
      JsonObject itemSchema = new JsonObject();
      itemSchema.addProperty("type", inferJsonType(normalizedType.substring(0, normalizedType.length() - 2)));
      fieldSchema.add("items", itemSchema);
    } else {
      fieldSchema.addProperty("type", inferJsonType(normalizedType));
    }
    fieldSchema.addProperty("description", field.getDescription());
    return fieldSchema;
  }

  private String inferJsonType(String typeName) {
    String normalized = Optional.ofNullable(typeName).orElse("").trim().toLowerCase();
    switch (normalized) {
      case "string":
        return "string";
      case "int":
      case "long":
      case "double":
      case "float":
      case "number":
        return "number";
      case "boolean":
      case "bool":
        return "boolean";
      default:
        return "object";
    }
  }

  private JsonElement toMcpContent(InspectorToolExecutionResult result) {
    com.google.gson.JsonArray content = new com.google.gson.JsonArray();
    JsonObject item = new JsonObject();
    item.addProperty("type", "text");
    if (result.isSuccess()) {
      item.addProperty("text", result.getPayload());
    } else {
      item.addProperty("text", result.getError());
    }
    content.add(item);
    return content;
  }

  private JsonElement readStructuredContent(String payload) {
    String safePayload = Optional.ofNullable(payload).orElse("").trim();
    if (safePayload.isEmpty()) {
      return JsonNull.INSTANCE;
    }
    try {
      JsonElement element = JsonParser.parseString(safePayload);
      if (element.isJsonObject() || element.isJsonArray()) {
        return element;
      }
      return JsonNull.INSTANCE;
    } catch (JsonSyntaxException e) {
      return JsonNull.INSTANCE;
    }
  }

  private String toSuccessResponse(JsonElement id, JsonElement resultElement) {
    JsonObject response = new JsonObject();
    response.addProperty("jsonrpc", "2.0");
    if (id == null)
      response.add("id", JsonNull.INSTANCE);
    else
      response.add("id", id);
    response.add("result", Optional.ofNullable(resultElement).orElse(JsonNull.INSTANCE));
    return gson.toJson(response);
  }

  private String toSuccessResponse(JsonElement id, String resultJson) {
    try {
      return toSuccessResponse(id, JsonParser.parseString(resultJson));
    } catch (JsonSyntaxException e) {
      return toSuccessResponse(id, new JsonPrimitive(Optional.ofNullable(resultJson).orElse("")));
    }
  }

  private String toErrorResponse(JsonElement id, String message) {
    JsonObject response = new JsonObject();
    response.addProperty("jsonrpc", "2.0");
    if (id == null)
      response.add("id", JsonNull.INSTANCE);
    else
      response.add("id", id);
    JsonObject error = new JsonObject();
    error.addProperty("message", message);
    response.add("error", error);
    return gson.toJson(response);
  }

  private String readString(JsonObject object, String key) {
    if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
      return "";
    }
    try {
      return object.get(key).getAsString().trim();
    } catch (UnsupportedOperationException | ClassCastException | IllegalStateException e) {
      return "";
    }
  }

  private Map<String, Object> readArguments(JsonObject argumentsObject) {
    if (argumentsObject == null) {
      return Collections.emptyMap();
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = gson.fromJson(argumentsObject, HashMap.class);
      return map == null ? Collections.emptyMap() : map;
    } catch (JsonSyntaxException e) {
      return Collections.emptyMap();
    }
  }
}
