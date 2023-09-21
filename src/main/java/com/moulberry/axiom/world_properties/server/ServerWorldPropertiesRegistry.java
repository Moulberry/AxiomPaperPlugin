package com.moulberry.axiom.world_properties.server;

import com.moulberry.axiom.world_properties.WorldPropertyCategory;
import com.moulberry.axiom.world_properties.WorldPropertyWidgetType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServerWorldPropertiesRegistry {

    private final LinkedHashMap<WorldPropertyCategory, List<ServerWorldProperty<?>>> propertyList = new LinkedHashMap<>();
    private final Map<ResourceLocation, ServerWorldProperty<?>> propertyMap = new HashMap<>();

    public ServerWorldPropertiesRegistry() {
        this.registerDefault();
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

    public void registerDefault() {
        // Time
        WorldPropertyCategory timeCategory = new WorldPropertyCategory("axiom.editorui.window.world_properties.time", true);

        ServerWorldProperty<Integer> time = new ServerWorldProperty<>(new ResourceLocation("axiom:time"),
            "axiom.editorui.window.world_properties.time",
            true, WorldPropertyWidgetType.TIME, 0, integer -> false
        );

        this.addCategory(timeCategory, List.of(time));
    }

}
