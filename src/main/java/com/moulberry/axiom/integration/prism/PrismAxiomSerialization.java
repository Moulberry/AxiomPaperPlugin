package com.moulberry.axiom.integration.prism;

import com.moulberry.axiom.AxiomPaper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueOutput;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

final class PrismAxiomSerialization {
    private static final String PARTS_VERSION = "v2";

    private PrismAxiomSerialization() {
    }

    @Nullable
    static String captureEntitySnapshot(Entity entity) {
        try {
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
            CompoundTag savedEntity = entity.saveAsPassenger(output) ? output.buildResult() : null;
            return savedEntity == null ? null : savedEntity.toString();
        } catch (Exception exception) {
            AxiomPaper.PLUGIN.getLogger().warning("Failed to snapshot entity for Prism: " + exception.getMessage());
            return null;
        }
    }

    @Nullable
    static String captureEntityState(Entity entity) {
        try {
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
            entity.saveWithoutId(output);
            CompoundTag savedEntity = output.buildResult();
            savedEntity.remove("Passengers");
            savedEntity.putString("id", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType()).toString());
            return savedEntity.toString();
        } catch (Exception exception) {
            AxiomPaper.PLUGIN.getLogger().warning("Failed to snapshot entity state for Prism: " + exception.getMessage());
            return null;
        }
    }

    static boolean sameLocation(Location first, Location second) {
        return Objects.equals(first.getWorld(), second.getWorld())
            && Double.compare(first.getX(), second.getX()) == 0
            && Double.compare(first.getY(), second.getY()) == 0
            && Double.compare(first.getZ(), second.getZ()) == 0
            && Float.compare(first.getYaw(), second.getYaw()) == 0
            && Float.compare(first.getPitch(), second.getPitch()) == 0;
    }

    static String encodeLocation(Location location) {
        return location.getWorld().getUID()
            + ","
            + location.getX()
            + ","
            + location.getY()
            + ","
            + location.getZ()
            + ","
            + location.getYaw()
            + ","
            + location.getPitch();
    }

    static Location decodeLocation(String encodedLocation) {
        String[] encodedParts = encodedLocation.split(",", 6);
        if (encodedParts.length != 6) {
            throw new IllegalArgumentException("Invalid Prism location state");
        }
        UUID worldUuid = UUID.fromString(encodedParts[0]);
        double x = Double.parseDouble(encodedParts[1]);
        double y = Double.parseDouble(encodedParts[2]);
        double z = Double.parseDouble(encodedParts[3]);
        float yaw = Float.parseFloat(encodedParts[4]);
        float pitch = Float.parseFloat(encodedParts[5]);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Non-finite Prism location state");
        }
        World world = Bukkit.getWorld(worldUuid);
        return new Location(world, x, y, z, yaw, pitch);
    }

    static String encodeWorldTimeState(long time, boolean daylightCycleEnabled) {
        return time + "," + daylightCycleEnabled;
    }

    static WorldTimeState decodeWorldTimeState(String encodedState) {
        String[] encodedParts = encodedState.split(",", 2);
        if (encodedParts.length != 2 || !(encodedParts[1].equals("true") || encodedParts[1].equals("false"))) {
            throw new IllegalArgumentException("Invalid Prism world time state");
        }
        return new WorldTimeState(Long.parseLong(encodedParts[0]), Boolean.parseBoolean(encodedParts[1]));
    }

    static String encodeBytes(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    static byte[] decodeBytes(String encodedBytes) {
        return Base64.getDecoder().decode(encodedBytes);
    }

    static String encodeParts(String... parts) {
        StringBuilder encodedBuilder = new StringBuilder(PARTS_VERSION);
        for (String part : parts) {
            encodedBuilder.append(';');
            if (part == null) {
                encodedBuilder.append('n');
            } else {
                encodedBuilder.append('s');
                encodedBuilder.append(Base64.getEncoder().encodeToString(part.getBytes(StandardCharsets.UTF_8)));
            }
        }
        return encodedBuilder.toString();
    }

    static boolean hasPartsVersion(@Nullable String encodedParts) {
        return encodedParts != null && encodedParts.startsWith(PARTS_VERSION + ";");
    }

    static String[] decodeParts(@Nullable String encodedParts, int expectedPartCount) {
        if (encodedParts == null || encodedParts.isEmpty()) {
            throw new IllegalArgumentException("Missing Prism custom data");
        }

        String[] rawParts = encodedParts.split(";", -1);
        String[] decodedParts = new String[expectedPartCount];
        if (rawParts[0].equals(PARTS_VERSION)) {
            if (rawParts.length != expectedPartCount + 1) {
                throw new IllegalArgumentException("Unexpected Prism custom data field count");
            }
            for (int index = 0; index < expectedPartCount; index++) {
                String rawPart = rawParts[index + 1];
                if (rawPart.equals("n")) {
                    decodedParts[index] = null;
                } else if (rawPart.startsWith("s")) {
                    decodedParts[index] = new String(
                        Base64.getDecoder().decode(rawPart.substring(1)),
                        StandardCharsets.UTF_8
                    );
                } else {
                    throw new IllegalArgumentException("Invalid Prism custom data field");
                }
            }
            return decodedParts;
        }

        if (rawParts.length > expectedPartCount) {
            throw new IllegalArgumentException("Unexpected legacy Prism custom data field count");
        }
        for (int index = 0; index < rawParts.length; index++) {
            if (!rawParts[index].isEmpty()) {
                decodedParts[index] = new String(
                    Base64.getDecoder().decode(rawParts[index]),
                    StandardCharsets.UTF_8
                );
            }
        }
        return decodedParts;
    }

    static String[] decodeParts(@Nullable String encodedParts, int minimumPartCount, int maximumPartCount) {
        if (minimumPartCount < 0 || maximumPartCount < minimumPartCount) {
            throw new IllegalArgumentException("Invalid Prism custom data field bounds");
        }
        if (encodedParts == null || encodedParts.isEmpty()) {
            throw new IllegalArgumentException("Missing Prism custom data");
        }

        String[] rawParts = encodedParts.split(";", -1);
        int offset = rawParts[0].equals(PARTS_VERSION) ? 1 : 0;
        int fieldCount = rawParts.length - offset;
        if (fieldCount < minimumPartCount || fieldCount > maximumPartCount) {
            throw new IllegalArgumentException("Unexpected Prism custom data field count");
        }
        return decodeParts(encodedParts, fieldCount);
    }

    record WorldTimeState(long time, boolean daylightCycleEnabled) {
    }
}
