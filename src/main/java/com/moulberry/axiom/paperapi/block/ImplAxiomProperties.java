package com.moulberry.axiom.paperapi.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

@ApiStatus.Internal
public class ImplAxiomProperties {

    public static final AxiomProperty PROPERTY_AXIS = AxiomProperty.fromNms(BlockStateProperties.AXIS);
    public static final AxiomProperty PROPERTY_HORIZONTAL_AXIS = AxiomProperty.fromNms(BlockStateProperties.HORIZONTAL_AXIS);
    public static final AxiomProperty PROPERTY_FACING = AxiomProperty.fromNms(BlockStateProperties.FACING);
    public static final AxiomProperty PROPERTY_HORIZONTAL_FACING = AxiomProperty.fromNms(BlockStateProperties.HORIZONTAL_FACING);
    public static final AxiomProperty PROPERTY_UP = AxiomProperty.fromNms(BlockStateProperties.UP);
    public static final AxiomProperty PROPERTY_DOWN = AxiomProperty.fromNms(BlockStateProperties.DOWN);
    public static final AxiomProperty PROPERTY_NORTH = AxiomProperty.fromNms(BlockStateProperties.NORTH);
    public static final AxiomProperty PROPERTY_EAST = AxiomProperty.fromNms(BlockStateProperties.EAST);
    public static final AxiomProperty PROPERTY_SOUTH = AxiomProperty.fromNms(BlockStateProperties.SOUTH);
    public static final AxiomProperty PROPERTY_WEST = AxiomProperty.fromNms(BlockStateProperties.WEST);
    public static final AxiomProperty PROPERTY_WATERLOGGED = AxiomProperty.fromNms(BlockStateProperties.WATERLOGGED);
    public static final AxiomProperty PROPERTY_HALF = AxiomProperty.fromNms(BlockStateProperties.HALF);
    public static final AxiomProperty PROPERTY_VERTICAL_DIRECTION = AxiomProperty.fromNms(BlockStateProperties.VERTICAL_DIRECTION);

    public record NmsAxiomProperty(Property<?> property) implements AxiomProperty {
        @Override
        public String name() {
            return this.property.getName();
        }

        @Override
        public int numValues() {
            return this.property.getPossibleValues().size();
        }
    }

    public record StringProperty(String name, List<String> values) implements AxiomProperty {
        @Override
        public int numValues() {
            return this.values.size();
        }
    }

}
