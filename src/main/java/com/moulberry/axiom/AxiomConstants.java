package com.moulberry.axiom;

import net.minecraft.core.BlockPos;
import org.bukkit.NamespacedKey;

public class AxiomConstants {

    public static final long MIN_POSITION_LONG = BlockPos.asLong(-33554432, -2048, -33554432);
    static {
        if (MIN_POSITION_LONG != 0b1000000000000000000000000010000000000000000000000000100000000000L) {
            throw new Error("BlockPos representation changed!");
        }
    }

    public static final int API_VERSION = 8;

}
