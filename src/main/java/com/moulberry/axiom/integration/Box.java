package com.moulberry.axiom.integration;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    @Nullable
    public Box tryCombine(Box other) {
        if (this.completelyOverlaps(other)) {
            return this;
        }

        if (other.completelyOverlaps(this)) {
            return other;
        }

        if (other.minX == this.minX && other.maxX == this.maxX) {
            if (other.minY == this.minY && other.maxY == this.maxY) {
                if (areLineSegmentsContinuous(other.minZ, other.maxZ, this.minZ, this.maxZ)) {
                    return new Box(
                        other.minX, other.minY, Math.min(other.minZ, this.minZ),
                        other.maxX, other.maxY, Math.max(other.maxZ, this.maxZ)
                    );
                }
            } else if (other.minZ == this.minZ && other.maxZ == this.maxZ) {
                if (areLineSegmentsContinuous(other.minY, other.maxY, this.minY, this.maxY)) {
                    return new Box(
                        other.minX, Math.min(other.minY, this.minY), other.minZ,
                        other.maxX, Math.max(other.maxY, this.maxY), other.maxZ
                    );
                }
            }
        } else if (other.minY == this.minY && other.maxY == this.maxY &&
                other.minZ == this.minZ && other.maxZ == this.maxZ) {
            if (areLineSegmentsContinuous(other.minX, other.maxX, this.minX, this.maxX)) {
                return new Box(
                    Math.min(other.minX, this.minX), other.minY, other.minZ,
                    Math.max(other.maxX, this.maxX), other.maxY, other.maxZ
                );
            }
        }

        return null; // Not able to combine
    }

    public static void combineAll(List<Box> boxes) {
        main:
        while (boxes.size() >= 2) {
            for (int i = 0; i < boxes.size() - 1; i++) {
                Box first = boxes.get(i);
                for (int j = i + 1; j < boxes.size(); j++) {
                    Box second = boxes.get(j);

                    Box combined = first.tryCombine(second);
                    if (combined != null) {
                        boxes.remove(j);
                        boxes.remove(i);
                        boxes.add(combined);
                        continue main;
                    }
                }
            }
            break;
        }
    }

    public boolean completelyOverlaps(Box other) {
        return this.minX() <= other.minX() && this.minY() <= other.minY() && this.minZ() <= other.minZ() &&
                this.maxX() >= other.maxX() && this.maxY() >= other.maxY() && this.maxZ() >= other.maxZ();
    }

    public boolean contains(int x, int y, int z) {
        return this.minX() <= x && this.minY() <= y && this.minZ() <= z &&
                this.maxX() >= x && this.maxY() >= y && this.maxZ() >= z;
    }

    @Nullable
    public static Box intersection(Box first, Box second) {
        if (first.minX > second.maxX || second.minX > first.maxX ||
                first.minY > second.maxY || second.minY > first.maxY ||
                first.minZ > second.maxZ || second.minZ > first.maxZ) {
            return null;
        }

        return new Box(
            Math.max(first.minX, second.minX),
            Math.max(first.minY, second.minY),
            Math.max(first.minZ, second.minZ),
            Math.min(first.maxX, second.maxX),
            Math.min(first.maxY, second.maxY),
            Math.min(first.maxZ, second.maxZ)
        );
    }

    private static boolean areLineSegmentsContinuous(int min1, int max1, int min2, int max2) {
        int size1 = max1 - min1 + 1;
        int size2 = max2 - min2 + 1;

        float mid1 = (min1 + max1);
        float mid2 = (min2 + max2);

        float midDiff = Math.abs(mid1 - mid2);
        return midDiff <= size1 + size2;
    }

}
