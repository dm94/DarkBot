package com.github.manolo8.darkbot.extensions.mcp.inspector;

public class InspectorRootSnapshot {
    private final String id;
    private final String label;
    private final long address;

    public InspectorRootSnapshot(String id, String label, long address) {
        this.id = id;
        this.label = label;
        this.address = address;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public long getAddress() {
        return address;
    }
}
