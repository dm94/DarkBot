package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.github.manolo8.darkbot.Main;
import eu.darkbot.api.API;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class InspectorMcpStdioTransport implements API.Singleton {
  private static final String STDIO_ENABLED_PROPERTY = "darkbot.mcp.stdio.enabled";
  private static final String STDIO_ENABLED_ENV = "DARKBOT_MCP_STDIO_ENABLED";
  private static final ThreadFactory STDIO_THREAD_FACTORY = createThreadFactory("mcp-stdio");
  private final InspectorMcpBridgeService bridgeService;
  private final BufferedReader reader;
  private final BufferedWriter writer;
  private final ExecutorService executor;
  private volatile boolean running;

  public InspectorMcpStdioTransport() {
    this(
        Main.INSTANCE.pluginAPI.requireInstance(InspectorMcpBridgeService.class),
        System.in,
        System.out,
        Executors.newSingleThreadExecutor(STDIO_THREAD_FACTORY));
  }

  InspectorMcpStdioTransport(
      InspectorMcpBridgeService bridgeService,
      InputStream inputStream,
      OutputStream outputStream,
      ExecutorService executor) {
    this(
        bridgeService,
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)),
        new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)),
        executor);
  }

  InspectorMcpStdioTransport(
      InspectorMcpBridgeService bridgeService,
      BufferedReader reader,
      BufferedWriter writer,
      ExecutorService executor) {
    this.bridgeService = bridgeService;
    this.reader = reader;
    this.writer = writer;
    this.executor = executor;
    this.running = false;
  }

  public synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    executor.submit(this::processLoop);
  }

  public synchronized void stop() {
    running = false;
  }

  public boolean isRunning() {
    return running;
  }

  public boolean isEnabled() {
    String property = Optional.ofNullable(System.getProperty(STDIO_ENABLED_PROPERTY)).orElse("");
    if (!property.trim().isEmpty()) {
      return parseBoolean(property);
    }
    String environment = Optional.ofNullable(System.getenv(STDIO_ENABLED_ENV)).orElse("");
    return parseBoolean(environment);
  }

  private void processLoop() {
    try {
      String requestLine;
      while (running && (requestLine = reader.readLine()) != null) {
        if (requestLine.trim().isEmpty()) {
          continue;
        }
        String response = bridgeService.handleRequest(requestLine, "stdio");
        if (response == null || response.trim().isEmpty()) {
          continue;
        }
        writer.write(response);
        writer.newLine();
        writer.flush();
      }
    } catch (IOException ignored) {
    } finally {
      running = false;
    }
  }

  private boolean parseBoolean(String value) {
    String normalized = Optional.ofNullable(value).orElse("").trim().toLowerCase(Locale.ROOT);
    return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
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
