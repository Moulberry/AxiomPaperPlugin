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
        World world = Bukkit.getWorld(UUID.fromString(encodedParts[0]));
        return new Location(
            world,
            Double.parseDouble(encodedParts[1]),
            Double.parseDouble(encodedParts[2]),
            Double.parseDouble(encodedParts[3]),
            Float.parseFloat(encodedParts[4]),
            Float.parseFloat(encodedParts[5])
        );
    }

    static String encodeWorldTimeState(long time, boolean daylightCycleEnabled) {
        return time + "," + daylightCycleEnabled;
    }

    static WorldTimeState decodeWorldTimeState(String encodedState) {
        String[] encodedParts = encodedState.split(",", 2);
        return new WorldTimeState(Long.parseLong(encodedParts[0]), Boolean.parseBoolean(encodedParts[1]));
    }

    static String encodeBytes(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    static byte[] decodeBytes(String encodedBytes) {
        return Base64.getDecoder().decode(encodedBytes);
    }

    static String encodeParts(String... parts) {
        StringBuilder encodedBuilder = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) {
                encodedBuilder.append(';');
            }

            String part = parts[index];
            String encodedPart = part == null
                ? ""
                : Base64.getEncoder().encodeToString(part.getBytes(StandardCharsets.UTF_8));
            encodedBuilder.append(encodedPart);
        }
        return encodedBuilder.toString();
    }

    static String[] decodeParts(@Nullable String encodedParts, int expectedPartCount) {
        String[] decodedParts = new String[expectedPartCount];
        if (encodedParts == null || encodedParts.isEmpty()) {
            return decodedParts;
        }

        String[] rawParts = encodedParts.split(";", -1);
        for (int index = 0; index < expectedPartCount && index < rawParts.length; index++) {
            if (!rawParts[index].isEmpty()) {
                decodedParts[index] = new String(Base64.getDecoder().decode(rawParts[index]), StandardCharsets.UTF_8);
            }
        }
        return decodedParts;
    }

    record WorldTimeState(long time, boolean daylightCycleEnabled) {
    }
}
