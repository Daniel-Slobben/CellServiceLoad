package org.slobben.cellserviceload.traffic;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Supplier;

/** Milliseconds in 10 ms buckets up to 10 s, plus the exact max; swapped out every report window. */
public class LatencyHistogram {
  private static final int BUCKET_MS = 10;
  private static final int BUCKETS = 1_000;

  // the last bucket holds everything from 10 s up
  private final AtomicLongArray counts = new AtomicLongArray(BUCKETS + 1);
  private final AtomicLong max = new AtomicLong();

  public void record(long ms) {
    counts.incrementAndGet((int) Math.min(Math.max(ms, 0) / BUCKET_MS, BUCKETS));
    max.accumulateAndGet(ms, Math::max);
  }

  /** Times the call, failed calls included: a timeout is a latency too. */
  public <T> T time(Supplier<T> call) {
    long start = System.nanoTime();
    try {
      return call.get();
    } finally {
      record((System.nanoTime() - start) / 1_000_000);
    }
  }

  public Summary swap() {
    long[] snapshot = new long[BUCKETS + 1];
    long count = 0;
    for (int i = 0; i <= BUCKETS; i++) {
      snapshot[i] = counts.getAndSet(i, 0);
      count += snapshot[i];
    }
    long maxMs = max.getAndSet(0);
    if (count == 0) {
      return new Summary(0, 0, 0, 0, 0);
    }
    return new Summary(
        count,
        percentile(snapshot, count, 0.50),
        percentile(snapshot, count, 0.95),
        percentile(snapshot, count, 0.99),
        maxMs);
  }

  // The upper edge of the bucket the percentile falls in.
  private static long percentile(long[] buckets, long count, double p) {
    long rank = (long) Math.ceil(count * p);
    long seen = 0;
    for (int i = 0; i < buckets.length; i++) {
      seen += buckets[i];
      if (seen >= rank) {
        return (long) (i + 1) * BUCKET_MS;
      }
    }
    return (long) buckets.length * BUCKET_MS;
  }

  public record Summary(long count, long p50, long p95, long p99, long max) {
    @Override
    public String toString() {
      return "p50=%d p95=%d p99=%d max=%d (n=%d)".formatted(p50, p95, p99, max, count);
    }
  }
}
