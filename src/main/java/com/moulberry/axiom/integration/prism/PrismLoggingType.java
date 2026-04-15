package com.moulberry.axiom.integration.prism;

public enum PrismLoggingType {
    BLOCK_CHANGES("block-changes"),
    ENTITY_SPAWNS("entity-spawns"),
    ENTITY_DELETES("entity-deletes"),
    ENTITY_MODIFICATIONS("entity-modifications"),
    PLAYER_TELEPORTS("player-teleports"),
    PLAYER_GAMEMODE_CHANGES("player-gamemode-changes"),
    PLAYER_FLY_SPEED_CHANGES("player-fly-speed-changes"),
    PLAYER_NO_PHYSICAL_TRIGGER_CHANGES("player-no-physical-trigger-changes"),
    WORLD_TIME_CHANGES("world-time-changes"),
    WORLD_PROPERTY_CHANGES("world-property-changes"),
    ANNOTATION_SNAPSHOTS("annotation-snapshots");

    private final String configKey;

    PrismLoggingType(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return this.configKey;
    }
}
