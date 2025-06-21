package com.moulberry.axiom;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ServerHeightmaps {

    private static List<NamedImage> heightmaps = new ArrayList<>();
    private record NamedImage(String name, byte[] imageBytes) {}

    public static void load(Path path) {
        heightmaps.clear();

        if (!Files.isDirectory(path)) {
            return;
        }

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path)) {
            for (Path imagePath : directoryStream) {
                try {
                    // Test if image can be read
                    BufferedImage image = ImageIO.read(Files.newInputStream(imagePath));
                    if (image == null) {
                        continue;
                    }

                    byte[] allBytes = Files.readAllBytes(imagePath);
                    if (allBytes.length > 2000000) {
                        Path relative = path.relativize(imagePath);
                        AxiomPaper.PLUGIN.getLogger().info("Heightmap " + relative + " skipped because it's over 2mb");
                        continue;
                    }

                    heightmaps.add(new NamedImage(imagePath.getFileName().toString(), allBytes));
                } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void sendTo(Player player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        for (NamedImage heightmap : heightmaps) {
            buf.writeUtf(heightmap.name);
            buf.writeByteArray(heightmap.imageBytes);

            byte[] packetBytes = ByteBufUtil.getBytes(buf);
            VersionHelper.sendCustomPayload(((CraftPlayer)player).getHandle(), "axiom:add_server_heightmap", packetBytes);
            buf.clear();
        }
    }

}
