package com.github.manolo8.darkbot.extensions.mcp.inspector;

public class InspectorToolFieldSnapshot {
    private final String name;
    private final String type;
    private final boolean required;
    private final String description;

    public InspectorToolFieldSnapshot(String name, String type, boolean required, String description) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public String getDescription() {
        return description;
    }
}
