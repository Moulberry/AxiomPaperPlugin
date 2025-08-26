package com.moulberry.axiom.listener;

import com.moulberry.axiom.AxiomPaper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.GenericGameEvent;

public class NoPhysicalTriggerListener implements Listener {

    private final AxiomPaper plugin;

    public NoPhysicalTriggerListener(AxiomPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGenericGameEvent(GenericGameEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (this.plugin.isNoPhysicalTrigger(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && this.plugin.isNoPhysicalTrigger(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

}
