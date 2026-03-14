package com.github.manolo8.darkbot.extensions.mcp.inspector;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class InspectorMcpToolRegistryTest {

    @Test
    public void testRegistryRegistersAndExecutesTool() {
        InspectorMcpToolRegistry registry = new InspectorMcpToolRegistry();
        InspectorToolSchemaSnapshot schema = new InspectorToolSchemaSnapshot(
                "test_tool",
                "Test tool",
                true,
                List.of(),
                List.of()
        );
        registry.registerTool(schema, InspectorMcpToolPermission.READ_SAFE,
                arguments -> new InspectorToolExecutionResult("test_tool", true, "{\"ok\":true}", "", false, 11));

        InspectorToolExecutionResult result = registry.execute("test_tool", Map.of("x", 1));
        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals("test_tool", result.getToolId());
    }

    @Test
    public void testRegistryRejectsDuplicateToolId() {
        InspectorMcpToolRegistry registry = new InspectorMcpToolRegistry();
        InspectorToolSchemaSnapshot schema = new InspectorToolSchemaSnapshot("dup_tool", "dup", true, List.of(), List.of());

        registry.registerTool(schema, InspectorMcpToolPermission.READ_SAFE,
                arguments -> new InspectorToolExecutionResult("dup_tool", true, "{}", "", false, 2));

        Assertions.assertThrows(IllegalStateException.class, () ->
                registry.registerTool(schema, InspectorMcpToolPermission.READ_SAFE,
                        arguments -> new InspectorToolExecutionResult("dup_tool", true, "{}", "", false, 2)));
    }
}
