package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

public class InspectorMcpBridgeServiceTest {

    @Test
    public void testBridgeRejectsRateLimitedRequest() {
        InspectorMcpToolExecutor executor = Mockito.mock(InspectorMcpToolExecutor.class);
        InspectorMcpPermissionService permissionService = Mockito.mock(InspectorMcpPermissionService.class);
        InspectorMcpRateLimiter rateLimiter = Mockito.mock(InspectorMcpRateLimiter.class);
        InspectorMcpMetrics metrics = Mockito.mock(InspectorMcpMetrics.class);
        Mockito.when(rateLimiter.allow("127.0.0.1")).thenReturn(false);

        InspectorMcpBridgeService bridge = new InspectorMcpBridgeService(
                executor, permissionService, rateLimiter, metrics, new Gson());

        String response = bridge.handleRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", "127.0.0.1");
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        Assertions.assertTrue(json.has("error"));
        Assertions.assertTrue(json.getAsJsonObject("error").get("message").getAsString().contains("Rate limit"));
    }

    @Test
    public void testBridgeRejectsPermissionDeniedToolCall() {
        InspectorMcpToolExecutor executor = Mockito.mock(InspectorMcpToolExecutor.class);
        InspectorMcpPermissionService permissionService = Mockito.mock(InspectorMcpPermissionService.class);
        InspectorMcpRateLimiter rateLimiter = Mockito.mock(InspectorMcpRateLimiter.class);
        InspectorMcpMetrics metrics = Mockito.mock(InspectorMcpMetrics.class);
        Mockito.when(rateLimiter.allow("127.0.0.1")).thenReturn(true);
        Mockito.when(permissionService.isAllowed("127.0.0.1", "mcp_stats")).thenReturn(false);

        InspectorMcpBridgeService bridge = new InspectorMcpBridgeService(
                executor, permissionService, rateLimiter, metrics, new Gson());

        String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"mcp_stats\",\"arguments\":{}}}";
        String response = bridge.handleRequest(request, "127.0.0.1");
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        Assertions.assertTrue(json.has("error"));
        Assertions.assertTrue(json.getAsJsonObject("error").get("message").getAsString().contains("Permission denied"));
    }

    @Test
    public void testBridgeReturnsToolExecutionResult() {
        InspectorMcpToolExecutor executor = Mockito.mock(InspectorMcpToolExecutor.class);
        InspectorMcpPermissionService permissionService = Mockito.mock(InspectorMcpPermissionService.class);
        InspectorMcpRateLimiter rateLimiter = Mockito.mock(InspectorMcpRateLimiter.class);
        InspectorMcpMetrics metrics = Mockito.mock(InspectorMcpMetrics.class);
        Mockito.when(rateLimiter.allow("127.0.0.1")).thenReturn(true);
        Mockito.when(permissionService.isAllowed("127.0.0.1", "list_roots")).thenReturn(true);
        Mockito.when(executor.execute(Mockito.eq("list_roots"), Mockito.anyMap()))
                .thenReturn(new InspectorToolExecutionResult("list_roots", true, "[]", "", false, 2));

        InspectorMcpBridgeService bridge = new InspectorMcpBridgeService(
                executor, permissionService, rateLimiter, metrics, new Gson());

        String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"list_roots\",\"arguments\":{}}}";
        String response = bridge.handleRequest(request, "127.0.0.1");
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        JsonObject result = json.getAsJsonObject("result");

        Assertions.assertTrue(result.get("success").getAsBoolean());
        Assertions.assertEquals("list_roots", result.get("toolId").getAsString());
        Mockito.verify(executor).execute("list_roots", Map.of());
    }

    @Test
    public void testBridgeHandlesInitializeAndInitialized() {
        InspectorMcpToolExecutor executor = Mockito.mock(InspectorMcpToolExecutor.class);
        InspectorMcpPermissionService permissionService = Mockito.mock(InspectorMcpPermissionService.class);
        InspectorMcpRateLimiter rateLimiter = Mockito.mock(InspectorMcpRateLimiter.class);
        InspectorMcpMetrics metrics = Mockito.mock(InspectorMcpMetrics.class);
        Mockito.when(rateLimiter.allow("127.0.0.1")).thenReturn(true);

        InspectorMcpBridgeService bridge = new InspectorMcpBridgeService(
                executor, permissionService, rateLimiter, metrics, new Gson());

        String initializeRequest = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\"}}";
        String initializeResponse = bridge.handleRequest(initializeRequest, "127.0.0.1");
        JsonObject result = JsonParser.parseString(initializeResponse).getAsJsonObject().getAsJsonObject("result");
        Assertions.assertEquals("2024-11-05", result.get("protocolVersion").getAsString());
        Assertions.assertEquals("darkbot-mcp", result.getAsJsonObject("serverInfo").get("name").getAsString());

        String initializedRequest = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}";
        String initializedResponse = bridge.handleRequest(initializedRequest, "127.0.0.1");
        Assertions.assertEquals("", initializedResponse);
    }

    @Test
    public void testBridgeReturnsMcpToolListFormat() {
        InspectorMcpToolExecutor executor = Mockito.mock(InspectorMcpToolExecutor.class);
        InspectorMcpPermissionService permissionService = Mockito.mock(InspectorMcpPermissionService.class);
        InspectorMcpRateLimiter rateLimiter = Mockito.mock(InspectorMcpRateLimiter.class);
        InspectorMcpMetrics metrics = Mockito.mock(InspectorMcpMetrics.class);
        Mockito.when(rateLimiter.allow("127.0.0.1")).thenReturn(true);
        Mockito.when(executor.listToolSchemas()).thenReturn(List.of(
                new InspectorToolSchemaSnapshot(
                        "list_roots",
                        "List roots",
                        true,
                        List.of(),
                        List.of())));

        InspectorMcpBridgeService bridge = new InspectorMcpBridgeService(
                executor, permissionService, rateLimiter, metrics, new Gson());

        String response = bridge.handleRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", "127.0.0.1");
        JsonObject result = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("result");
        JsonObject firstTool = result.getAsJsonArray("tools").get(0).getAsJsonObject();
        Assertions.assertEquals("list_roots", firstTool.get("name").getAsString());
        Assertions.assertTrue(firstTool.has("inputSchema"));
    }
}
