package com.github.manolo8.darkbot.extensions.mcp.inspector;

import eu.darkbot.api.API;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InspectorMcpRateLimiter implements API.Singleton {
  private static final int DEFAULT_MAX_REQUESTS = 120;
  private static final long DEFAULT_WINDOW_MS = 60_000L;
  private static final String MAX_REQUESTS_PROPERTY = "darkbot.mcp.rate_limit.max_requests";
  private static final String WINDOW_MS_PROPERTY = "darkbot.mcp.rate_limit.window_ms";
  private final int maxRequests;
  private final long windowMs;
  private final Map<String, ClientWindow> windowsByClient = new ConcurrentHashMap<>();

  public InspectorMcpRateLimiter() {
    this(readPositiveInt(MAX_REQUESTS_PROPERTY, DEFAULT_MAX_REQUESTS),
        readPositiveLong(WINDOW_MS_PROPERTY, DEFAULT_WINDOW_MS));
  }

  InspectorMcpRateLimiter(int maxRequests, long windowMs) {
    this.maxRequests = maxRequests > 0 ? maxRequests : DEFAULT_MAX_REQUESTS;
    this.windowMs = windowMs > 0 ? windowMs : DEFAULT_WINDOW_MS;
  }

  public boolean allow(String clientId) {
    String normalizedClientId = normalizeClientId(clientId);
    ClientWindow window = windowsByClient.computeIfAbsent(normalizedClientId, ignored -> new ClientWindow());
    long now = System.currentTimeMillis();
    synchronized (window) {
      if (now - window.startedAt >= windowMs) {
        window.startedAt = now;
        window.requestCount = 0;
      }
      if (window.requestCount >= maxRequests) {
        return false;
      }
      window.requestCount++;
      return true;
    }
  }

  private static int readPositiveInt(String key, int fallback) {
    String rawValue = Optional.ofNullable(System.getProperty(key)).orElse("");
    try {
      int parsed = Integer.parseInt(rawValue.trim());
      return parsed > 0 ? parsed : fallback;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static long readPositiveLong(String key, long fallback) {
    String rawValue = Optional.ofNullable(System.getProperty(key)).orElse("");
    try {
      long parsed = Long.parseLong(rawValue.trim());
      return parsed > 0 ? parsed : fallback;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private String normalizeClientId(String clientId) {
    return Optional.ofNullable(clientId).orElse("unknown").trim().toLowerCase(Locale.ROOT);
  }

  private static final class ClientWindow {
    private long startedAt = System.currentTimeMillis();
    private int requestCount;
  }
}
