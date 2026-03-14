package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.github.manolo8.darkbot.Main;
import eu.darkbot.api.API;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class InspectorMcpSocketTransport implements API.Singleton {
  private static final int DEFAULT_PORT = 7788;
  private static final int MAX_PORT_ATTEMPTS = 30;
  private static final ThreadFactory ACCEPT_THREAD_FACTORY = createThreadFactory("mcp-accept");
  private static final ThreadFactory CLIENT_THREAD_FACTORY = createThreadFactory("mcp-client");
  private final InspectorMcpBridgeService bridgeService;
  private final ExecutorService acceptExecutor;
  private final ExecutorService clientExecutor;
  private Optional<ServerSocket> serverSocket;

  public InspectorMcpSocketTransport() {
    this(
        Main.INSTANCE.pluginAPI.requireInstance(InspectorMcpBridgeService.class),
        Executors.newSingleThreadExecutor(ACCEPT_THREAD_FACTORY),
        Executors.newFixedThreadPool(4, CLIENT_THREAD_FACTORY));
  }

  InspectorMcpSocketTransport(
      InspectorMcpBridgeService bridgeService,
      ExecutorService acceptExecutor,
      ExecutorService clientExecutor) {
    this.bridgeService = bridgeService;
    this.acceptExecutor = acceptExecutor;
    this.clientExecutor = clientExecutor;
    this.serverSocket = Optional.empty();
  }

  public synchronized int start() {
    return start(DEFAULT_PORT);
  }

  public synchronized int start(int preferredPort) {
    if (serverSocket.isPresent()) {
      return serverSocket.map(ServerSocket::getLocalPort).orElse(0);
    }
    ServerSocket boundSocket = bindSocket(preferredPort);
    serverSocket = Optional.of(boundSocket);
    acceptExecutor.submit(() -> acceptLoop(boundSocket));
    return boundSocket.getLocalPort();
  }

  public synchronized void stop() {
    Optional<ServerSocket> activeSocket = serverSocket;
    serverSocket = Optional.empty();
    if (activeSocket.isPresent()) {
      try {
        activeSocket.get().close();
      } catch (IOException ignored) {
      }
    }
  }

  public synchronized boolean isRunning() {
    return serverSocket.filter(socket -> !socket.isClosed()).isPresent();
  }

  public synchronized int getPort() {
    return serverSocket.map(ServerSocket::getLocalPort).orElse(0);
  }

  private ServerSocket bindSocket(int preferredPort) {
    if (preferredPort <= 0) {
      return bindEphemeralSocket();
    }
    int safePort = Math.max(1, preferredPort);
    for (int offset = 0; offset < MAX_PORT_ATTEMPTS; offset++) {
      int candidatePort = safePort + offset;
      try {
        ServerSocket socket = new ServerSocket(candidatePort, 32, InetAddress.getLoopbackAddress());
        socket.setReuseAddress(true);
        return socket;
      } catch (BindException ignored) {
      } catch (IOException e) {
        throw new IllegalStateException("Unable to initialize MCP socket transport", e);
      }
    }
    throw new IllegalStateException("No available localhost port for MCP socket transport");
  }

  private ServerSocket bindEphemeralSocket() {
    try {
      ServerSocket socket = new ServerSocket(0, 32, InetAddress.getLoopbackAddress());
      socket.setReuseAddress(true);
      return socket;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to bind ephemeral MCP socket transport port", e);
    }
  }

  private void acceptLoop(ServerSocket socket) {
    while (!socket.isClosed()) {
      try {
        Socket client = socket.accept();
        client.setTcpNoDelay(true);
        clientExecutor.submit(() -> handleClient(client));
      } catch (IOException e) {
        if (!socket.isClosed()) {
          throw new IllegalStateException("MCP socket accept failed", e);
        }
      }
    }
  }

  private void handleClient(Socket client) {
    try (Socket socket = client;
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
      String clientId = resolveClientId(socket);
      String requestLine;
      while ((requestLine = reader.readLine()) != null) {
        if (requestLine.trim().isEmpty()) {
          continue;
        }
        String response = bridgeService.handleRequest(requestLine, clientId);
        writer.write(response);
        writer.newLine();
        writer.flush();
      }
    } catch (IOException ignored) {
    }
  }

  private String resolveClientId(Socket socket) {
    return Optional.ofNullable(socket.getInetAddress())
        .map(InetAddress::getHostAddress)
        .filter(value -> !value.trim().isEmpty())
        .orElse("unknown");
  }

  private static ThreadFactory createThreadFactory(String prefix) {
    AtomicInteger counter = new AtomicInteger(0);
    return runnable -> {
      Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }
}
