package com.moulberry.axiom.world_properties;

import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.nio.charset.StandardCharsets;

public abstract class WorldPropertyDataType<T> {

    public abstract int getTypeId();
    public abstract byte[] serialize(T value);
    public abstract T deserialize(byte[] bytes);

    public static WorldPropertyDataType<Boolean> BOOLEAN = new WorldPropertyDataType<>() {
        @Override
        public int getTypeId() {
            return 0;
        }

        @Override
        public byte[] serialize(Boolean value) {
            return new byte[] { value ? (byte)1 : (byte)0 };
        }

        @Override
        public Boolean deserialize(byte[] bytes) {
            return bytes[0] != 0;
        }
    };

    public static WorldPropertyDataType<Integer> INTEGER = new WorldPropertyDataType<>() {
        @Override
        public int getTypeId() {
            return 1;
        }

        @Override
        public byte[] serialize(Integer value) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(8));
            buf.writeVarInt(value);
            return buf.accessByteBufWithCorrectSize();
        }

        @Override
        public Integer deserialize(byte[] bytes) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            return buf.readVarInt();
        }
    };

    public static WorldPropertyDataType<String> STRING = new WorldPropertyDataType<>() {
        @Override
        public int getTypeId() {
            return 2;
        }

        @Override
        public byte[] serialize(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String deserialize(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    };

    public static WorldPropertyDataType<Item> ITEM = new WorldPropertyDataType<>() {
        @Override
        public int getTypeId() {
            return 3;
        }

        @Override
        public byte[] serialize(Item value) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(8));
            buf.writeId(BuiltInRegistries.ITEM, value);
            return buf.accessByteBufWithCorrectSize();
        }

        @Override
        public Item deserialize(byte[] bytes) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            return buf.readById(BuiltInRegistries.ITEM);
        }
    };

    public static WorldPropertyDataType<Block> BLOCK = new WorldPropertyDataType<>() {
        @Override
        public int getTypeId() {
            return 4;
        }

        @Override
        public byte[] serialize(Block value) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(8));
            buf.writeId(BuiltInRegistries.BLOCK, value);
            return buf.accessByteBufWithCorrectSize();
        }

        @Override
        public Block deserialize(byte[] bytes) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            return buf.readById(BuiltInRegistries.BLOCK);
        }
    };

    public static WorldPropertyDataType<Unit> EMPTY = new WorldPropertyDataType<>() {
        @Override
        public int getTypeId() {
            return 5;
        }

        @Override
        public byte[] serialize(Unit value) {
            return new byte[0];
        }

        @Override
        public Unit deserialize(byte[] bytes) {
            return Unit.INSTANCE;
        }
    };

}
