package com.moulberry.axiom.buffer;

@FunctionalInterface
public interface PositionConsumer<T> {

    void accept(int x, int y, int z, T t);

}
