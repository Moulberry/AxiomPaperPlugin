package com.moulberry.axiom.world_properties.server;

public enum PropertyUpdateResult {

    UPDATE_AND_SYNC(true, true),
    UPDATE_WITHOUT_SYNC(true, false),
    CANCEL(false, false);

    private final boolean update;
    private final boolean sync;

    PropertyUpdateResult(boolean update, boolean sync) {
        this.update = update;
        this.sync = sync;
    }

    public boolean isUpdate() {
        return this.update;
    }

    public boolean isSync() {
        return this.sync;
    }

}
