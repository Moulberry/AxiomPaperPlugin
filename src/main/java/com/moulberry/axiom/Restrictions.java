package com.moulberry.axiom;

import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.entity.Player;

public class Restrictions {

    public boolean canImportBlocks = true;
    public boolean canUseEditor = true;
    public boolean canEditDisplayEntities = true;
    public int maxSectionsPerSecond = 0;
    public BlockPos boundsMin = null;
    public BlockPos boundsMax = null;

    public PlotSquaredIntegration.PlotBounds lastPlotBounds = null;

    public void send(AxiomPaper plugin, Player player) {
        if (player.getListeningPluginChannels().contains("axiom:restrictions")) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBoolean(this.canImportBlocks);
            buf.writeBoolean(this.canUseEditor);
            buf.writeBoolean(this.canEditDisplayEntities);

            buf.writeVarInt(this.maxSectionsPerSecond);

            if (this.boundsMin == null || this.boundsMax == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                int minX = this.boundsMin.getX();
                int minY = this.boundsMin.getY();
                int minZ = this.boundsMin.getZ();
                int maxX = this.boundsMax.getX();
                int maxY = this.boundsMax.getY();
                int maxZ = this.boundsMax.getZ();

                if (minX < -33554431) minX = -33554431;
                if (minX > 33554431) minX = 33554431;
                if (minY < -2047) minY = -2047;
                if (minY > 2047) minY = 2047;
                if (minZ < -33554431) minZ = -33554431;
                if (minZ > 33554431) minZ = 33554431;

                if (maxX < -33554431) maxX = -33554431;
                if (maxX > 33554431) maxX = 33554431;
                if (maxY < -2047) maxY = -2047;
                if (maxY > 2047) maxY = 2047;
                if (maxZ < -33554431) maxZ = -33554431;
                if (maxZ > 33554431) maxZ = 33554431;

                buf.writeBlockPos(new BlockPos(minX, minY, minZ));
                buf.writeBlockPos(new BlockPos(maxX, maxY, maxZ));
            }

            byte[] bytes = new byte[buf.writerIndex()];
            buf.getBytes(0, bytes);
            player.sendPluginMessage(plugin, "axiom:restrictions", bytes);
        }
    }

}
