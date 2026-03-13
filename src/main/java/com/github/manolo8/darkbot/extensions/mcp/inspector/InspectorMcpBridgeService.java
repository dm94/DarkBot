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

public class InspectorMcpBridgeService implements API.Singleton {
  private final InspectorMcpToolExecutor executor;
  private final Gson gson;

  public InspectorMcpBridgeService() {
    this.executor = Main.INSTANCE.pluginAPI.requireInstance(InspectorMcpToolExecutor.class);
    this.gson = new Gson();
  }

  public String handleRequest(String requestJson) {
    if (requestJson == null || requestJson.trim().isEmpty()) {
      return toErrorResponse(null, "Invalid request: empty body");
    }
    JsonObject request;
    try {
      request = JsonParser.parseString(requestJson).getAsJsonObject();
    } catch (IllegalStateException | JsonSyntaxException e) {
      return toErrorResponse(null, "Invalid request: malformed JSON");
    }

    JsonElement id = request.get("id");
    String method = readString(request, "method");
    if (method.isEmpty()) {
      return toErrorResponse(id, "Invalid request: method is required");
    }

    if ("tools/list".equals(method)) {
      return toSuccessResponse(id, executor.listToolSchemasAsJson());
    }
    if ("tools/call".equals(method)) {
      return handleToolCall(id, request.getAsJsonObject("params"));
    }
    return toErrorResponse(id, "Method not found: " + method);
  }

  private String handleToolCall(JsonElement id, JsonObject params) {
    if (params == null) {
      return toErrorResponse(id, "Invalid params: object required");
    }
    String toolName = readString(params, "name");
    if (toolName.isEmpty()) {
      return toErrorResponse(id, "Invalid params: name is required");
    }
    Map<String, Object> arguments = readArguments(params.getAsJsonObject("arguments"));
    String resultJson = executor.executeAsJson(toolName, arguments);
    return toSuccessResponse(id, resultJson);
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
