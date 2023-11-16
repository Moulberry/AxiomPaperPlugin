package com.moulberry.axiom.world_properties;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Unit;

import java.util.List;

public interface WorldPropertyWidgetType<T> {

    WorldPropertyDataType<T> dataType();
    void write(FriendlyByteBuf friendlyByteBuf);

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

    WorldPropertyWidgetType<Void> BUTTON = new WorldPropertyWidgetType<>() {
        @Override
        public WorldPropertyDataType<Void> dataType() {
            return WorldPropertyDataType.EMPTY;
        }

        @Override
        public void write(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeVarInt(4);
        }
    };

    record ButtonArray(List<String> otherButtons) implements WorldPropertyWidgetType<Integer> {
        @Override
        public WorldPropertyDataType<Integer> dataType() {
            return WorldPropertyDataType.INTEGER;
        }

        @Override
        public void write(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeVarInt(5);
            friendlyByteBuf.writeCollection(this.otherButtons, FriendlyByteBuf::writeUtf);
        }
    }

}
