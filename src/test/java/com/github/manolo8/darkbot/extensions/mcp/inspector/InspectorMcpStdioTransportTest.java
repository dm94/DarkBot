package com.github.manolo8.darkbot.extensions.mcp.inspector;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class InspectorMcpStdioTransportTest {

    @Test
    public void testStdioTransportHandlesRequestLine() throws Exception {
        InspectorMcpBridgeService bridgeService = Mockito.mock(InspectorMcpBridgeService.class);
        String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        String expectedResponse = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}";
        Mockito.when(bridgeService.handleRequest(request, "stdio")).thenReturn(expectedResponse);

        ByteArrayInputStream inputStream = new ByteArrayInputStream((request + "\n").getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        InspectorMcpStdioTransport transport =
                new InspectorMcpStdioTransport(bridgeService, inputStream, outputStream, executor);

        transport.start();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        String output = outputStream.toString(StandardCharsets.UTF_8).trim();
        Assertions.assertEquals(expectedResponse, output);
        Assertions.assertFalse(transport.isRunning());
    }
}
