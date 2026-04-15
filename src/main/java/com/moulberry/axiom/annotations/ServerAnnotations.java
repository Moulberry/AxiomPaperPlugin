package com.moulberry.axiom.annotations;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.VersionHelper;
import com.moulberry.axiom.annotations.data.AnnotationData;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;

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

        byte[] bytes = ByteBufUtil.getBytes(friendlyByteBuf);
        for (ServerPlayer serverPlayer : players) {
            VersionHelper.sendCustomPayload(serverPlayer, VersionHelper.createIdentifier("axiom:annotation_update"), bytes);
        }
    }

    public static void sendAll(World world, ServerPlayer player) {
        if (!AxiomPaper.PLUGIN.allowAnnotations) {
            return;
        }

        List<AnnotationUpdateAction> actions = new ArrayList<>();

        actions.add(new AnnotationUpdateAction.ClearAllAnnotations());

        ServerAnnotations serverAnnotations = getOrLoad(world);

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

        ServerAnnotations serverAnnotations = getOrLoad(world);
        if (serverAnnotations == null) {
            serverAnnotations = new ServerAnnotations();
            serverAnnotationCache.put(world, serverAnnotations);
        }

        boolean dirty = false;

        for (AnnotationUpdateAction action : actions) {
            switch (action) {
                case AnnotationUpdateAction.CreateAnnotation(var uuid, var annotationData) -> {
                    serverAnnotations.annotations.put(uuid, annotationData);
                    dirty = true;
                }
                case AnnotationUpdateAction.DeleteAnnotation(var uuid) -> {
                    AnnotationData removed = serverAnnotations.annotations.remove(uuid);
                    if (removed != null) {
                        dirty = true;
                    }
                }
                case AnnotationUpdateAction.MoveAnnotation(var uuid, var to) -> {
                    AnnotationData annotation = serverAnnotations.annotations.get(uuid);
                    if (annotation != null) {
                        annotation.setPosition(to);
                        dirty = true;
                    }
                }
                case AnnotationUpdateAction.ClearAllAnnotations ignored -> {
                    if (!serverAnnotations.annotations.isEmpty()) {
                        serverAnnotations.annotations.clear();
                        dirty = true;
                    }
                }
                case AnnotationUpdateAction.RotateAnnotation(var uuid, var to) -> {
                    AnnotationData annotation = serverAnnotations.annotations.get(uuid);
                    if (annotation != null) {
                        annotation.setRotation(to);
                        dirty = true;
                    }
                }
                default -> throw new UnsupportedOperationException("Unknown action: " + action.getClass());
            }
        }

        if (dirty) {
            world.getPersistentDataContainer().set(ANNOTATION_DATA_KEY, ServerAnnotationsAdapater.INSTANCE, serverAnnotations);
        }

        // Forward actions back to clients
        List<ServerPlayer> playersWithAxiom = new ArrayList<>();

        for (ServerPlayer player : ((CraftWorld)world).getHandle().players()) {
            if (AxiomPaper.PLUGIN.canUseAxiom(player.getBukkitEntity())) {
                playersWithAxiom.add(player);
            }
        }

        if (!playersWithAxiom.isEmpty()) {
            sendAnnotationUpdates(actions, playersWithAxiom);
        }
    }

    public static byte[] createSnapshot(World world) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        List<AnnotationUpdateAction> actions = new ArrayList<>();
        actions.add(new AnnotationUpdateAction.ClearAllAnnotations());

        ServerAnnotations serverAnnotations = getOrLoad(world);
        if (serverAnnotations != null) {
            for (Map.Entry<UUID, AnnotationData> entry : serverAnnotations.annotations.entrySet()) {
                actions.add(new AnnotationUpdateAction.CreateAnnotation(entry.getKey(), entry.getValue()));
            }
        }

        friendlyByteBuf.writeCollection(actions, (buffer, action) -> action.write(buffer));
        return ByteBufUtil.getBytes(friendlyByteBuf);
    }

    public static void applySnapshot(World world, byte[] snapshot) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(snapshot));
        List<AnnotationUpdateAction> actions = new ArrayList<>();
        while (friendlyByteBuf.isReadable()) {
            AnnotationUpdateAction action = AnnotationUpdateAction.read(friendlyByteBuf);
            if (action != null) {
                actions.add(action);
            }
        }
        handleUpdates(world, actions);
    }

    private static ServerAnnotations getOrLoad(World world) {
        ServerAnnotations serverAnnotations = serverAnnotationCache.get(world);
        if (serverAnnotations == null) {
            serverAnnotations = world.getPersistentDataContainer().get(ANNOTATION_DATA_KEY, ServerAnnotationsAdapater.INSTANCE);
            serverAnnotationCache.put(world, serverAnnotations);
        }
        return serverAnnotations;
    }

}
