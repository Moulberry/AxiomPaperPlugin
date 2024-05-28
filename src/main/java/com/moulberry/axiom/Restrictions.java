package com.moulberry.axiom;

import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.entity.Player;

import java.util.Set;

public class Restrictions {

    public boolean canImportBlocks = true;
    public boolean canUseEditor = true;
    public boolean canEditDisplayEntities = true;
    public int maxSectionsPerSecond = 0;
    public Set<PlotSquaredIntegration.PlotBox> bounds = Set.of();

    public PlotSquaredIntegration.PlotBounds lastPlotBounds = null;

    public void send(AxiomPaper plugin, Player player) {
        if (player.getListeningPluginChannels().contains("axiom:restrictions")) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeBoolean(this.canImportBlocks);
            buf.writeBoolean(this.canUseEditor);
            buf.writeBoolean(this.canEditDisplayEntities);

            buf.writeVarInt(this.maxSectionsPerSecond);

            int count = Math.min(64, bounds.size());
            buf.writeVarInt(count);
            for (PlotSquaredIntegration.PlotBox bound : this.bounds) {
                if (count > 0) {
                    count -= 1;
                } else {
                    break;
                }

                int minX = bound.min().getX();
                int minY = bound.min().getY();
                int minZ = bound.min().getZ();
                int maxX = bound.max().getX();
                int maxY = bound.max().getY();
                int maxZ = bound.max().getZ();

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

    @Override
    public String toString() {
        return "Restrictions{" +
            "canImportBlocks=" + canImportBlocks +
            ", canUseEditor=" + canUseEditor +
            ", canEditDisplayEntities=" + canEditDisplayEntities +
            ", maxSectionsPerSecond=" + maxSectionsPerSecond +
            ", bounds=" + bounds +
            ", lastPlotBounds=" + lastPlotBounds +
            '}';
    }
}
