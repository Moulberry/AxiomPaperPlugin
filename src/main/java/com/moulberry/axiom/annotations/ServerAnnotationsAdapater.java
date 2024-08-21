package com.moulberry.axiom.annotations;

import com.moulberry.axiom.AxiomPaper;
import com.moulberry.axiom.annotations.data.AnnotationData;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

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

        for (Map.Entry<UUID, AnnotationData> entry : serverAnnotations.annotations.entrySet()) {
            try {
                friendlyByteBuf.writerIndex(0);
                entry.getValue().write(friendlyByteBuf);

                byte[] bytes = new byte[friendlyByteBuf.writerIndex()];
                friendlyByteBuf.getBytes(0, bytes);

                container.set(new NamespacedKey(AxiomPaper.PLUGIN, entry.getKey().toString()),
                    PersistentDataType.BYTE_ARRAY, bytes);
            } catch (Exception e) {
                e.printStackTrace();
            }
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
                AnnotationData annotation = AnnotationData.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));

                serverAnnotations.annotations.put(uuid, annotation);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return serverAnnotations;
    }
}
