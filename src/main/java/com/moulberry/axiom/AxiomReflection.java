package com.moulberry.axiom;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.jpenilla.reflectionremapper.ReflectionRemapper;

import java.lang.reflect.Method;

public class AxiomReflection {

    private static Method updateBlockEntityTicker = null;

    public static void init() {
        ReflectionRemapper reflectionRemapper = ReflectionRemapper.forReobfMappingsInPaperJar();
        String methodName = reflectionRemapper.remapMethodName(LevelChunk.class, "updateBlockEntityTicker", BlockEntity.class);

        try {
            updateBlockEntityTicker = LevelChunk.class.getDeclaredMethod(methodName, BlockEntity.class);
            updateBlockEntityTicker.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void updateBlockEntityTicker(LevelChunk levelChunk, BlockEntity blockEntity) {
        try {
            updateBlockEntityTicker.invoke(levelChunk, blockEntity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
