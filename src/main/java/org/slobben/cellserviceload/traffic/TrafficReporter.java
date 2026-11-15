package org.slobben.cellserviceload.traffic;

import static org.slobben.cellserviceload.traffic.TrafficStats.Counter.*;

import java.util.Locale;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.traffic.Report.ClientCounts;
import org.slobben.cellserviceload.traffic.TrafficStats.Counter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Logs what the clients saw in the last window and adds it to the run; the README explains each line. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrafficReporter {
  private static final long WINDOW_MS = 10_000L;
  private static final double ZOOMED_OUT_CELL_SIZE = 0.5;

  private final TrafficStats trafficStats;
  private final ClientRegistry clientRegistry;
  private final LoadScheduler loadScheduler;
  private Run run;
  private long windowStartMs;

  public ClientCounts countClients() {
    long now = System.currentTimeMillis();
    long thresholdMs = trafficStats.getReconnectThresholdMs();
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
    return new ClientCounts(
        clients,
        open,
        reconnecting,
        loadScheduler.starting(),
        stuck,
        zoomedOut,
        clients == 0 ? 0 : blocksWatched / clients,
        maxBlocksWatched);
  }

  @Scheduled(fixedDelay = 500)
  public void report() {
    Run current = loadScheduler.current();
    if (current != run) {
      run = current;
      windowStartMs = current == null ? 0 : current.getStartedAtMs();
    }
    long now = System.currentTimeMillis();
    if (run == null || now - windowStartMs < WINDOW_MS) {
      return;
    }
    double seconds = (now - windowStartMs) / 1000.0;
    windowStartMs = now;
    Report report =
        new Report(
            (now - run.getStartedAtMs()) / 1000,
            seconds,
            countClients(),
            trafficStats.swapCounters(),
            trafficStats.swapLargestFrameBytes(),
            trafficStats.getFrameGaps().swap(),
            trafficStats.getReconnectLatency().swap(),
            trafficStats.getSettingsLatency().swap(),
            trafficStats.getViewportLatency().swap());
    run.getReports().add(report);
    log(report);
  }

  private void log(Report report) {
    ClientCounts c = report.clients();
    Map<Counter, Long> counts = report.counts();
    double seconds = report.seconds();
    log.info(
        "===== {}m{}s ===== clients={} socketsOpen={} reconnecting={} starting={} stuck={} | zoomedOut={} blocksWatched avg={} max={}",
        report.atS() / 60,
        "%02d".formatted(report.atS() % 60),
        c.clients(),
        c.open(),
        c.reconnecting(),
        c.starting(),
        c.stuck(),
        c.zoomedOut(),
        c.blocksAvg(),
        c.blocksMax());
    long bytes = counts.get(FRAME_BYTES);
    long fullBlocks = counts.get(FULL_BLOCKS);
    log.info(
        "download {}MB/s ({}KB/s per client) | full {}MB/s: {}/s avg {}KB | border {}MB/s: {}/s | frames {}/s largest {}KB",
        megabytesPerSecond(bytes, seconds),
        c.clients() == 0 ? 0 : (long) (bytes / seconds / 1024 / c.clients()),
        megabytesPerSecond(counts.get(FULL_BYTES), seconds),
        perSecond(fullBlocks, seconds),
        fullBlocks == 0 ? 0 : counts.get(FULL_BYTES) / fullBlocks / 1024,
        megabytesPerSecond(counts.get(BORDER_BYTES), seconds),
        perSecond(counts.get(BORDER_BLOCKS), seconds),
        perSecond(counts.get(FRAMES), seconds),
        report.largestFrameBytes() / 1024);
    log.info(
        "frame gaps ms {}, over {}ms={}",
        report.frameGaps(),
        trafficStats.getReconnectThresholdMs(),
        counts.get(SLOW_FRAME_GAPS));
    log.info(
        "sockets: started={} startFailed={} | reconnects closed={} silent={} retry={}, reconnect ms {} | noBlocks={}",
        counts.get(CLIENTS_STARTED),
        counts.get(START_FAILURES),
        counts.get(RECONNECTS_CLOSED),
        counts.get(RECONNECTS_SILENT),
        counts.get(RECONNECTS_RETRY),
        report.reconnects(),
        counts.get(NO_BLOCKS));
    log.info("rest ms: settings {}, viewport {}", report.settings(), report.viewport());
    if (c.clients() > 0 && counts.get(FRAMES) == 0) {
      log.warn(
          "No block frames in the last {} s. Is the server up, and does this load test speak its protocol (git pull)?",
          Math.round(seconds));
    }
  }

  private static String megabytesPerSecond(long bytes, double seconds) {
    return String.format(Locale.ROOT, "%.2f", bytes / seconds / (1024 * 1024));
  }

  private static long perSecond(long count, double seconds) {
    return Math.round(count / seconds);
  }
}
