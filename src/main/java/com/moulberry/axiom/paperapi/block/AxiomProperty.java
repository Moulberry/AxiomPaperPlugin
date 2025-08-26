package com.moulberry.axiom.paperapi.block;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;

public sealed interface AxiomProperty permits ImplAxiomProperties.NmsAxiomProperty, ImplAxiomProperties.StringProperty {

    String name();
    int numValues();

    static AxiomProperty customBoolean(String name) {
        return fromNms(BooleanProperty.create(name));
    }

    static AxiomProperty customInt(String name, int min, int max) {
        return fromNms(IntegerProperty.create(name, min, max));
    }

    static AxiomProperty customString(String name, List<String> values) {
        return new ImplAxiomProperties.StringProperty(name, values);
    }

    static AxiomProperty fromNms(Property<?> property) {
        return new ImplAxiomProperties.NmsAxiomProperty(property);
    }

    static AxiomProperty axis() {
        return ImplAxiomProperties.PROPERTY_AXIS;
    }

    static AxiomProperty horizontalAxis() {
        return ImplAxiomProperties.PROPERTY_HORIZONTAL_AXIS;
    }

    static AxiomProperty facing() {
        return ImplAxiomProperties.PROPERTY_FACING;
    }

    static AxiomProperty horizontalFacing() {
        return ImplAxiomProperties.PROPERTY_HORIZONTAL_FACING;
    }

    static AxiomProperty up() {
        return ImplAxiomProperties.PROPERTY_UP;
    }

    static AxiomProperty down() {
        return ImplAxiomProperties.PROPERTY_DOWN;
    }

    static AxiomProperty north() {
        return ImplAxiomProperties.PROPERTY_NORTH;
    }

    static AxiomProperty east() {
        return ImplAxiomProperties.PROPERTY_EAST;
    }

    static AxiomProperty south() {
        return ImplAxiomProperties.PROPERTY_SOUTH;
    }

    static AxiomProperty west() {
        return ImplAxiomProperties.PROPERTY_WEST;
    }

    static AxiomProperty waterlogged() {
        return ImplAxiomProperties.PROPERTY_WATERLOGGED;
    }

    static AxiomProperty half() {
        return ImplAxiomProperties.PROPERTY_HALF;
    }

    static AxiomProperty verticalDirection() {
        return ImplAxiomProperties.PROPERTY_VERTICAL_DIRECTION;
    }

}
