package com.github.manolo8.darkbot.extensions.mcp.inspector;

import java.util.Collections;
import java.util.List;

public class InspectorToolSchemaSnapshot {
    private final String id;
    private final String description;
    private final boolean readOnly;
    private final List<InspectorToolFieldSnapshot> inputFields;
    private final List<InspectorToolFieldSnapshot> outputFields;

    public InspectorToolSchemaSnapshot(String id, String description, boolean readOnly,
                                       List<InspectorToolFieldSnapshot> inputFields,
                                       List<InspectorToolFieldSnapshot> outputFields) {
        this.id = id;
        this.description = description;
        this.readOnly = readOnly;
        this.inputFields = Collections.unmodifiableList(inputFields);
        this.outputFields = Collections.unmodifiableList(outputFields);
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public List<InspectorToolFieldSnapshot> getInputFields() {
        return inputFields;
    }

    public List<InspectorToolFieldSnapshot> getOutputFields() {
        return outputFields;
    }
}
