package com.moulberry.axiom;

import com.moulberry.axiom.event.AxiomCreateWorldPropertiesEvent;
import com.moulberry.axiom.world_properties.WorldPropertyCategory;
import com.moulberry.axiom.world_properties.WorldPropertyWidgetType;
import com.moulberry.axiom.world_properties.server.PropertyUpdateResult;
import com.moulberry.axiom.world_properties.server.ServerWorldProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

public class WorldPropertiesExample implements Listener {

    private static final ServerWorldProperty<Boolean> CHECKBOX = new ServerWorldProperty<>(
        new NamespacedKey("axiom", "checkbox"),
        "Checkbox",
        false, WorldPropertyWidgetType.CHECKBOX, world -> false,
        (player, world, bool) -> {
            world.sendMessage(Component.text("Checkbox: " + bool)); // Do something with input
            return PropertyUpdateResult.UPDATE_AND_SYNC; // sync with client
        }
    );

    private static final ServerWorldProperty<Integer> SLIDER = new ServerWorldProperty<>(
        new NamespacedKey("axiom", "slider"),
        "Slider",
        false, new WorldPropertyWidgetType.Slider(0, 8),
        world -> 4,
        (player, world, integer) -> {
            world.sendMessage(Component.text("Slider: " + integer)); // Do something with input
            return PropertyUpdateResult.UPDATE_AND_SYNC; // sync with client
        }
    );

    private static final ServerWorldProperty<String> TEXTBOX = new ServerWorldProperty<>(
        new NamespacedKey("axiom", "textbox"),
        "Textbox",
        false, WorldPropertyWidgetType.TEXTBOX,
        world -> "Hello",
        (player, world, string) -> {
            world.sendMessage(Component.text("Textbox: " + string)); // Do something with input
            return PropertyUpdateResult.UPDATE_AND_SYNC; // sync with client
        }
    );

    private static final ServerWorldProperty<Void> BUTTON = new ServerWorldProperty<>(
        new NamespacedKey("axiom", "button"),
        "Button",
        false, WorldPropertyWidgetType.BUTTON,
        world -> null,
        (player, world, unit) -> {
            world.sendMessage(Component.text("Button pressed")); // Do something with input
            return PropertyUpdateResult.UPDATE_AND_SYNC; // sync with client
        }
    );

    @EventHandler
    public void onCreateWorldProperties(AxiomCreateWorldPropertiesEvent event) {
        WorldPropertyCategory category = new WorldPropertyCategory("Examples", false);
        event.addCategory(category, List.of(CHECKBOX, SLIDER, TEXTBOX, BUTTON));
    }

}
