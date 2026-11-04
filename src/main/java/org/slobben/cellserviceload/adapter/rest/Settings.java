package org.slobben.cellserviceload.adapter.rest;

import java.util.List;
import java.util.UUID;

public record Settings(int blockSize, UUID clientId, Coordinates viewport, List<Block> blocks) {
}
