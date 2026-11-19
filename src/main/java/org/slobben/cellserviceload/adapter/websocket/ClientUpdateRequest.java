package org.slobben.cellserviceload.adapter.websocket;

// The blocks in view, minX to maxX and minY to maxY inclusive. sectors: the alive cells per sector of the blocks
// instead of their cells, as a frontend zoomed far out asks. resend: full blocks wanted again; the load test never does.
public record ClientUpdateRequest(int minX, int maxX, int minY, int maxY, boolean sectors, String[] resend) {
}
