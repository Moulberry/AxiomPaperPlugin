package com.moulberry.axiom.paperapi;

import com.moulberry.axiom.paperapi.entity.ImplAxiomHiddenEntities;
import org.bukkit.entity.Display;
import org.bukkit.entity.Marker;

import java.util.UUID;

public class AxiomEntityAPI {

    private static final AxiomEntityAPI INSTANCE = new AxiomEntityAPI();

    private AxiomEntityAPI() {
    }

    public static AxiomEntityAPI getAPI() {
        return INSTANCE;
    }

    public void hideMarkerGizmo(Marker marker) {
        ImplAxiomHiddenEntities.hideMarkerGizmo(marker);
    }

    public void hideDisplayGizmo(Display display) {
        ImplAxiomHiddenEntities.hideDisplayGizmo(display);
    }

    // Hide custom display gizmo associated with an object
    // Will be automatically cleaned up when object is garbage collected
    public void hideCustomDisplayGizmo(Object object, UUID uuid) {
        ImplAxiomHiddenEntities.hideCustomDisplayGizmo(object, uuid);
    }


}
