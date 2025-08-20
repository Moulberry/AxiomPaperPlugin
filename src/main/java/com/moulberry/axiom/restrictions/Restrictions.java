package com.moulberry.axiom.restrictions;

import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.integration.plotsquared.PlotSquaredIntegration;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Restrictions {

    private static final Map<String, AxiomPermission> PERMISSION_BY_NAME = new LinkedHashMap<>();
    static {
        for (AxiomPermission value : AxiomPermission.values()) {
            PERMISSION_BY_NAME.put(value.getInternalName(), value);
        }
    }

    public EnumSet<AxiomPermission> allowedPermissions = EnumSet.of(AxiomPermission.DEFAULT);
    public EnumSet<AxiomPermission> deniedPermissions = EnumSet.noneOf(AxiomPermission.class);
    public int infiniteReachLimit = -1;
    public Set<PlotSquaredIntegration.PlotBox> bounds = Set.of();

    public Restrictions() {
    }

    public void send(Player player) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());

        friendlyByteBuf.writeVarInt(this.allowedPermissions.size());
        for (AxiomPermission allowedPermission : this.allowedPermissions) {
            friendlyByteBuf.writeUtf(allowedPermission.getInternalName());
        }

        friendlyByteBuf.writeVarInt(this.deniedPermissions.size());
        for (AxiomPermission disallowedPermission : this.deniedPermissions) {
            friendlyByteBuf.writeUtf(disallowedPermission.getInternalName());
        }

        friendlyByteBuf.writeInt(this.infiniteReachLimit);

        friendlyByteBuf.writeVarInt(this.bounds.size());
        for (PlotSquaredIntegration.PlotBox bound : this.bounds) {
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

            friendlyByteBuf.writeBlockPos(new BlockPos(minX, minY, minZ));
            friendlyByteBuf.writeBlockPos(new BlockPos(maxX, maxY, maxZ));
        }

        byte[] bytes = ByteBufUtil.getBytes(friendlyByteBuf);
        VersionHelper.sendCustomPayload(player, "axiom:restrictions", bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || this.getClass() != o.getClass()) return false;

        Restrictions that = (Restrictions) o;
        return this.infiniteReachLimit == that.infiniteReachLimit &&
            this.allowedPermissions.equals(that.allowedPermissions) &&
            this.deniedPermissions.equals(that.deniedPermissions) &&
            this.bounds.equals(that.bounds);
    }

    @Override
    public int hashCode() {
        int result = this.allowedPermissions.hashCode();
        result = 31 * result + this.deniedPermissions.hashCode();
        result = 31 * result + this.infiniteReachLimit;
        result = 31 * result + this.bounds.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Restrictions{" +
            "allowedPermissions=" + allowedPermissions +
            ", deniedPermissions=" + deniedPermissions +
            ", infiniteReachLimit=" + infiniteReachLimit +
            ", bounds=" + bounds +
            '}';
    }
}
