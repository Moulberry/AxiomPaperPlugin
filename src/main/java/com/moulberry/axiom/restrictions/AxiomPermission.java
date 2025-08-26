package com.moulberry.axiom.restrictions;

public enum AxiomPermission {

    ALL(null, "axiom.all"),

    // The following permissions are non-default. They are not recommended for public servers
    ENTITY(null, "axiom.entity.*"),
    ENTITY_SPAWN(ENTITY, "axiom.entity.spawn"),
    ENTITY_MANIPULATE(ENTITY, "axiom.entity.manipulate"),
    ENTITY_DELETE(ENTITY, "axiom.entity.delete"),
    ENTITY_REQUESTDATA(ENTITY, "axiom.entity.request_data"),

    BLUEPRINT(null, "axiom.blueprint.*"),
    BLUEPRINT_UPLOAD(BLUEPRINT, "axiom.blueprint.upload"),
    BLUEPRINT_REQUEST(BLUEPRINT, "axiom.blueprint.request"),
    BLUEPRINT_MANIFEST(BLUEPRINT, "axiom.blueprint.manifest"),

    ANNOTATION(null, "axiom.annotation.*"),
    ANNOTATION_CREATE(ANNOTATION, "axiom.annotation.create"),
    ANNOTATION_CLEARALL(ANNOTATION, "axiom.annotation.clear_all"),

    // The following permissions are the default permissions
    DEFAULT(null, "axiom.default"),

    USE(DEFAULT, "axiom.use"),

    ALLOW_COPYING_OTHER_PLOTS(DEFAULT, "axiom.allow_copying_other_plots"),
    CAN_IMPORT_BLOCKS(DEFAULT, "axiom.can_import_blocks"),
    CAN_EXPORT_BLOCKS(DEFAULT, "axiom.can_export_blocks"),

    CHUNK(DEFAULT, "axiom.chunk.*"),
    CHUNK_REQUEST(CHUNK, "axiom.chunk.request"),
    CHUNK_REQUESTBLOCKENTITY(CHUNK, "axiom.chunk.request_block_entity"),

    BUILD(DEFAULT, "axiom.build.*"),
    BUILD_PLACE(BUILD, "axiom.build.place"),
    BUILD_SECTION(BUILD, "axiom.build.section"),
    BUILD_NBT(BUILD, "axiom.build.nbt"),

    EDITOR(DEFAULT, "axiom.editor.*"),
    EDITOR_USE(EDITOR, "axiom.editor.use"),
    EDITOR_VIEWS(EDITOR, "axiom.editor.views"),

    PLAYER(DEFAULT, "axiom.player.*"),
    PLAYER_BYPASS_MOVEMENT_RESTRICTIONS(PLAYER, "axiom.player.bypass_movement_restrictions"),
    PLAYER_SPEED(PLAYER, "axiom.player.speed"),
    PLAYER_TELEPORT(PLAYER, "axiom.player.teleport"),
    PLAYER_GAMEMODE(PLAYER, "axiom.player.gamemode.*"),
    PLAYER_GAMEMODE_SURVIVAL(PLAYER_GAMEMODE, "axiom.player.gamemode.survival"),
    PLAYER_GAMEMODE_CREATIVE(PLAYER_GAMEMODE, "axiom.player.gamemode.creative"),
    PLAYER_GAMEMODE_SPECTATOR(PLAYER_GAMEMODE, "axiom.player.gamemode.spectator"),
    PLAYER_GAMEMODE_ADVENTURE(PLAYER_GAMEMODE, "axiom.player.gamemode.adventure"),
    PLAYER_HOTBAR(PLAYER, "axiom.player.hotbar"),
    PLAYER_SETNOPHYSICALTRIGGER(PLAYER, "axiom.player.set_no_physical_trigger"),

    WORLD(DEFAULT, "axiom.world.*"),
    WORLD_TIME(WORLD, "axiom.world.time"),
    WORLD_PROPERTY(WORLD, "axiom.world.property"),

    CAPABILITY(DEFAULT, "axiom.capability.*"),
    BULLDOZER(CAPABILITY, "axiom.capability.bulldozer"),
    REPLACE_MODE(CAPABILITY, "axiom.capability.replace_mode"),
    FORCE_PLACE(CAPABILITY, "axiom.capability.force_place"),
    NO_UPDATES(CAPABILITY, "axiom.capability.no_updates"),
    TINKER(CAPABILITY, "axiom.capability.tinker"),
    INFINITE_REACH(CAPABILITY, "axiom.capability.infinite_reach"),
    FAST_PLACE(CAPABILITY, "axiom.capability.fast_place"),
    ANGEL_PLACEMENT(CAPABILITY, "axiom.capability.angel_placement"),
    NO_CLIP(CAPABILITY, "axiom.capability.no_clip"),
    PHANTOM(CAPABILITY, "axiom.capability.phantom"),

    TOOL(DEFAULT, "axiom.tool.*"),
    TOOL_MAGICSELECT(TOOL, "axiom.tool.magic_select"),
    TOOL_BOXSELECT(TOOL, "axiom.tool.box_select"),
    TOOL_FREEHANDSELECT(TOOL, "axiom.tool.freehand_select"),
    TOOL_LASSOSELECT(TOOL, "axiom.tool.lasso_select"),
    TOOL_RULER(TOOL, "axiom.tool.ruler"),
    TOOL_ANNOTATION(TOOL, "axiom.tool.annotation"),
    TOOL_PAINTER(TOOL, "axiom.tool.painter"),
    TOOL_NOISEPAINTER(TOOL, "axiom.tool.noise_painter"),
    TOOL_BIOMEPAINTER(TOOL, "axiom.tool.biome_painter"),
    TOOL_GRADIENTPAINTER(TOOL, "axiom.tool.gradient_painter"),
    TOOL_SCRIPTBRUSH(TOOL, "axiom.tool.script_brush"),
    TOOL_FREEHANDDRAW(TOOL, "axiom.tool.freehand_draw"),
    TOOL_SCULPTDRAW(TOOL, "axiom.tool.sculpt_draw"),
    TOOL_ROCK(TOOL, "axiom.tool.rock"),
    TOOL_WELD(TOOL, "axiom.tool.weld"),
    TOOL_MELT(TOOL, "axiom.tool.melt"),
    TOOL_STAMP(TOOL, "axiom.tool.stamp"),
    TOOL_TEXT(TOOL, "axiom.tool.text"),
    TOOL_SHAPE(TOOL, "axiom.tool.shape"),
    TOOL_PATH(TOOL, "axiom.tool.path"),
    TOOL_MODELLING(TOOL, "axiom.tool.modelling"),
    TOOL_FLOODFILL(TOOL, "axiom.tool.floodfill"),
    TOOL_FLUIDBALL(TOOL, "axiom.tool.fluidball"),
    TOOL_ELEVATION(TOOL, "axiom.tool.elevation"),
    TOOL_SLOPE(TOOL, "axiom.tool.slope"),
    TOOL_SMOOTH(TOOL, "axiom.tool.smooth"),
    TOOL_DISTORT(TOOL, "axiom.tool.distort"),
    TOOL_ROUGHEN(TOOL, "axiom.tool.roughen"),
    TOOL_SHATTER(TOOL, "axiom.tool.shatter"),
    TOOL_EXTRUDE(TOOL, "axiom.tool.extrude"),
    TOOL_MODIFY(TOOL, "axiom.tool.modify"),

    BUILDERTOOL(DEFAULT, "axiom.builder_tool.*"),
    BUILDERTOOL_MOVE(BUILDERTOOL, "axiom.builder_tool.move"),
    BUILDERTOOL_CLONE(BUILDERTOOL, "axiom.builder_tool.clone"),
    BUILDERTOOL_STACK(BUILDERTOOL, "axiom.builder_tool.stack"),
    BUILDERTOOL_SMEAR(BUILDERTOOL, "axiom.builder_tool.smear"),
    BUILDERTOOL_EXTRUDE(BUILDERTOOL, "axiom.builder_tool.extrude"),
    BUILDERTOOL_ERASE(BUILDERTOOL, "axiom.builder_tool.erase"),
    BUILDERTOOL_SETUPSYMMETRY(BUILDERTOOL, "axiom.builder_tool.setup_symmetry");

    public final AxiomPermission parent;
    private final String name;

    AxiomPermission(AxiomPermission parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    public String getPermissionNode() {
        return this.name;
    }

    public String getInternalName() {
        if (this == DEFAULT) {
            return "axiom.*";
        } else {
            return this.name;
        }
    }

}
