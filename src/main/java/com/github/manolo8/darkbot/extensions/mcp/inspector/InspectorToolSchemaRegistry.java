package com.github.manolo8.darkbot.extensions.mcp.inspector;

import eu.darkbot.api.API;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InspectorToolSchemaRegistry implements API.Singleton {
    private final List<InspectorToolSchemaSnapshot> toolSchemas;

    public InspectorToolSchemaRegistry() {
        this.toolSchemas = List.of(
                schema(
                        "list_roots",
                        "Lista raíces de memoria conocidas para inspección",
                        List.of(),
                        List.of(field("roots", "InspectorRootSnapshot[]", true, "Raíces disponibles con id, label y address"))
                ),
                schema(
                        "inspect_object",
                        "Inspecciona un objeto por dirección y devuelve slots",
                        List.of(
                                field("address", "long", true, "Dirección base del objeto"),
                                field("slotLimit", "int", false, "Límite máximo de slots")
                        ),
                        List.of(field("object", "InspectorObjectSnapshot", true, "Nombre del objeto, dirección y slots"))
                ),
                schema(
                        "snapshot_root",
                        "Obtiene snapshot de una raíz conocida",
                        List.of(
                                field("rootId", "string", true, "Identificador de la raíz"),
                                field("slotLimit", "int", false, "Límite máximo de slots")
                        ),
                        List.of(field("object", "InspectorObjectSnapshot", true, "Snapshot del objeto raíz"))
                ),
                schema(
                        "read_slot",
                        "Lee un slot exacto por nombre desde una dirección",
                        List.of(
                                field("address", "long", true, "Dirección base del objeto"),
                                field("slotName", "string", true, "Nombre exacto del slot")
                        ),
                        List.of(field("slot", "InspectorSlotSnapshot", true, "Slot con metadatos y valor"))
                ),
                schema(
                        "search_slot",
                        "Busca slots por nombre o tipo",
                        List.of(
                                field("address", "long", true, "Dirección base del objeto"),
                                field("query", "string", true, "Texto de búsqueda"),
                                field("limit", "int", false, "Límite máximo de resultados")
                        ),
                        List.of(field("slots", "InspectorSlotSnapshot[]", true, "Resultados de búsqueda"))
                ));
    }

    public List<InspectorToolSchemaSnapshot> getToolSchemas() {
        return toolSchemas;
    }

    public List<String> getToolIds() {
        return toolSchemas.stream().map(InspectorToolSchemaSnapshot::getId).collect(Collectors.toList());
    }

    private InspectorToolSchemaSnapshot schema(String id, String description,
                                               List<InspectorToolFieldSnapshot> inputFields,
                                               List<InspectorToolFieldSnapshot> outputFields) {
        return new InspectorToolSchemaSnapshot(id, description, true, new ArrayList<>(inputFields), new ArrayList<>(outputFields));
    }

    private InspectorToolFieldSnapshot field(String name, String type, boolean required, String description) {
        return new InspectorToolFieldSnapshot(name, type, required, description);
    }
}
