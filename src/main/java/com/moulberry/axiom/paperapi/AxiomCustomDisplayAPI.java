package com.moulberry.axiom.paperapi;

import com.moulberry.axiom.paperapi.block.AxiomCustomBlockBuilder;
import com.moulberry.axiom.paperapi.block.AxiomProperty;
import com.moulberry.axiom.paperapi.display.AxiomCustomDisplayBuilder;
import com.moulberry.axiom.paperapi.display.ImplServerCustomDisplays;
import net.kyori.adventure.key.Key;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.CheckReturnValue;

import java.util.List;

/**
 * API methods for registering custom display items with Axiom
 * Will appear in the 'Create Display Entity' menu
 */
public class AxiomCustomDisplayAPI {

    private static final AxiomCustomDisplayAPI INSTANCE = new AxiomCustomDisplayAPI();

    private AxiomCustomDisplayAPI() {
    }

    public static AxiomCustomDisplayAPI getAPI() {
        return INSTANCE;
    }

    @CheckReturnValue
    public AxiomCustomDisplayBuilder create(Key key, String searchKey, ItemStack itemStack) {
        return new AxiomCustomDisplayBuilder(key, searchKey, itemStack);
    }

    public void register(Plugin plugin, AxiomCustomDisplayBuilder customDisplay) throws AxiomAlreadyRegisteredException {
        ImplServerCustomDisplays.register(plugin, customDisplay);
    }

    public void unregisterAll(Plugin plugin) {
        ImplServerCustomDisplays.unregisterAll(plugin);
    }

}
