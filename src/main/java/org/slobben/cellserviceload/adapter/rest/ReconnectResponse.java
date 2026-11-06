package org.slobben.cellserviceload.adapter.rest;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** {@code viewport} is null when the server still has one of the client's visible blocks. */
public record ReconnectResponse(UUID clientId, @Nullable Coordinates viewport) {
}
