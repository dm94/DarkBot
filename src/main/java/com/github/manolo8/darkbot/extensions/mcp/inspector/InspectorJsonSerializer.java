package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.darkbot.api.API;

import java.util.List;
import java.util.Optional;

public class InspectorJsonSerializer implements API.Singleton {
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public String toJsonToolSchemas(List<InspectorToolSchemaSnapshot> schemas) {
        return gson.toJson(schemas);
    }

    public String toJsonRoots(List<InspectorRootSnapshot> roots) {
        return gson.toJson(roots);
    }

    public String toJsonObject(Optional<InspectorObjectSnapshot> objectSnapshot) {
        return gson.toJson(objectSnapshot.orElse(null));
    }

    public String toJsonSlot(Optional<InspectorSlotSnapshot> slotSnapshot) {
        return gson.toJson(slotSnapshot.orElse(null));
    }

    public String toJsonSlots(List<InspectorSlotSnapshot> slots) {
        return gson.toJson(slots);
    }

    public String toJsonExecutionResult(InspectorToolExecutionResult result) {
        return gson.toJson(result);
    }
}
