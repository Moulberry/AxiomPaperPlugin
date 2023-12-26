package com.moulberry.axiom.buffer;

import com.google.common.util.concurrent.RateLimiter;
import it.unimi.dsi.fastutil.objects.Object2ByteMap;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public class BiomeBuffer {

    private final Position2ByteMap map;
    private final ResourceKey<Biome>[] palette;
    private final Object2ByteMap<ResourceKey<Biome>> paletteReverse;
    private int paletteSize = 0;

    public BiomeBuffer() {
        this.map = new Position2ByteMap();
        this.palette = new ResourceKey[255];
        this.paletteReverse = new Object2ByteOpenHashMap<>();
    }

    private BiomeBuffer(Position2ByteMap map, ResourceKey<Biome>[] palette, Object2ByteMap<ResourceKey<Biome>> paletteReverse) {
        this.map = map;
        this.palette = palette;
        this.paletteReverse = paletteReverse;
        this.paletteSize = this.paletteReverse.size();
    }

    public int size() {
        return this.map.size();
    }

    public void save(FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeByte(this.paletteSize);
        for (int i = 0; i < this.paletteSize; i++) {
            friendlyByteBuf.writeResourceKey(this.palette[i]);
        }
        this.map.save(friendlyByteBuf);
    }

    public static BiomeBuffer load(FriendlyByteBuf friendlyByteBuf, @Nullable RateLimiter rateLimiter, AtomicBoolean reachedRateLimit) {
        int paletteSize = friendlyByteBuf.readByte();
        ResourceKey<Biome>[] palette = new ResourceKey[255];
        Object2ByteMap<ResourceKey<Biome>> paletteReverse = new Object2ByteOpenHashMap<>();
        for (int i = 0; i < paletteSize; i++) {
            ResourceKey<Biome> key = friendlyByteBuf.readResourceKey(Registries.BIOME);
            palette[i] = key;
            paletteReverse.put(key, (byte)(i+1));
        }
        Position2ByteMap map = Position2ByteMap.load(friendlyByteBuf, rateLimiter, reachedRateLimit);
        return new BiomeBuffer(map, palette, paletteReverse);

    }

    public void clear() {
        this.map.clear();
    }

    public void forEachEntry(PositionConsumer<ResourceKey<Biome>> consumer) {
        this.map.forEachEntry((x, y, z, v) -> {
            if (v != 0) consumer.accept(x, y, z, this.palette[(v & 0xFF) - 1]);
        });
    }

    public ResourceKey<Biome> get(int quartX, int quartY, int quartZ) {
        int index = this.map.get(quartX, quartY, quartZ) & 0xFF;
        if (index == 0) return null;
        return this.palette[index - 1];
    }

    private int getPaletteIndex(ResourceKey<Biome> biome) {
        int index = this.paletteReverse.getOrDefault(biome, (byte) 0) & 0xFF;
        if (index != 0) return index;

        index = this.paletteSize;
        if (index >= this.palette.length) {
            throw new UnsupportedOperationException("Too many biomes! :(");
        }

        this.palette[index] = biome;
        this.paletteReverse.put(biome, (byte)(index + 1));

        this.paletteSize += 1;
        return index + 1;
    }

    public void set(int quartX, int quartY, int quartZ, ResourceKey<Biome> biome) {
        this.map.put(quartX, quartY, quartZ, (byte) this.getPaletteIndex(biome));
    }

}
