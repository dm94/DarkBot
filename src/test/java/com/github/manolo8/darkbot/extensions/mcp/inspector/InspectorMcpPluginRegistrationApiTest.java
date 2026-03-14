package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

public class InspectorMcpPluginRegistrationApiTest {

    @Test
    public void testExternalPluginRegistersToolAndBridgeExecutesIt() {
        InspectorMcpToolRegistry registry = new InspectorMcpToolRegistry();
        InspectorMcpPluginRegistrationApi registrationApi = new InspectorMcpPluginRegistrationApi(registry);
        registrationApi.registerReadSafeTool(
                "external_echo",
                "Echo tool from plugin",
                List.of(new InspectorToolFieldSnapshot("message", "string", true, "Message to echo")),
                List.of(new InspectorToolFieldSnapshot("message", "string", true, "Echo response")),
                arguments -> {
                    String message = String.valueOf(arguments.getOrDefault("message", ""));
                    return new InspectorToolExecutionResult("external_echo", true,
                            "{\"message\":\"" + message + "\"}", "", false, message.length());
                });

        InspectorMcpToolExecutor executor = Mockito.mock(InspectorMcpToolExecutor.class);
        Mockito.when(executor.execute(Mockito.anyString(), Mockito.anyMap()))
                .thenAnswer(invocation -> registry.execute(invocation.getArgument(0), invocation.getArgument(1)));
        Mockito.when(executor.listToolSchemasAsJson()).thenReturn(new Gson().toJson(registry.listSchemas()));

        InspectorMcpBridgeService bridge = new InspectorMcpBridgeService(
                executor,
                new InspectorMcpPermissionService(registry, Set.of("127.0.0.1")),
                new InspectorMcpRateLimiter(20, 1_000),
                new InspectorMcpMetrics(),
                new Gson());

        String listResponse = bridge.handleRequest("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}",
                "127.0.0.1");
        JsonArray tools = JsonParser.parseString(listResponse).getAsJsonObject().getAsJsonArray("result");
        Assertions.assertTrue(containsTool(tools, "external_echo"));

        String callRequest = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"external_echo\",\"arguments\":{\"message\":\"hola\"}}}";
        String callResponse = bridge.handleRequest(callRequest, "127.0.0.1");
        JsonObject result = JsonParser.parseString(callResponse).getAsJsonObject().getAsJsonObject("result");
        JsonObject payload = JsonParser.parseString(result.get("payload").getAsString()).getAsJsonObject();

        Assertions.assertTrue(result.get("success").getAsBoolean());
        Assertions.assertEquals("external_echo", result.get("toolId").getAsString());
        Assertions.assertEquals("hola", payload.get("message").getAsString());
    }

    private boolean containsTool(JsonArray schemas, String toolId) {
        for (int index = 0; index < schemas.size(); index++) {
            JsonObject schema = schemas.get(index).getAsJsonObject();
            if (toolId.equals(schema.get("id").getAsString())) {
                return true;
            }
        }
        return false;
    }
}
