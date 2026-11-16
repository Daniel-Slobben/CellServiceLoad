package org.slobben.cellserviceload.adapter.websocket;

// sectors: the alive cells per sector of the blocks instead of their cells, as a frontend zoomed far out asks
public record ClientUpdateRequest(String[] blocksToRemove, String[] blocksToAdd, boolean sectors) {
}
