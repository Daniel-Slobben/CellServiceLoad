package org.slobben.cellserviceload.traffic;

import static org.slobben.cellserviceload.traffic.TrafficStats.Counter.*;

import java.util.Locale;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.traffic.TrafficStats.Counter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Logs what the clients saw in the last window; the README explains each line. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrafficReporter {
  private static final long WINDOW_MS = 10_000L;
  private static final double SECONDS = WINDOW_MS / 1000.0;
  private static final double ZOOMED_OUT_CELL_SIZE = 0.5;

  private final TrafficStats trafficStats;
  private final ClientRegistry clientRegistry;
  private final LoadScheduler loadScheduler;
  private final long startedAtMs = System.currentTimeMillis();

  @Scheduled(fixedDelay = WINDOW_MS)
  public void report() {
    long now = System.currentTimeMillis();
    long thresholdMs = trafficStats.getReconnectThresholdMs();
    Map<Counter, Long> counts = trafficStats.swapCounters();
    long largestFrameBytes = trafficStats.swapLargestFrameBytes();

    int clients = 0;
    int open = 0;
    int reconnecting = 0;
    int stuck = 0;
    int zoomedOut = 0;
    long blocksWatched = 0;
    int maxBlocksWatched = 0;
    for (Client client : clientRegistry.all()) {
      int watched = client.getBlocksInView().size();
      clients++;
      blocksWatched += watched;
      maxBlocksWatched = Math.max(maxBlocksWatched, watched);
      if (client.getConnection().isConnected()) {
        open++;
      }
      if (client.isReconnecting()) {
        reconnecting++;
      }
      long lastFrameAtMs = client.getLastFrameAtMs() > 0 ? client.getLastFrameAtMs() : client.getCreatedAtMs();
      if (now - lastFrameAtMs > thresholdMs) {
        stuck++;
      }
      if (client.getCamera().cellSize() < ZOOMED_OUT_CELL_SIZE) {
        zoomedOut++;
      }
    }

    long elapsedS = (now - startedAtMs) / 1000;
    log.info(
        "===== {}m{}s ===== clients={} socketsOpen={} reconnecting={} starting={} stuck={} | zoomedOut={} blocksWatched avg={} max={}",
        elapsedS / 60,
        "%02d".formatted(elapsedS % 60),
        clients,
        open,
        reconnecting,
        loadScheduler.starting(),
        stuck,
        zoomedOut,
        clients == 0 ? 0 : blocksWatched / clients,
        maxBlocksWatched);
    long bytes = counts.get(FRAME_BYTES);
    long fullBlocks = counts.get(FULL_BLOCKS);
    log.info(
        "download {}MB/s ({}KB/s per client) | full {}MB/s: {}/s avg {}KB | border {}MB/s: {}/s | frames {}/s largest {}KB",
        megabytesPerSecond(bytes),
        clients == 0 ? 0 : (long) (bytes / SECONDS / 1024 / clients),
        megabytesPerSecond(counts.get(FULL_BYTES)),
        perSecond(fullBlocks),
        fullBlocks == 0 ? 0 : counts.get(FULL_BYTES) / fullBlocks / 1024,
        megabytesPerSecond(counts.get(BORDER_BYTES)),
        perSecond(counts.get(BORDER_BLOCKS)),
        perSecond(counts.get(FRAMES)),
        largestFrameBytes / 1024);
    log.info(
        "frame gaps ms {}, over {}ms={}",
        trafficStats.getFrameGaps().swap(),
        thresholdMs,
        counts.get(SLOW_FRAME_GAPS));
    log.info(
        "sockets: started={} startFailed={} | reconnects closed={} silent={} retry={}, reconnect ms {} | noBlocks={}",
        counts.get(CLIENTS_STARTED),
        counts.get(START_FAILURES),
        counts.get(RECONNECTS_CLOSED),
        counts.get(RECONNECTS_SILENT),
        counts.get(RECONNECTS_RETRY),
        trafficStats.getReconnectLatency().swap(),
        counts.get(NO_BLOCKS));
    log.info(
        "rest ms: settings {}, viewport {}",
        trafficStats.getSettingsLatency().swap(),
        trafficStats.getViewportLatency().swap());
    if (clients > 0 && counts.get(FRAMES) == 0) {
      log.warn(
          "No block frames in the last {} s. Is the server up, and does this load test speak its protocol (git pull)?",
          WINDOW_MS / 1000);
    }
  }

  private static String megabytesPerSecond(long bytes) {
    return String.format(Locale.ROOT, "%.2f", bytes / SECONDS / (1024 * 1024));
  }

  private static long perSecond(long count) {
    return Math.round(count / SECONDS);
  }
}
