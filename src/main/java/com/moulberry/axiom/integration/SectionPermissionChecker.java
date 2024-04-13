package com.moulberry.axiom.integration;

import java.util.List;

public interface SectionPermissionChecker {

    boolean allAllowed();
    boolean noneAllowed();
    boolean allowed(int x, int y, int z);
    Box bounds();

    static SectionPermissionChecker combine(SectionPermissionChecker first, SectionPermissionChecker second) {
        if (first.noneAllowed() || second.noneAllowed()) {
            return NONE_ALLOWED;
        }
        if (first.allAllowed()) {
            return second;
        }
        if (second.allAllowed()) {
            return first;
        }

        Box intersect = Box.intersection(first.bounds(), second.bounds());
        if (intersect == null) {
            return NONE_ALLOWED;
        }

        return new SectionPermissionChecker() {
            @Override
            public boolean allAllowed() {
                return false;
            }

            @Override
            public boolean noneAllowed() {
                return false;
            }

            @Override
            public boolean allowed(int x, int y, int z) {
                return first.allowed(x, y, z) && second.allowed(x, y, z);
            }

            @Override
            public Box bounds() {
                return intersect;
            }
        };
    }

    static SectionPermissionChecker fromAllowedBoxes(List<Box> allowed) {
        if (allowed.isEmpty()) return NONE_ALLOWED;

        if (allowed.size() == 1) {
            Box allowedBox = allowed.get(0);
            if (allowedBox.completelyOverlaps(FULL_BOUNDS)) {
                return ALL_ALLOWED;
            } else {
                return new AllAllowedInBox(allowedBox);
            }
        }

        int minBoundsX = 15;
        int minBoundsY = 15;
        int minBoundsZ = 15;
        int maxBoundsX = 0;
        int maxBoundsY = 0;
        int maxBoundsZ = 0;

        for (Box box : allowed) {
            minBoundsX = Math.min(box.minX(), minBoundsX);
            minBoundsY = Math.min(box.minY(), minBoundsY);
            minBoundsZ = Math.min(box.minZ(), minBoundsZ);
            maxBoundsX = Math.max(box.maxX(), maxBoundsX);
            maxBoundsY = Math.max(box.maxY(), maxBoundsY);
            maxBoundsZ = Math.max(box.maxZ(), maxBoundsZ);
        }

        return new AllAllowedBoxes(new Box(minBoundsX, minBoundsY, minBoundsZ, maxBoundsX, maxBoundsY, maxBoundsZ), allowed);
    }

    static SectionPermissionChecker fromBoxWithBooleans(List<BoxWithBoolean> boxes, boolean defaultValue) {
        if (boxes.isEmpty()) return defaultValue ? ALL_ALLOWED : NONE_ALLOWED;

        if (boxes.size() == 1) {
            BoxWithBoolean boxWithBoolean = boxes.get(0);
            if (boxWithBoolean.value()) {
                if (defaultValue) {
                    return ALL_ALLOWED;
                } else if (boxWithBoolean.box().completelyOverlaps(FULL_BOUNDS)) {
                    return ALL_ALLOWED;
                } else {
                    return new AllAllowedInBox(boxWithBoolean.box());
                }
            } else {
                if (!defaultValue) {
                    return NONE_ALLOWED;
                } else if (boxWithBoolean.box().completelyOverlaps(FULL_BOUNDS)) {
                    return NONE_ALLOWED;
                }
            }
        }

        int minBoundsX = 15;
        int minBoundsY = 15;
        int minBoundsZ = 15;
        int maxBoundsX = 0;
        int maxBoundsY = 0;
        int maxBoundsZ = 0;

        for (BoxWithBoolean boxWithBoolean : boxes) {
            if (boxWithBoolean.value()) {
                Box box = boxWithBoolean.box();
                minBoundsX = Math.min(box.minX(), minBoundsX);
                minBoundsY = Math.min(box.minY(), minBoundsY);
                minBoundsZ = Math.min(box.minZ(), minBoundsZ);
                maxBoundsX = Math.max(box.maxX(), maxBoundsX);
                maxBoundsY = Math.max(box.maxY(), maxBoundsY);
                maxBoundsZ = Math.max(box.maxZ(), maxBoundsZ);
            }
        }

        Box bounds = new Box(minBoundsX, minBoundsY, minBoundsZ, maxBoundsX, maxBoundsY, maxBoundsZ);
        return new BooleanBoxes(bounds, boxes, defaultValue);
    }

    record AllAllowedInBox(Box box) implements SectionPermissionChecker {
        @Override
        public boolean allAllowed() {
            return true;
        }

        @Override
        public boolean noneAllowed() {
            return false;
        }

        @Override
        public boolean allowed(int x, int y, int z) {
            return true;
        }

        @Override
        public Box bounds() {
            return box;
        }
    }

    record AllAllowedBoxes(Box bounds, List<Box> allowed) implements SectionPermissionChecker {
        @Override
        public boolean allAllowed() {
            return false;
        }

        @Override
        public boolean noneAllowed() {
            return false;
        }

        @Override
        public boolean allowed(int x, int y, int z) {
            for (Box box : this.allowed) {
                if (box.contains(x, y, z)) return true;
            }
            return false;
        }

        @Override
        public Box bounds() {
            return this.bounds;
        }
    }

    record BooleanBoxes(Box bounds, List<BoxWithBoolean> boxes, boolean defaultValue) implements SectionPermissionChecker {
        @Override
        public boolean allAllowed() {
            return false;
        }

        @Override
        public boolean noneAllowed() {
            return false;
        }

        @Override
        public boolean allowed(int x, int y, int z) {
            for (BoxWithBoolean boxWithBoolean : this.boxes) {
                if (boxWithBoolean.box().contains(x, y, z)) {
                    return boxWithBoolean.value();
                }
            }
            return this.defaultValue;
        }

        @Override
        public Box bounds() {
            return this.bounds;
        }
    }

    Box FULL_BOUNDS = new Box(0, 0, 0, 15, 15, 15);
    SectionPermissionChecker ALL_ALLOWED = new SectionPermissionChecker() {
        @Override
        public boolean allAllowed() {
            return true;
        }

        @Override
        public boolean noneAllowed() {
            return false;
        }

        @Override
        public boolean allowed(int x, int y, int z) {
            return true;
        }

        @Override
        public Box bounds() {
            return FULL_BOUNDS;
        }
    };


    Box EMPTY_BOUNDS = new Box(0, 0, 0, 0, 0, 0);
    SectionPermissionChecker NONE_ALLOWED = new SectionPermissionChecker() {
        @Override
        public boolean allAllowed() {
            return false;
        }

        @Override
        public boolean noneAllowed() {
            return true;
        }

        @Override
        public boolean allowed(int x, int y, int z) {
            return false;
        }

        @Override
        public Box bounds() {
            return EMPTY_BOUNDS;
        }
    };

}
