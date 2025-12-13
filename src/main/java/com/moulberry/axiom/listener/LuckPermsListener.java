package com.moulberry.axiom.listener;

import com.moulberry.axiom.AxiomPaper;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;

public class LuckPermsListener {

    public static void register(AxiomPaper plugin) {
        LuckPermsProvider.get().getEventBus().subscribe(UserDataRecalculateEvent.class, event -> {
            plugin.clearCachedPermissionsFor(event.getUser().getUniqueId());
        });
    }

}
