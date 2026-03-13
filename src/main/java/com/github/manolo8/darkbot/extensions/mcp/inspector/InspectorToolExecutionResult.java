package com.github.manolo8.darkbot.extensions.mcp.inspector;

public class InspectorToolExecutionResult {
  private final String toolId;
  private final boolean success;
  private final String payload;
  private final String error;

  public InspectorToolExecutionResult(String toolId, boolean success, String payload, String error) {
    this.toolId = toolId;
    this.success = success;
    this.payload = payload;
    this.error = error;
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
}
