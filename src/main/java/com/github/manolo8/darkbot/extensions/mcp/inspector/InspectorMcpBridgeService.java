package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.github.manolo8.darkbot.Main;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import eu.darkbot.api.API;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InspectorMcpBridgeService implements API.Singleton {
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
      long start = System.nanoTime();
      String response = toSuccessResponse(id, executor.listToolSchemasAsJson());
      metrics.recordToolCall("tools/list", true, false, System.nanoTime() - start);
      return response;
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
    String resultJson = gson.toJson(result);
    return toSuccessResponse(id, resultJson);
  }

  private String readClientId(String clientId) {
    return Optional.ofNullable(clientId).orElse("").trim();
  }

  private String toSuccessResponse(JsonElement id, String resultJson) {
    JsonObject response = new JsonObject();
    response.addProperty("jsonrpc", "2.0");
    if (id == null)
      response.add("id", JsonNull.INSTANCE);
    else
      response.add("id", id);
    try {
      response.add("result", JsonParser.parseString(resultJson));
    } catch (JsonSyntaxException e) {
      response.addProperty("result", resultJson);
    }
    return gson.toJson(response);
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
