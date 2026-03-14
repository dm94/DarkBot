package com.github.manolo8.darkbot.extensions.mcp.inspector;

import java.util.Collections;
import java.util.List;

public class InspectorMcpMetricsSnapshot {
  private final long totalRequests;
  private final long totalErrors;
  private final long totalRateLimited;
  private final List<InspectorMcpToolMetricsSnapshot> tools;

  public InspectorMcpMetricsSnapshot(long totalRequests, long totalErrors, long totalRateLimited,
      List<InspectorMcpToolMetricsSnapshot> tools) {
    this.totalRequests = totalRequests;
    this.totalErrors = totalErrors;
    this.totalRateLimited = totalRateLimited;
    this.tools = Collections.unmodifiableList(tools);
  }

  public long getTotalRequests() {
    return totalRequests;
  }

  public long getTotalErrors() {
    return totalErrors;
  }

  public long getTotalRateLimited() {
    return totalRateLimited;
  }

  public List<InspectorMcpToolMetricsSnapshot> getTools() {
    return tools;
  }
}
