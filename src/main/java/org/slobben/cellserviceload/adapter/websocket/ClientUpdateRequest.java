package org.slobben.cellserviceload.adapter.websocket;

public record ClientUpdateRequest(String[] blocksToRemove, String[] blocksToAdd) {
}
