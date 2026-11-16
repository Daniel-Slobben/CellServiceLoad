package org.slobben.cellserviceload.traffic;

import java.util.Map;

import org.slobben.cellserviceload.traffic.LatencyHistogram.Summary;
import org.slobben.cellserviceload.traffic.TrafficStats.Counter;

/** What the clients saw in one window; counts are totals for the window, not rates. */
public record Report(
    long atS,
    double seconds,
    ClientCounts clients,
    Map<Counter, Long> counts,
    long largestFrameBytes,
    Summary frameGaps,
    Summary reconnects,
    Summary settings,
    Summary viewport) {

  /** The clients right now. Stuck clients have had no block frame for longer than the watchdog. */
  public record ClientCounts(
      int clients,
      int open,
      int reconnecting,
      int starting,
      int stuck,
      int zoomedOut,
      int sectors,
      long blocksAvg,
      int blocksMax) {}
}
