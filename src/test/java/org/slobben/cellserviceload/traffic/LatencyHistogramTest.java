package org.slobben.cellserviceload.traffic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LatencyHistogramTest {

  @Test
  void percentilesAreBucketUpperEdges() {
    LatencyHistogram histogram = new LatencyHistogram();
    for (int ms = 1; ms <= 100; ms++) {
      histogram.record(ms * 10 - 5);
    }

    assertEquals(new LatencyHistogram.Summary(100, 0, 500, 950, 990, 995), histogram.swap());
  }

  @Test
  void swapResetsAndOverflowKeepsExactMax() {
    LatencyHistogram histogram = new LatencyHistogram();
    histogram.record(25_000);

    assertEquals(new LatencyHistogram.Summary(1, 0, 10_010, 10_010, 10_010, 25_000), histogram.swap());
    assertEquals(new LatencyHistogram.Summary(0, 0, 0, 0, 0, 0), histogram.swap());
  }

  @Test
  void timeCountsFailedCalls() {
    LatencyHistogram histogram = new LatencyHistogram();
    histogram.time(() -> "ok");
    assertThrows(IllegalStateException.class, () -> histogram.time(() -> {
      throw new IllegalStateException("timeout");
    }));

    LatencyHistogram.Summary summary = histogram.swap();
    assertEquals(2, summary.count());
    assertEquals(1, summary.failed());
  }
}
