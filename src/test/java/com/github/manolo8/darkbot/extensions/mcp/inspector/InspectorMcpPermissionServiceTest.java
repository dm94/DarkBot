package com.github.manolo8.darkbot.extensions.mcp.inspector;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class InspectorMcpPermissionServiceTest {

    @Test
    public void testPermissionLevels() {
        InspectorMcpToolRegistry registry = new InspectorMcpToolRegistry();
        registry.registerTool(schema("safe_tool"), InspectorMcpToolPermission.READ_SAFE, arguments -> success("safe_tool"));
        registry.registerTool(schema("priv_tool"), InspectorMcpToolPermission.READ_PRIVILEGED, arguments -> success("priv_tool"));
        registry.registerTool(schema("write_tool"), InspectorMcpToolPermission.WRITE_DISABLED, arguments -> success("write_tool"));

        InspectorMcpPermissionService permissionService = new InspectorMcpPermissionService(registry, Set.of("127.0.0.1"));

        Assertions.assertTrue(permissionService.isAllowed("192.168.1.10", "safe_tool"));
        Assertions.assertFalse(permissionService.isAllowed("192.168.1.10", "priv_tool"));
        Assertions.assertTrue(permissionService.isAllowed("127.0.0.1", "priv_tool"));
        Assertions.assertFalse(permissionService.isAllowed("127.0.0.1", "write_tool"));
    }

    private InspectorToolSchemaSnapshot schema(String id) {
        return new InspectorToolSchemaSnapshot(id, id, true, List.of(), List.of());
    }

    private InspectorToolExecutionResult success(String toolId) {
        return new InspectorToolExecutionResult(toolId, true, "{}", "", false, 2);
    }
}
