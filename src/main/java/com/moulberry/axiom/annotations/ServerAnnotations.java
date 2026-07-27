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

    private static List<ServerPlayer> sendAnnotationUpdates(
        List<AnnotationUpdateAction> actions,
        List<ServerPlayer> players
    ) {
        List<ServerPlayer> failedPlayers = new ArrayList<>();
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            friendlyByteBuf.writeCollection(actions, (buffer, action) -> action.write(buffer));
            byte[] bytes = ByteBufUtil.getBytes(friendlyByteBuf);
            for (ServerPlayer serverPlayer : players) {
                try {
                    VersionHelper.sendCustomPayload(
                        serverPlayer,
                        VersionHelper.createIdentifier("axiom:annotation_update"),
                        bytes
                    );
                } catch (RuntimeException exception) {
                    failedPlayers.add(serverPlayer);
                    AxiomPaper.PLUGIN.getLogger().warning(
                        "Failed to send annotation updates to " + serverPlayer.getScoreboardName()
                            + ": " + exception.getMessage()
                    );
                }
            }
        } catch (RuntimeException exception) {
            failedPlayers.addAll(players);
            AxiomPaper.PLUGIN.getLogger().warning(
                "Failed to serialize annotation updates: " + exception.getMessage()
            );
        } finally {
            friendlyByteBuf.release();
        }
        return failedPlayers;
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

        ServerAnnotations currentAnnotations = getOrLoad(world);
        ServerAnnotations updatedAnnotations = shallowCopyOf(currentAnnotations);
        java.util.Set<UUID> copiedAnnotations = new java.util.HashSet<>();

        boolean dirty = false;

        for (AnnotationUpdateAction action : actions) {
            switch (action) {
                case AnnotationUpdateAction.CreateAnnotation(var uuid, var annotationData) -> {
                    updatedAnnotations.annotations.put(uuid, copyAnnotation(annotationData));
                    dirty = true;
                }
                case AnnotationUpdateAction.DeleteAnnotation(var uuid) -> {
                    AnnotationData removed = updatedAnnotations.annotations.remove(uuid);
                    if (removed != null) {
                        dirty = true;
                    }
                }
                case AnnotationUpdateAction.MoveAnnotation(var uuid, var to) -> {
                    AnnotationData annotation = updatedAnnotations.annotations.get(uuid);
                    if (annotation != null) {
                        if (copiedAnnotations.add(uuid)) {
                            annotation = copyAnnotation(annotation);
                            updatedAnnotations.annotations.put(uuid, annotation);
                        }
                        annotation.setPosition(to);
                        dirty = true;
                    }
                }
                case AnnotationUpdateAction.ClearAllAnnotations ignored -> {
                    if (!updatedAnnotations.annotations.isEmpty()) {
                        updatedAnnotations.annotations.clear();
                        dirty = true;
                    }
                }
                case AnnotationUpdateAction.RotateAnnotation(var uuid, var to) -> {
                    AnnotationData annotation = updatedAnnotations.annotations.get(uuid);
                    if (annotation != null) {
                        if (copiedAnnotations.add(uuid)) {
                            annotation = copyAnnotation(annotation);
                            updatedAnnotations.annotations.put(uuid, annotation);
                        }
                        annotation.setRotation(to);
                        dirty = true;
                    }
                }
                default -> throw new UnsupportedOperationException("Unknown action: " + action.getClass());
            }
        }

        if (dirty) {
            world.getPersistentDataContainer().set(
                ANNOTATION_DATA_KEY,
                ServerAnnotationsAdapater.INSTANCE,
                updatedAnnotations
            );
            serverAnnotationCache.put(world, updatedAnnotations);
        }

        // Forward actions back to clients
        List<ServerPlayer> playersWithAxiom = new ArrayList<>();

        for (ServerPlayer player : ((CraftWorld)world).getHandle().players()) {
            if (AxiomPaper.PLUGIN.canUseAxiom(player.getBukkitEntity())) {
                playersWithAxiom.add(player);
            }
        }

        if (!playersWithAxiom.isEmpty()) {
            scheduleFullResync(world, sendAnnotationUpdates(actions, playersWithAxiom));
        }
    }

    private static void scheduleFullResync(World world, List<ServerPlayer> failedPlayers) {
        if (failedPlayers.isEmpty()) {
            return;
        }

        try {
            org.bukkit.Bukkit.getScheduler().runTask(AxiomPaper.PLUGIN, () -> {
                for (ServerPlayer serverPlayer : failedPlayers) {
                    try {
                        org.bukkit.entity.Player player = serverPlayer.getBukkitEntity();
                        if (player.isOnline() && player.getWorld().equals(world)
                            && AxiomPaper.PLUGIN.canUseAxiom(player)) {
                            sendAll(world, serverPlayer);
                        }
                    } catch (RuntimeException exception) {
                        AxiomPaper.PLUGIN.getLogger().warning(
                            "Failed to resynchronize annotations for " + serverPlayer.getScoreboardName()
                                + ": " + exception.getMessage()
                        );
                    }
                }
            });
        } catch (RuntimeException exception) {
            AxiomPaper.PLUGIN.getLogger().warning(
                "Failed to schedule annotation resynchronization: " + exception.getMessage()
            );
        }
    }

    public static AnnotationSnapshot captureSnapshot(World world) {
        LinkedHashMap<UUID, byte[]> serialized = new LinkedHashMap<>();
        ServerAnnotations serverAnnotations = getOrLoad(world);
        if (serverAnnotations != null) {
            for (Map.Entry<UUID, AnnotationData> entry : serverAnnotations.annotations.entrySet()) {
                serialized.put(entry.getKey(), serializeAnnotation(entry.getValue()));
            }
        }
        return new AnnotationSnapshot(serialized);
    }

    public static byte[] createDelta(AnnotationSnapshot source, AnnotationSnapshot target) {
        List<AnnotationUpdateAction> actions = new ArrayList<>();
        java.util.LinkedHashSet<UUID> changed = new java.util.LinkedHashSet<>(source.annotations.keySet());
        changed.addAll(target.annotations.keySet());

        for (UUID uuid : changed) {
            byte[] sourceData = source.annotations.get(uuid);
            byte[] targetData = target.annotations.get(uuid);
            if (java.util.Arrays.equals(sourceData, targetData)) {
                continue;
            }

            if (targetData == null) {
                actions.add(new AnnotationUpdateAction.DeleteAnnotation(uuid));
            } else {
                actions.add(new AnnotationUpdateAction.CreateAnnotation(uuid, deserializeAnnotation(targetData)));
            }
        }
        return serializeActions(actions);
    }

    public static void applyActions(World world, byte[] serializedActions) {
        handleUpdates(world, deserializeActions(serializedActions));
    }

    public static boolean isNoOp(World world, byte[] serializedActions) {
        AnnotationSnapshot current = captureSnapshot(world);
        LinkedHashMap<UUID, byte[]> result = new LinkedHashMap<>();
        current.annotations.forEach((uuid, data) -> result.put(uuid, data.clone()));

        for (AnnotationUpdateAction action : deserializeActions(serializedActions)) {
            switch (action) {
                case AnnotationUpdateAction.CreateAnnotation(var uuid, var annotationData) ->
                    result.put(uuid, serializeAnnotation(annotationData));
                case AnnotationUpdateAction.DeleteAnnotation(var uuid) -> result.remove(uuid);
                case AnnotationUpdateAction.MoveAnnotation(var uuid, var to) -> {
                    byte[] existing = result.get(uuid);
                    if (existing != null) {
                        AnnotationData annotationData = deserializeAnnotation(existing);
                        annotationData.setPosition(to);
                        result.put(uuid, serializeAnnotation(annotationData));
                    }
                }
                case AnnotationUpdateAction.RotateAnnotation(var uuid, var to) -> {
                    byte[] existing = result.get(uuid);
                    if (existing != null) {
                        AnnotationData annotationData = deserializeAnnotation(existing);
                        annotationData.setRotation(to);
                        result.put(uuid, serializeAnnotation(annotationData));
                    }
                }
                case AnnotationUpdateAction.ClearAllAnnotations ignored -> result.clear();
                default -> throw new UnsupportedOperationException("Unknown action: " + action.getClass());
            }
        }

        if (!current.annotations.keySet().equals(result.keySet())) {
            return false;
        }
        return current.annotations.entrySet().stream()
            .allMatch(entry -> java.util.Arrays.equals(entry.getValue(), result.get(entry.getKey())));
    }

    private static List<AnnotationUpdateAction> deserializeActions(byte[] serializedActions) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(serializedActions));
        try {
            List<AnnotationUpdateAction> actions = friendlyByteBuf.readCollection(
                AxiomPaper.PLUGIN.limitCollection(ArrayList::new),
                AnnotationUpdateAction::read
            );
            if (actions.stream().anyMatch(java.util.Objects::isNull) || friendlyByteBuf.isReadable()) {
                throw new IllegalArgumentException("Unsupported or trailing annotation action data");
            }
            return actions;
        } finally {
            friendlyByteBuf.release();
        }
    }

    private static byte[] serializeActions(List<AnnotationUpdateAction> actions) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            friendlyByteBuf.writeCollection(actions, (buffer, action) -> action.write(buffer));
            return ByteBufUtil.getBytes(friendlyByteBuf);
        } finally {
            friendlyByteBuf.release();
        }
    }

    private static byte[] serializeAnnotation(AnnotationData annotationData) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            annotationData.write(friendlyByteBuf);
            return ByteBufUtil.getBytes(friendlyByteBuf);
        } finally {
            friendlyByteBuf.release();
        }
    }

    private static AnnotationData deserializeAnnotation(byte[] serializedAnnotation) {
        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(serializedAnnotation));
        try {
            AnnotationData annotationData = AnnotationData.read(friendlyByteBuf);
            if (annotationData == null || friendlyByteBuf.isReadable()) {
                throw new IllegalArgumentException("Unsupported or trailing annotation data");
            }
            return annotationData;
        } finally {
            friendlyByteBuf.release();
        }
    }

    private static ServerAnnotations shallowCopyOf(ServerAnnotations source) {
        ServerAnnotations copy = new ServerAnnotations();
        if (source != null) {
            copy.annotations.putAll(source.annotations);
        }
        return copy;
    }

    private static AnnotationData copyAnnotation(AnnotationData annotationData) {
        return deserializeAnnotation(serializeAnnotation(annotationData));
    }

    private static ServerAnnotations getOrLoad(World world) {
        ServerAnnotations serverAnnotations = serverAnnotationCache.get(world);
        if (serverAnnotations == null) {
            serverAnnotations = world.getPersistentDataContainer().get(ANNOTATION_DATA_KEY, ServerAnnotationsAdapater.INSTANCE);
            serverAnnotationCache.put(world, serverAnnotations);
        }
        return serverAnnotations;
    }

    public record AnnotationSnapshot(Map<UUID, byte[]> annotations) {
        public AnnotationSnapshot {
            LinkedHashMap<UUID, byte[]> immutableCopy = new LinkedHashMap<>();
            annotations.forEach((uuid, data) -> immutableCopy.put(uuid, data.clone()));
            annotations = java.util.Collections.unmodifiableMap(immutableCopy);
        }

        @Override
        public Map<UUID, byte[]> annotations() {
            LinkedHashMap<UUID, byte[]> copy = new LinkedHashMap<>();
            this.annotations.forEach((uuid, data) -> copy.put(uuid, data.clone()));
            return java.util.Collections.unmodifiableMap(copy);
        }
    }

}
