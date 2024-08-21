package com.moulberry.axiom.annotations;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.annotations.data.AnnotationData;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class ServerAnnotations {

    private static final WeakHashMap<World, ServerAnnotations> serverAnnotationCache = new WeakHashMap<>();
    private static final NamespacedKey ANNOTATION_DATA_KEY = new NamespacedKey(AxiomPaper.PLUGIN, "annotation_data");

    final LinkedHashMap<UUID, AnnotationData> annotations = new LinkedHashMap<>();

    private static void sendAnnotationUpdates(List<AnnotationUpdateAction> actions, List<ServerPlayer> players) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        friendlyByteBuf.writeCollection(actions, (buffer, action) -> action.write(buffer));

        byte[] bytes = new byte[friendlyByteBuf.writerIndex()];
        friendlyByteBuf.getBytes(0, bytes);
        for (ServerPlayer serverPlayer : players) {
            VersionHelper.sendCustomPayload(serverPlayer, VersionHelper.createResourceLocation("axiom:annotation_update"), bytes);
        }
    }

    public static void sendAll(World world, ServerPlayer player) {
        if (!AxiomPaper.PLUGIN.allowAnnotations) {
            return;
        }

        List<AnnotationUpdateAction> actions = new ArrayList<>();

        actions.add(new AnnotationUpdateAction.ClearAllAnnotations());

        ServerAnnotations serverAnnotations = serverAnnotationCache.get(world);
        if (serverAnnotations == null) {
            serverAnnotations = world.getPersistentDataContainer().get(ANNOTATION_DATA_KEY, ServerAnnotationsAdapater.INSTANCE);
            serverAnnotationCache.put(world, serverAnnotations);
        }

        if (serverAnnotations != null) {
            for (Map.Entry<UUID, AnnotationData> entry : serverAnnotations.annotations.entrySet()) {
                actions.add(new AnnotationUpdateAction.CreateAnnotation(entry.getKey(), entry.getValue()));
            }
        }

        sendAnnotationUpdates(actions, List.of(player));
    }

    public static void handleUpdates(World world, List<AnnotationUpdateAction> actions) {
        if (!AxiomPaper.PLUGIN.allowAnnotations) {
            return;
        }

        ServerAnnotations serverAnnotations = serverAnnotationCache.get(world);
        if (serverAnnotations == null) {
            serverAnnotations = world.getPersistentDataContainer().get(ANNOTATION_DATA_KEY, ServerAnnotationsAdapater.INSTANCE);
            serverAnnotationCache.put(world, serverAnnotations);
        }
        if (serverAnnotations == null) {
            serverAnnotations = new ServerAnnotations();
            serverAnnotationCache.put(world, serverAnnotations);
        }

        boolean dirty = false;

        for (AnnotationUpdateAction action : actions) {
            if (action instanceof AnnotationUpdateAction.CreateAnnotation create) {
                serverAnnotations.annotations.put(create.uuid(), create.annotationData());
                dirty = true;
            } else if (action instanceof AnnotationUpdateAction.DeleteAnnotation delete) {
                AnnotationData removed = serverAnnotations.annotations.remove(delete.uuid());
                if (removed != null) {
                    dirty = true;
                }
            } else if (action instanceof AnnotationUpdateAction.MoveAnnotation move) {
                AnnotationData annotation = serverAnnotations.annotations.get(move.uuid());
                if (annotation != null) {
                    annotation.setPosition(move.to());
                    dirty = true;
                }
            } else if (action instanceof AnnotationUpdateAction.ClearAllAnnotations) {
                if (!serverAnnotations.annotations.isEmpty()) {
                    serverAnnotations.annotations.clear();
                    dirty = true;
                }
            } else if (action instanceof AnnotationUpdateAction.RotateAnnotation rotate) {
                AnnotationData annotation = serverAnnotations.annotations.get(rotate.uuid());
                if (annotation != null) {
                    annotation.setRotation(rotate.to());
                    dirty = true;
                }
            } else {
                throw new UnsupportedOperationException("Unknown action: " + action.getClass());
            }
        }

        if (dirty) {
            world.getPersistentDataContainer().set(ANNOTATION_DATA_KEY, ServerAnnotationsAdapater.INSTANCE, serverAnnotations);
        }

        // Forward actions back to clients
        List<ServerPlayer> playersWithAxiom = new ArrayList<>();

        for (ServerPlayer player : ((CraftWorld)world).getHandle().players()) {
            if (AxiomPaper.PLUGIN.canUseAxiom(player.getBukkitEntity(), "axiom.annotations.view")) {
                playersWithAxiom.add(player);
            }
        }

        if (!playersWithAxiom.isEmpty()) {
            sendAnnotationUpdates(actions, playersWithAxiom);
        }
    }

}
