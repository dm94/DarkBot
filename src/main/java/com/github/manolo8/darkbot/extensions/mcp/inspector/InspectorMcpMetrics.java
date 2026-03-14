package com.github.manolo8.darkbot.extensions.mcp.inspector;

import eu.darkbot.api.API;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class InspectorMcpMetrics implements API.Singleton {
  private final LongAdder totalRequests = new LongAdder();
  private final LongAdder totalErrors = new LongAdder();
  private final LongAdder totalRateLimited = new LongAdder();
  private final Map<String, ToolCounters> countersByTool = new ConcurrentHashMap<>();

  public void recordRequest() {
    totalRequests.increment();
  }

  public void recordRateLimited() {
    totalRateLimited.increment();
    totalErrors.increment();
  }

  public void recordProtocolError() {
    totalErrors.increment();
  }

  public void recordToolCall(String toolId, boolean success, boolean truncated, long latencyNanos) {
    ToolCounters counters = countersByTool.computeIfAbsent(toolId, ignored -> new ToolCounters());
    counters.requests.increment();
    if (!success) {
      counters.errors.increment();
      totalErrors.increment();
    }
    if (truncated) {
      counters.truncatedResponses.increment();
    }
    counters.totalLatencyNanos.add(Math.max(latencyNanos, 0L));
  }

  public InspectorMcpMetricsSnapshot snapshot() {
    Map<String, ToolCounters> ordered = new TreeMap<>(countersByTool);
    List<InspectorMcpToolMetricsSnapshot> tools = new ArrayList<>();
    for (Map.Entry<String, ToolCounters> entry : ordered.entrySet()) {
      tools.add(buildToolSnapshot(entry.getKey(), entry.getValue()));
    }
    return new InspectorMcpMetricsSnapshot(totalRequests.longValue(), totalErrors.longValue(),
        totalRateLimited.longValue(), tools);
  }

  private InspectorMcpToolMetricsSnapshot buildToolSnapshot(String toolId, ToolCounters counters) {
    long requests = counters.requests.longValue();
    double averageLatencyMs = requests == 0 ? 0.0d : (counters.totalLatencyNanos.doubleValue() / requests) / 1_000_000d;
    return new InspectorMcpToolMetricsSnapshot(toolId, requests, counters.errors.longValue(),
        counters.truncatedResponses.longValue(), averageLatencyMs);
  }

  private static final class ToolCounters {
    private final LongAdder requests = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder truncatedResponses = new LongAdder();
    private final LongAdder totalLatencyNanos = new LongAdder();
  }
}
