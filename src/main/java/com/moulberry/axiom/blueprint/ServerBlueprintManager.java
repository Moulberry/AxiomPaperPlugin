package com.moulberry.axiom.blueprint;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.packet.CustomByteArrayPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ServerBlueprintManager {

    private static ServerBlueprintRegistry registry = null;

    public static void initialize(Path blueprintDirectory) {
        Map<String, RawBlueprint> map = new HashMap<>();
        loadRegistryFromFolder(map, blueprintDirectory, "/");
        registry = new ServerBlueprintRegistry(map);
    }

    private static final int MAX_SIZE = 1000000;
    private static final ResourceLocation PACKET_BLUEPRINT_MANIFEST_IDENTIFIER = new ResourceLocation("axiom:blueprint_manifest");

    public static void sendManifest(List<ServerPlayer> serverPlayers) {
        if (registry != null) {
            List<ServerPlayer> sendTo = new ArrayList<>();

            for (ServerPlayer serverPlayer : serverPlayers) {
                CraftPlayer craftPlayer = serverPlayer.getBukkitEntity();
                if (AxiomPaper.PLUGIN.canUseAxiom(craftPlayer) &&
                        craftPlayer.getListeningPluginChannels().contains("axiom:blueprint_manifest")) {
                    sendTo.add(serverPlayer);
                }
            }

            if (sendTo.isEmpty()) return;

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBoolean(true); // replace

            for (Map.Entry<String, RawBlueprint> entry : registry.blueprints().entrySet()) {
                buf.writeUtf(entry.getKey());
                RawBlueprint.writeHeader(buf, entry.getValue());

                if (buf.writerIndex() > MAX_SIZE) {
                    // Finish and send current packet
                    buf.writeUtf("");
                    byte[] bytes = new byte[buf.writerIndex()];
                    buf.getBytes(0, bytes);
                    var payload = new CustomByteArrayPayload(PACKET_BLUEPRINT_MANIFEST_IDENTIFIER, bytes);
                    for (ServerPlayer serverPlayer : sendTo) {
                        serverPlayer.connection.send(new ClientboundCustomPayloadPacket(payload));
                    }

                    // Continue
                    buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeBoolean(false); // don't replace
                }
            }

            buf.writeUtf("");
            byte[] bytes = new byte[buf.writerIndex()];
            buf.getBytes(0, bytes);
            var payload = new CustomByteArrayPayload(PACKET_BLUEPRINT_MANIFEST_IDENTIFIER, bytes);
            for (ServerPlayer serverPlayer : sendTo) {
                serverPlayer.connection.send(new ClientboundCustomPayloadPacket(payload));
            }
        }
    }

    public static ServerBlueprintRegistry getRegistry() {
        return registry;
    }

    private static void loadRegistryFromFolder(Map<String, RawBlueprint> map, Path folder, String location) {
        if (!Files.isDirectory(folder)) {
            return;
        }

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(folder)) {
            for (Path path : directoryStream) {
                String filename = path.getFileName().toString();
                if (filename.endsWith(".bp")) {
                    try {
                        RawBlueprint rawBlueprint = BlueprintIo.readRawBlueprint(new BufferedInputStream(Files.newInputStream(path)));
                        String newLocation = location + filename.substring(0, filename.length()-3);
                        map.put(newLocation, rawBlueprint);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (Files.isDirectory(path)) {
                    String newLocation = location + filename + "/";
                    loadRegistryFromFolder(map, path, newLocation);
                }
            }
        } catch (IOException ignored) {}
    }

}
