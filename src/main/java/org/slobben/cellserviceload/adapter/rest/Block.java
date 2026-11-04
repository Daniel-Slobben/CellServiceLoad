package org.slobben.cellserviceload.adapter.rest;

/** A block as it arrives in {@link Settings}: JSON, with the LZ4 cells base64 encoded. */
public record Block(int x, int y, int generation, String type, String encodedCells) {
}
