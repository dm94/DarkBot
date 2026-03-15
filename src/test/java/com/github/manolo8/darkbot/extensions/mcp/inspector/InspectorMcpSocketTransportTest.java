package com.github.manolo8.darkbot.extensions.mcp.inspector;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InspectorMcpSocketTransportTest {

    @Test
    public void testTransportHandlesRequestLine() throws Exception {
        InspectorMcpBridgeService bridgeService = Mockito.mock(InspectorMcpBridgeService.class);
        String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        String expectedResponse = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[]}";
        Mockito.when(bridgeService.handleRequest(Mockito.eq(request), Mockito.anyString())).thenReturn(expectedResponse);

        ExecutorService acceptExecutor = Executors.newSingleThreadExecutor();
        ExecutorService clientExecutor = Executors.newFixedThreadPool(2);
        InspectorMcpSocketTransport transport =
                new InspectorMcpSocketTransport(bridgeService, acceptExecutor, clientExecutor);

        int port = transport.start(0);
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write(request);
            writer.newLine();
            writer.flush();

            String response = reader.readLine();
            Assertions.assertEquals(expectedResponse, response);
        } finally {
            transport.stop();
            acceptExecutor.shutdownNow();
            clientExecutor.shutdownNow();
        }
    }
}
