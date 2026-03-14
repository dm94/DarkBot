package com.github.manolo8.darkbot.extensions.mcp.inspector;

public class InspectorMcpToolMetricsSnapshot {
  private final String toolId;
  private final long requests;
  private final long errors;
  private final long truncatedResponses;
  private final double averageLatencyMs;

  public InspectorMcpToolMetricsSnapshot(String toolId, long requests, long errors, long truncatedResponses,
      double averageLatencyMs) {
    this.toolId = toolId;
    this.requests = requests;
    this.errors = errors;
    this.truncatedResponses = truncatedResponses;
    this.averageLatencyMs = averageLatencyMs;
  }

  public String getToolId() {
    return toolId;
  }

  public long getRequests() {
    return requests;
  }

  public long getErrors() {
    return errors;
  }

  public long getTruncatedResponses() {
    return truncatedResponses;
  }

  public double getAverageLatencyMs() {
    return averageLatencyMs;
  }
}
