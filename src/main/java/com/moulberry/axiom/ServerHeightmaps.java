package com.moulberry.axiom;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
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

                    heightmaps.add(new NamedImage(imagePath.getFileName().toString(), Files.readAllBytes(imagePath)));
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

            byte[] packetBytes = new byte[buf.writerIndex()];
            buf.getBytes(0, packetBytes);
            player.sendPluginMessage(AxiomPaper.PLUGIN, "axiom:add_server_heightmap", packetBytes);
            buf.clear();
        }
    }

}
