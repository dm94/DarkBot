package com.github.manolo8.darkbot.extensions.mcp.inspector;

import eu.darkbot.api.API;

import java.util.List;
import java.util.Optional;

public interface InspectorContract extends API.Singleton {

  List<String> getSupportedTools();

  List<InspectorRootSnapshot> listRoots();

  Optional<InspectorObjectSnapshot> inspectObject(long address, int slotLimit);

  Optional<InspectorObjectSnapshot> snapshotRoot(String rootId, int slotLimit);

  Optional<InspectorSlotSnapshot> readSlot(long address, String slotName);

  List<InspectorSlotSnapshot> searchSlots(long address, String query, int limit);
}
