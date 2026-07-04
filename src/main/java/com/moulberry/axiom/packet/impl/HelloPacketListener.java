package com.moulberry.axiom.packet.impl;

import com.moulberry.axiom.*;
import com.moulberry.axiom.blueprint.DFUHelper;
import com.moulberry.axiom.packet.PacketHandler;
import com.moulberry.axiom.restrictions.AxiomPermission;
import com.moulberry.axiom.viaversion.ViaVersionHelper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.SharedConstants;
import net.minecraft.core.IdMapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class HelloPacketListener implements PacketHandler {

    private final AxiomPaper plugin;

    public HelloPacketListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onReceive(Player player, FriendlyByteBuf friendlyByteBuf) {
        int apiVersion = friendlyByteBuf.readVarInt();
        if (apiVersion != AxiomConstants.API_VERSION) {
            String versions = " (C="+apiVersion+" S="+AxiomConstants.API_VERSION+")";

            String message;
            if (apiVersion < AxiomConstants.API_VERSION) {
                message = "Unable to use Axiom, you're on an outdated version! Please update to the latest version of Axiom to use it on this server." + versions;
            } else {
                message = "Unable to use Axiom, server hasn't updated Axiom yet." + versions;
            }

            this.plugin.sendGoodbyeReason(player, message);

            String unsupportedAxiomVersion = plugin.configuration.getString("unsupported-axiom-version");
            if (unsupportedAxiomVersion == null) unsupportedAxiomVersion = "kick";
            if (unsupportedAxiomVersion.equals("warn")) {
                player.sendMessage(Component.text(message).color(NamedTextColor.RED));
                return;
            } else if (!unsupportedAxiomVersion.equals("ignore")) {
                player.kick(Component.text(message));
                return;
            }
        }

        int dataVersion = friendlyByteBuf.readVarInt();
        int protocolVersion = friendlyByteBuf.readVarInt();
        long handshakeId = friendlyByteBuf.readLong();

        boolean validHandshake = this.plugin.acceptHandshake(player, handshakeId);
        if (!validHandshake) {
            this.plugin.sendGoodbyeReason(player, "Invalid handshake ID");
            return;
        } else if (!this.plugin.hasPermission(player, AxiomPermission.USE)) {
            this.plugin.sendGoodbyeReason(player, "Missing axiom.use permission");
            return;
        }

        int serverDataVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        if (protocolVersion != SharedConstants.getProtocolVersion()) {
            String incompatibleDataVersion = plugin.configuration.getString("incompatible-data-version");
            if (incompatibleDataVersion == null) incompatibleDataVersion = "warn";

            if (!Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
                String message = "Axiom: Incompatible data version detected (client " + dataVersion +
                    ", server " + serverDataVersion  + ")";

                this.plugin.sendGoodbyeReason(player, message);

                if (incompatibleDataVersion.equals("warn")) {
                    player.sendMessage(Component.text(message).color(NamedTextColor.RED));
                    return;
                } else if (!incompatibleDataVersion.equals("ignore")) {
                    player.kick(Component.text(message));
                    return;
                }
            } else {
                IdMapper<BlockState> mapper;
                try {
                    mapper = ViaVersionHelper.getBlockRegistryForVersion(this.plugin.allowedBlockRegistry, protocolVersion);
                } catch (Exception e) {
                    String clientDescription = "client: " + ProtocolVersion.getProtocol(protocolVersion);
                    String serverDescription = "server: " + ProtocolVersion.getProtocol(SharedConstants.getProtocolVersion());
                    String description = clientDescription + " <-> " + serverDescription;
                    String message = "Axiom+ViaVersion: " + e.getMessage() + " (" + description + ")";

                    this.plugin.sendGoodbyeReason(player, message);

                    if (incompatibleDataVersion.equals("warn")) {
                        player.sendMessage(Component.text(message).color(NamedTextColor.RED));
                    } else {
                        player.kick(Component.text(message));
                    }
                    return;
                }

                this.plugin.playerBlockRegistry.put(player.getUniqueId(), mapper);
                this.plugin.playerProtocolVersion.put(player.getUniqueId(), protocolVersion);

                Component text = Component.text("Axiom: Warning, client and server versions don't match. " +
                        "Axiom will try to use ViaVersion conversions, but this process may cause problems");
                player.sendMessage(text.color(NamedTextColor.RED));
            }
        }

        this.plugin.onAxiomActive(player);
    }

}
