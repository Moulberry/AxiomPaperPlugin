package com.moulberry.axiom.integration.prism;

public enum PrismLoggingType {
    BLOCK_CHANGES("block-changes", true),
    ENTITY_SPAWNS("entity-spawns", true),
    ENTITY_DELETES("entity-deletes", true),
    ENTITY_MODIFICATIONS("entity-modifications", true),
    PLAYER_TELEPORTS("player-teleports", false),
    PLAYER_GAMEMODE_CHANGES("player-gamemode-changes", false),
    PLAYER_FLY_SPEED_CHANGES("player-fly-speed-changes", false),
    PLAYER_NO_PHYSICAL_TRIGGER_CHANGES("player-no-physical-trigger-changes", false),
    WORLD_TIME_CHANGES("world-time-changes", false),
    WORLD_PROPERTY_CHANGES("world-property-changes", false),
    ANNOTATION_CHANGES("annotation-changes", false),
    BIOME_CHANGES("biome-changes", true);

    private final String configKey;
    private final boolean enabledByDefault;

    PrismLoggingType(String configKey, boolean enabledByDefault) {
        this.configKey = configKey;
        this.enabledByDefault = enabledByDefault;
    }

    public String configKey() {
        return this.configKey;
    }

    public boolean enabledByDefault() {
        return this.enabledByDefault;
    }
}
