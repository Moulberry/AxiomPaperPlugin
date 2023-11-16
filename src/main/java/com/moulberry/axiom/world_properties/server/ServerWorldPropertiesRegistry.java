package com.moulberry.axiom.world_properties.server;

import com.moulberry.axiom.world_properties.WorldPropertyCategory;
import com.moulberry.axiom.world_properties.WorldPropertyWidgetType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R2.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class ServerWorldPropertiesRegistry {

    private final LinkedHashMap<WorldPropertyCategory, List<ServerWorldPropertyHolder<?>>> propertyList = new LinkedHashMap<>();
    private final Map<ResourceLocation, ServerWorldPropertyHolder<?>> propertyMap = new HashMap<>();
    private final World world;

    public ServerWorldPropertiesRegistry(World world) {
        this.world = world;
        this.registerDefault();
    }

    public ServerWorldPropertyHolder<?> getById(ResourceLocation resourceLocation) {
        return propertyMap.get(resourceLocation);
    }

    @SuppressWarnings("unchecked")
    public void addCategory(WorldPropertyCategory category, List<ServerWorldPropertyBase<?>> properties) {
        List<ServerWorldPropertyHolder<?>> holders = new ArrayList<>();
        for (ServerWorldPropertyBase<?> property : properties) {
            Object defaultValue = property.getDefaultValue(this.world);
            holders.add(new ServerWorldPropertyHolder<>(defaultValue, (ServerWorldPropertyBase<Object>) property));
        }

        this.propertyList.put(category, holders);

        for (ServerWorldPropertyHolder<?> holder : holders) {
            ResourceLocation id = holder.getId();
            if (this.propertyMap.containsKey(id)) {
                throw new RuntimeException("Duplicate property: " + id);
            }
            this.propertyMap.put(id, holder);
        }
    }

    public void registerFor(Plugin plugin, Player bukkitPlayer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeVarInt(this.propertyList.size());

        for (Map.Entry<WorldPropertyCategory, List<ServerWorldPropertyHolder<?>>> entry : this.propertyList.entrySet()) {
            entry.getKey().write(buf);
            buf.writeCollection(entry.getValue(), (buffer, p) -> p.write(buffer));
        }

        byte[] bytes = new byte[buf.writerIndex()];
        buf.getBytes(0, bytes);
        bukkitPlayer.sendPluginMessage(plugin, "axiom:register_world_properties", bytes);
    }

    private static final ServerWorldProperty<Integer> TIME = new ServerWorldProperty<>(
        new NamespacedKey("axiom", "time"),
        "axiom.editorui.window.world_properties.time",
        true, WorldPropertyWidgetType.TIME, world -> 0,
        (player, w, integer) -> PropertyUpdateResult.UPDATE_WITHOUT_SYNC
    );

    public static final ServerWorldProperty<Boolean> PAUSE_WEATHER = new ServerWorldProperty<>(
        new NamespacedKey("axiom", "pause_weather"),
        "axiom.editorui.window.world_properties.pause_weather",
        true, WorldPropertyWidgetType.CHECKBOX, world -> !world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE),
        (player, world, bool) -> {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, !bool);
            return PropertyUpdateResult.UPDATE_WITHOUT_SYNC;
        }
    );

    private static final ServerWorldProperty<Integer> WEATHER_TYPE = new ServerWorldProperty<>(
            new NamespacedKey("axiom", "weather_type"),
            "axiom.editorui.window.world_properties.clear_weather",
            true, new WorldPropertyWidgetType.ButtonArray(
            List.of("axiom.editorui.window.world_properties.rain_weather", "axiom.editorui.window.world_properties.thunder_weather")
    ), world -> 0, (player, world, index) -> {
        ServerLevel serverLevel = ((CraftWorld)world).getHandle();
        if (index == 0) {
            serverLevel.setWeatherParameters(ServerLevel.RAIN_DELAY.sample(serverLevel.random), 0, false, false);
        } else if (index == 1) {
            serverLevel.setWeatherParameters(0, ServerLevel.RAIN_DURATION.sample(serverLevel.random), true, false);
        } else if (index == 2) {
            serverLevel.setWeatherParameters(0, ServerLevel.THUNDER_DURATION.sample(serverLevel.random), true, true);
        }
        return PropertyUpdateResult.UPDATE_WITHOUT_SYNC;
    });

    public void registerDefault() {
        // Time
        WorldPropertyCategory timeCategory = new WorldPropertyCategory("axiom.editorui.window.world_properties.time", true);

        this.addCategory(timeCategory, List.of(TIME));

        // Weather
        WorldPropertyCategory weatherCategory = new WorldPropertyCategory("axiom.editorui.window.world_properties.weather",
            true);

        this.addCategory(weatherCategory, List.of(PAUSE_WEATHER, WEATHER_TYPE));
    }

}
