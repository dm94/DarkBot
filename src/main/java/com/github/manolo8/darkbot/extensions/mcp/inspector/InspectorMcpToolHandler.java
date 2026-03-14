package com.github.manolo8.darkbot.extensions.mcp.inspector;

import java.util.Map;

@FunctionalInterface
public interface InspectorMcpToolHandler {
  InspectorToolExecutionResult execute(Map<String, Object> arguments);
}
