package com.github.manolo8.darkbot.extensions.mcp.inspector;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.BotInstaller;
import com.github.manolo8.darkbot.core.utils.ByteUtils;
import com.github.manolo8.darkbot.utils.debug.ObjectInspector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.github.manolo8.darkbot.Main.API;

public class InspectorContractImpl implements InspectorContract {
    private static final int DEFAULT_SLOT_LIMIT = 250;
    private static final int DEFAULT_SEARCH_LIMIT = 100;
    private static final List<String> SUPPORTED_TOOLS = List.of(
            "list_roots",
            "inspect_object",
            "read_slot",
            "search_slot",
            "snapshot_root");

    @Override
    public List<String> getSupportedTools() {
        return SUPPORTED_TOOLS;
    }

    @Override
    public List<InspectorRootSnapshot> listRoots() {
        BotInstaller installer = Main.INSTANCE.pluginAPI.requireInstance(BotInstaller.class);
        List<InspectorRootSnapshot> roots = new ArrayList<>();
        addRoot(roots, "gui_manager", "GuiManager", installer.guiManagerAddress::get);
        addRoot(roots, "screen_manager", "ScreenManager", installer.screenManagerAddress::get);
        addRoot(roots, "connection_manager", "ConnectionManager", installer.connectionManagerAddress::get);
        addRoot(roots, "main_address", "Main Address", installer.mainAddress::get);
        addRoot(roots, "main_app_address", "MainApp Address", installer.mainApplicationAddress::get);
        roots.add(new InspectorRootSnapshot("hero_address", "Hero Address", Main.INSTANCE.hero.address));
        addRoot(roots, "hero_info_address", "HeroInfo Address", installer.heroInfoAddress::get);
        addRoot(roots, "settings_address", "Settings Address", installer.settingsAddress::get);
        return roots;
    }

    @Override
    public Optional<InspectorObjectSnapshot> inspectObject(long address, int slotLimit) {
        if (address <= 0)
            return Optional.empty();
        long normalized = address & ByteUtils.ATOM_MASK;
        String objectName = ByteUtils.readObjectNameDirect(normalized);
        if ("ERROR".equals(objectName))
            return Optional.empty();
        return Optional.of(new InspectorObjectSnapshot(objectName, normalized,
                readSlots(normalized, sanitizeSlotLimit(slotLimit))));
    }

    @Override
    public Optional<InspectorObjectSnapshot> snapshotRoot(String rootId, int slotLimit) {
        return listRoots().stream()
                .filter(root -> root.getId().equals(rootId))
                .findFirst()
                .flatMap(root -> inspectObject(root.getAddress(), slotLimit));
    }

    @Override
    public Optional<InspectorSlotSnapshot> readSlot(long address, String slotName) {
        String normalizedName = Optional.ofNullable(slotName).map(String::trim).orElse("");
        if (normalizedName.isEmpty())
            return Optional.empty();
        return inspectObject(address, DEFAULT_SLOT_LIMIT)
                .flatMap(snapshot -> snapshot.getSlots().stream()
                        .filter(slot -> slot.getName().equals(normalizedName))
                        .findFirst());
    }

    @Override
    public List<InspectorSlotSnapshot> searchSlots(long address, String query, int limit) {
        String normalized = Optional.ofNullable(query).orElse("").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty())
            return List.of();
        int targetLimit = sanitizeSearchLimit(limit);
        return inspectObject(address, DEFAULT_SLOT_LIMIT)
                .map(snapshot -> snapshot.getSlots().stream()
                        .filter(slot -> slot.getName().toLowerCase(Locale.ROOT).contains(normalized)
                                || slot.getType().toLowerCase(Locale.ROOT).contains(normalized))
                        .limit(targetLimit)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    private List<InspectorSlotSnapshot> readSlots(long address, int slotLimit) {
        List<ObjectInspector.Slot> slots = ObjectInspector.getObjectSlots(address);
        List<InspectorSlotSnapshot> snapshots = new ArrayList<>();
        int max = Math.min(slotLimit, slots.size());
        for (int i = 0; i < max; i++) {
            ObjectInspector.Slot slot = slots.get(i);
            long slotAddress = address + slot.offset;
            snapshots.add(new InspectorSlotSnapshot(
                    slot.name,
                    slot.getType(),
                    slot.offset,
                    slot.size,
                    readSlotValue(slotAddress, slot)));
        }
        return snapshots;
    }

    private String readSlotValue(long slotAddress, ObjectInspector.Slot slot) {
        switch (slot.slotType) {
            case DOUBLE:
                return String.valueOf(Double.longBitsToDouble(API.readLong(slotAddress)));
            case STRING:
                return readStringValue(slotAddress);
            case BOOLEAN:
                return readBooleanValue(slotAddress);
            case INT:
            case UINT:
                return String.valueOf(API.readInt(slotAddress));
            default:
                return readPointerValue(slotAddress);
        }
    }

    private String readStringValue(long slotAddress) {
        long pointer = API.readAtom(slotAddress);
        if (pointer == 0)
            return "null";
        return API.readStringDirect(pointer);
    }

    private String readBooleanValue(long slotAddress) {
        int value = API.readInt(slotAddress);
        if (value == 1)
            return "true";
        if (value == 0)
            return "false";
        return "Boolean(" + value + ")";
    }

    private String readPointerValue(long slotAddress) {
        long pointer = API.readAtom(slotAddress);
        if (pointer == 0)
            return "null";
        return String.format("0x%x", pointer);
    }

    private void addRoot(List<InspectorRootSnapshot> roots, String id, String label, Supplier<Long> supplier) {
        long address = Optional.ofNullable(supplier.get()).orElse(0L);
        roots.add(new InspectorRootSnapshot(id, label, address));
    }

    private int sanitizeSlotLimit(int slotLimit) {
        if (slotLimit <= 0)
            return DEFAULT_SLOT_LIMIT;
        return Math.min(slotLimit, 1000);
    }

    private int sanitizeSearchLimit(int limit) {
        if (limit <= 0)
            return DEFAULT_SEARCH_LIMIT;
        return Math.min(limit, 1000);
    }
}
