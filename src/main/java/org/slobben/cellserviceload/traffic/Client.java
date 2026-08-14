package org.slobben.cellserviceload.traffic;

import org.slobben.cellserviceload.adapter.websocket.WebSocketClientConnection;

import java.util.List;
import java.util.UUID;

public record Client(
        UUID id,
        List<String> blocksInView,
        WebSocketClientConnection webSocketClientConnection) {

}

