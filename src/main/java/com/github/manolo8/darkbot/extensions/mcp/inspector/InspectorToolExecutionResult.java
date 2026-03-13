package com.github.manolo8.darkbot.extensions.mcp.inspector;

public class InspectorToolExecutionResult {
  private final String toolId;
  private final boolean success;
  private final String payload;
  private final String error;
  private final boolean truncated;
  private final int payloadLength;

  public InspectorToolExecutionResult(String toolId, boolean success, String payload, String error, boolean truncated,
      int payloadLength) {
    this.toolId = toolId;
    this.success = success;
    this.payload = payload;
    this.error = error;
    this.truncated = truncated;
    this.payloadLength = payloadLength;
  }

  public String getToolId() {
    return toolId;
  }

  public boolean isSuccess() {
    return success;
  }

  public String getPayload() {
    return payload;
  }

  public String getError() {
    return error;
  }

  public boolean isTruncated() {
    return truncated;
  }

  public int getPayloadLength() {
    return payloadLength;
  }
}
