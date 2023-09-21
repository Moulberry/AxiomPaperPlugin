package com.moulberry.axiom.world_properties.server;

import com.moulberry.axiom.world_properties.WorldPropertyCategory;
import com.moulberry.axiom.world_properties.WorldPropertyWidgetType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServerWorldPropertiesRegistry {

    private final LinkedHashMap<WorldPropertyCategory, List<ServerWorldProperty<?>>> propertyList = new LinkedHashMap<>();
    private final Map<ResourceLocation, ServerWorldProperty<?>> propertyMap = new HashMap<>();

    public ServerWorldPropertiesRegistry(World world) {
        this.registerDefault(world);
    }

    public ServerWorldProperty<?> getById(ResourceLocation resourceLocation) {
        return propertyMap.get(resourceLocation);
    }

    public void addCategory(WorldPropertyCategory category, List<ServerWorldProperty<?>> properties) {
        this.propertyList.put(category, properties);

        for (ServerWorldProperty<?> property : properties) {
            ResourceLocation id = property.getId();
            if (this.propertyMap.containsKey(id)) {
                throw new RuntimeException("Duplicate property: " + id);
            }
            this.propertyMap.put(id, property);
        }
    }

    public void registerFor(Plugin plugin, Player bukkitPlayer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeVarInt(this.propertyList.size());

        for (Map.Entry<WorldPropertyCategory, List<ServerWorldProperty<?>>> entry : this.propertyList.entrySet()) {
            entry.getKey().write(buf);
            buf.writeCollection(entry.getValue(), (buffer, p) -> p.write(buffer));
        }

        bukkitPlayer.sendPluginMessage(plugin, "axiom:register_world_properties",
            buf.accessByteBufWithCorrectSize());
    }

    public void registerDefault(World world) {
        ServerLevel serverLevel = ((CraftWorld)world).getHandle();

        // Time
        WorldPropertyCategory timeCategory = new WorldPropertyCategory("axiom.editorui.window.world_properties.time", true);

        ServerWorldProperty<Integer> time = new ServerWorldProperty<>(new ResourceLocation("axiom:time"),
            "axiom.editorui.window.world_properties.time",
            true, WorldPropertyWidgetType.TIME, 0, integer -> false
        );

        this.addCategory(timeCategory, List.of(time));

        // Weather
        WorldPropertyCategory weatherCategory = new WorldPropertyCategory("axiom.editorui.window.world_properties.weather",
            true);

        ServerWorldProperty<Boolean> pauseWeather = new ServerWorldProperty<>(new ResourceLocation("axiom:pause_weather"),
            "axiom.editorui.window.world_properties.pause_weather",
            true, WorldPropertyWidgetType.CHECKBOX, !world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE), bool -> {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, !bool);
            return false;
        });

        ServerWorldProperty<Integer> weatherType = new ServerWorldProperty<>(new ResourceLocation("axiom:weather_type"),
            "axiom.editorui.window.world_properties.clear_weather",
            true, new WorldPropertyWidgetType.ButtonArray(
            List.of("axiom.editorui.window.world_properties.rain_weather", "axiom.editorui.window.world_properties.thunder_weather")
        ), 0, index -> {
            if (index == 0) {
                serverLevel.setWeatherParameters(ServerLevel.RAIN_DELAY.sample(serverLevel.random), 0, false, false);
            } else if (index == 1) {
                serverLevel.setWeatherParameters(0, ServerLevel.RAIN_DURATION.sample(serverLevel.random), true, false);
            } else if (index == 2) {
                serverLevel.setWeatherParameters(0, ServerLevel.THUNDER_DURATION.sample(serverLevel.random), true, true);
            }
            return false;
        });

        this.addCategory(weatherCategory, List.of(pauseWeather, weatherType));
    }

}
