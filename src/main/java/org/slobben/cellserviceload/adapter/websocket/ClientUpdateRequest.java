package org.slobben.cellserviceload.adapter.websocket;

import java.util.UUID;

public record ClientUpdateRequest(UUID client, String[] blocksToRemove, String[] blocksToAdd) {
}
