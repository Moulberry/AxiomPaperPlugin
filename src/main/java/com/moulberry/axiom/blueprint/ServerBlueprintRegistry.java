package com.moulberry.axiom.blueprint;

import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;

public record ServerBlueprintRegistry(Map<String, RawBlueprint> blueprints) {

    public void writeManifest(FriendlyByteBuf friendlyByteBuf) {
        for (Map.Entry<String, RawBlueprint> entry : this.blueprints.entrySet()) {
            friendlyByteBuf.writeUtf(entry.getKey());
            RawBlueprint.writeHeader(friendlyByteBuf, entry.getValue());
        }
        friendlyByteBuf.writeUtf("");
    }

    public static ServerBlueprintRegistry readManifest(FriendlyByteBuf friendlyByteBuf) {
        Map<String, RawBlueprint> blueprints = new HashMap<>();

        while (true) {
            String path = friendlyByteBuf.readUtf();
            if (path.isEmpty()) {
                return new ServerBlueprintRegistry(blueprints);
            }

            blueprints.put(path, RawBlueprint.readHeader(friendlyByteBuf));
        }

    }

}
