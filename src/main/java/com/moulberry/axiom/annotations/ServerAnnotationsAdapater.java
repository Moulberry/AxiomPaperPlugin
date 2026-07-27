package com.moulberry.axiom.annotations;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.annotations.data.AnnotationData;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class ServerAnnotationsAdapater implements PersistentDataType<PersistentDataContainer, ServerAnnotations>  {
    public static final ServerAnnotationsAdapater INSTANCE = new ServerAnnotationsAdapater();

    @Override
    public @NotNull Class<PersistentDataContainer> getPrimitiveType() {
        return PersistentDataContainer.class;
    }

    @Override
    public @NotNull Class<ServerAnnotations> getComplexType() {
        return ServerAnnotations.class;
    }

    @Override
    public @NotNull PersistentDataContainer toPrimitive(@NotNull ServerAnnotations serverAnnotations, @NotNull PersistentDataAdapterContext context) {
        PersistentDataContainer container = context.newPersistentDataContainer();

        FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (Map.Entry<UUID, AnnotationData> entry : serverAnnotations.annotations.entrySet()) {
                friendlyByteBuf.clear();
                entry.getValue().write(friendlyByteBuf);

                byte[] bytes = ByteBufUtil.getBytes(friendlyByteBuf);
                container.set(new NamespacedKey(AxiomPaper.PLUGIN, entry.getKey().toString()),
                    PersistentDataType.BYTE_ARRAY, bytes);
            }
        } finally {
            friendlyByteBuf.release();
        }

        return container;
    }

    @Override
    public @NotNull ServerAnnotations fromPrimitive(@NotNull PersistentDataContainer container, @NotNull PersistentDataAdapterContext context) {
        ServerAnnotations serverAnnotations = new ServerAnnotations();

        for (NamespacedKey key : container.getKeys()) {
            try {
                String uuidString = key.value();
                UUID uuid = UUID.fromString(uuidString);

                byte[] bytes = container.get(key, PersistentDataType.BYTE_ARRAY);
                if (bytes == null) {
                    throw new IllegalArgumentException("Annotation entry does not contain byte array data");
                }

                FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
                AnnotationData annotation;
                try {
                    annotation = AnnotationData.read(friendlyByteBuf);
                    if (annotation == null || friendlyByteBuf.isReadable()) {
                        throw new IllegalArgumentException("Unsupported or trailing annotation data");
                    }
                } finally {
                    friendlyByteBuf.release();
                }

                serverAnnotations.annotations.put(uuid, annotation);
            } catch (Exception e) {
                AxiomPaper.PLUGIN.getLogger().log(
                    Level.WARNING,
                    "Failed to load annotation " + key.value(),
                    e
                );
            }
        }

        return serverAnnotations;
    }
}
