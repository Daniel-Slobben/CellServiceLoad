package org.slobben.cellserviceload.traffic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrafficReporter {
  private static final long WINDOW_MS = 10_000L;
  private static final double ZOOMED_OUT_CELL_SIZE = 0.5;

  private final TrafficStats trafficStats;
  private final ClientRegistry clientRegistry;

  @Scheduled(fixedDelay = WINDOW_MS)
  public void report() {
    long frames = trafficStats.swapFrames();
    long bytes = trafficStats.swapBytes();
    long fullBlocks = trafficStats.swapFullBlocks();
    long borderBlocks = trafficStats.swapBorderBlocks();
    long healthAcks = trafficStats.swapHealthAcks();
    long sessionsDead = trafficStats.swapSessionsDead();
    long reconnects = trafficStats.swapReconnects();
    long reconnectFailures = trafficStats.swapReconnectFailures();
    long recording = trafficStats.getRecordingClients();

    int clients = 0;
    long blocksWatched = 0;
    int maxBlocksWatched = 0;
    int zoomedOut = 0;
    for (Client client : clientRegistry.all()) {
      int watched = client.getBlocksInView().size();
      clients++;
      blocksWatched += watched;
      maxBlocksWatched = Math.max(maxBlocksWatched, watched);
      if (client.getCamera().cellSize() < ZOOMED_OUT_CELL_SIZE) {
        zoomedOut++;
      }
    }
    log.info(
        "clients={} zoomedOut={} blocksWatched avg={} max={} total={}, reconnects={} reconnectFailures={}",
        clients,
        zoomedOut,
        clients == 0 ? 0 : blocksWatched / clients,
        maxBlocksWatched,
        blocksWatched,
        reconnects,
        reconnectFailures);
    if (recording == 0) {
      return;
    }
    double seconds = WINDOW_MS / 1000.0;
    log.info(
        "traffic[{}recording] frames={} {}KB ({}KB/s, {}KB/s per client), blocks full={} border={}, healthAcks={} sessionsDead={}",
        recording,
        frames,
        bytes / 1024,
        (long) (bytes / 1024 / seconds),
        (long) (bytes / 1024.0 / seconds / recording),
        fullBlocks,
        borderBlocks,
        healthAcks,
        sessionsDead);
  }
}
