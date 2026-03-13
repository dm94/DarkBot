package com.github.manolo8.darkbot.extensions.mcp.inspector;

import java.util.Collections;
import java.util.List;

public class InspectorObjectSnapshot {
    private final String objectName;
    private final long address;
    private final List<InspectorSlotSnapshot> slots;

    public InspectorObjectSnapshot(String objectName, long address, List<InspectorSlotSnapshot> slots) {
        this.objectName = objectName;
        this.address = address;
        this.slots = Collections.unmodifiableList(slots);
    }

    public String getObjectName() {
        return objectName;
    }

    public long getAddress() {
        return address;
    }

    public List<InspectorSlotSnapshot> getSlots() {
        return slots;
    }
}
