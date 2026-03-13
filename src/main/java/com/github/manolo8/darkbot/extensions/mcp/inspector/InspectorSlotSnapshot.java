package com.github.manolo8.darkbot.extensions.mcp.inspector;

public class InspectorSlotSnapshot {
    private final String name;
    private final String type;
    private final long offset;
    private final long size;
    private final String value;

    public InspectorSlotSnapshot(String name, String type, long offset, long size, String value) {
        this.name = name;
        this.type = type;
        this.offset = offset;
        this.size = size;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public long getOffset() {
        return offset;
    }

    public long getSize() {
        return size;
    }

    public String getValue() {
        return value;
    }
}
