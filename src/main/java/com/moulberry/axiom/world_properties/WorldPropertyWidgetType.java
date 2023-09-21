package com.moulberry.axiom.world_properties;

import net.minecraft.network.FriendlyByteBuf;

public interface WorldPropertyWidgetType<T> {

    WorldPropertyDataType<T> dataType();
    void write(FriendlyByteBuf friendlyByteBuf);

    static WorldPropertyWidgetType<?> read(FriendlyByteBuf friendlyByteBuf) {
        int type = friendlyByteBuf.readVarInt();
        return switch (type) {
            case 0 -> CHECKBOX;
            case 1 -> new Slider(friendlyByteBuf.readInt(), friendlyByteBuf.readInt());
            case 2 -> TEXTBOX;
            case 3 -> TIME;
            default -> throw new RuntimeException("Unknown widget type: " + type);
        };
    }

    WorldPropertyWidgetType<Boolean> CHECKBOX = new WorldPropertyWidgetType<>() {
        @Override
        public WorldPropertyDataType<Boolean> dataType() {
            return WorldPropertyDataType.BOOLEAN;
        }

        @Override
        public void write(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeVarInt(0);
        }
    };

    record Slider(int min, int max) implements WorldPropertyWidgetType<Integer> {
        @Override
        public WorldPropertyDataType<Integer> dataType() {
            return WorldPropertyDataType.INTEGER;
        }

        @Override
        public void write(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeVarInt(1);
            friendlyByteBuf.writeInt(this.min);
            friendlyByteBuf.writeInt(this.max);
        }
    }

    WorldPropertyWidgetType<String> TEXTBOX = new WorldPropertyWidgetType<>() {
        @Override
        public WorldPropertyDataType<String> dataType() {
            return WorldPropertyDataType.STRING;
        }

        @Override
        public void write(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeVarInt(2);
        }
    };

    WorldPropertyWidgetType<Integer> TIME = new WorldPropertyWidgetType<>() {
        @Override
        public WorldPropertyDataType<Integer> dataType() {
            return WorldPropertyDataType.INTEGER;
        }

        @Override
        public void write(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeVarInt(3);
        }
    };

}
