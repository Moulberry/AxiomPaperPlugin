package com.moulberry.axiom.integration.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.LinkedHashMap;
import java.util.UUID;

public class CanBuildChecker implements Listener {
    public static LinkedHashMap<Location, Boolean> locationPlaceCheck = new LinkedHashMap<>();
    public static LinkedHashMap<Location, Boolean> locationBreakCheck = new LinkedHashMap<>();

    public static boolean canBuild(Player player, World world, int x, int y, int z) {
        return canBuild(new Location(world, x, y, z).getBlock(), player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (locationPlaceCheck.containsKey(event.getBlock().getLocation())) {
            locationPlaceCheck.put(event.getBlock().getLocation(), event.isCancelled());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (locationBreakCheck.containsKey(event.getBlock().getLocation())) {
            locationBreakCheck.put(event.getBlock().getLocation(), event.isCancelled());
            event.setCancelled(true);
        }
    }

    public static boolean canBuild(Block block, UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);

        locationPlaceCheck.put(block.getLocation(), false);
        locationBreakCheck.put(block.getLocation(), false);

        BlockPlaceEvent blockPlaceEvent = new BlockPlaceEvent(block, block.getState(), block, player.getInventory().getItemInMainHand(), player, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(blockPlaceEvent);

        BlockBreakEvent blockBreakEvent = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(blockBreakEvent);

        if (locationPlaceCheck.get(block.getLocation())) {
            locationPlaceCheck.remove(block.getLocation());
            return false;
        }

        if (locationBreakCheck.get(block.getLocation())) {
            locationBreakCheck.remove(block.getLocation());
            return false;
        }

        locationPlaceCheck.remove(block.getLocation());
        locationBreakCheck.remove(block.getLocation());
        return true;
    }

    public static boolean canBreak(Block block, UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);

        locationBreakCheck.put(block.getLocation(), false);

        BlockBreakEvent blockBreakEvent = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(blockBreakEvent);

        if (locationBreakCheck.get(block.getLocation())) {
            locationBreakCheck.remove(block.getLocation());
            return false;
        }

        locationBreakCheck.remove(block.getLocation());
        return true;
    }

    public static boolean cabPlace(Block block, UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);

        locationPlaceCheck.put(block.getLocation(), false);

        BlockPlaceEvent blockPlaceEvent = new BlockPlaceEvent(block, block.getState(), block, player.getInventory().getItemInMainHand(), player, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(blockPlaceEvent);

        if (locationPlaceCheck.get(block.getLocation())) {
            locationPlaceCheck.remove(block.getLocation());
            return false;
        }

        locationPlaceCheck.remove(block.getLocation());
        return true;
    }
}
