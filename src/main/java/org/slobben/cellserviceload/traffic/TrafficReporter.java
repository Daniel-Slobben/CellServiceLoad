package org.slobben.cellserviceload.traffic;

import static org.slobben.cellserviceload.traffic.TrafficStats.Counter.*;

import java.util.Locale;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.traffic.TrafficStats.Counter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

  @Scheduled(fixedDelay = WINDOW_MS)
  public void report() {
    Map<Counter, Long> counts = trafficStats.swapCounters();
    long largestFrameBytes = trafficStats.swapLargestFrameBytes();

    int clients = 0;
    long blocksWatched = 0;
    int maxBlocksWatched = 0;
    int zoomedOut = 0;
    int reconnecting = 0;
    for (Client client : clientRegistry.all()) {
      int watched = client.getBlocksInView().size();
      clients++;
      blocksWatched += watched;
      maxBlocksWatched = Math.max(maxBlocksWatched, watched);
      if (client.getCamera().cellSize() < ZOOMED_OUT_CELL_SIZE) {
        zoomedOut++;
      }
      if (client.isReconnecting()) {
        reconnecting++;
      }
    }

    long bytes = counts.get(FRAME_BYTES);
    log.info(
        "clients={} reconnecting={} starting={} zoomedOut={} blocksWatched avg={} max={} total={}",
        clients,
        reconnecting,
        loadScheduler.starting(),
        zoomedOut,
        clients == 0 ? 0 : blocksWatched / clients,
        maxBlocksWatched,
        blocksWatched);
    log.info(
        "frames {}/s {}MB/s ({}KB/s per client), largest {}KB, blocks full {}/s border {}/s",
        perSecond(counts.get(FRAMES)),
        String.format(Locale.ROOT, "%.2f", bytes / SECONDS / (1024 * 1024)),
        clients == 0 ? 0 : (long) (bytes / SECONDS / 1024 / clients),
        largestFrameBytes / 1024,
        perSecond(counts.get(FULL_BLOCKS)),
        perSecond(counts.get(BORDER_BLOCKS)));
    log.info(
        "frame gaps ms {}, over {}ms={}",
        trafficStats.getFrameGaps().swap(),
        trafficStats.getReconnectThresholdMs(),
        counts.get(SLOW_FRAME_GAPS));
    log.info(
        "sessions: health ack ms {}, sessionsDead={} reconnects={} reconnectFailures={} connectFailures={}",
        trafficStats.getHealthAckRtt().swap(),
        counts.get(SESSIONS_DEAD),
        counts.get(RECONNECTS),
        counts.get(RECONNECT_FAILURES),
        counts.get(CONNECT_FAILURES));
    log.info(
        "rest ms: settings {}, viewport {}, reconnect {}",
        trafficStats.getSettingsLatency().swap(),
        trafficStats.getViewportLatency().swap(),
        trafficStats.getReconnectLatency().swap());
  }

  private static long perSecond(long count) {
    return Math.round(count / SECONDS);
  }
}
