package org.slobben.cellserviceload.traffic;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import lombok.AccessLevel;
import lombok.Getter;
import org.slobben.cellserviceload.adapter.websocket.BlockFrame;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Counts for all clients, swapped out by the {@link TrafficReporter} every window. */
@Component
@Getter
public class TrafficStats {

  public enum Counter {
    FRAMES,
    FRAME_BYTES,
    FULL_BLOCKS,
    BORDER_BLOCKS,
    // gaps the frontend's watchdog would have reconnected on
    SLOW_FRAME_GAPS,
    NO_BLOCKS,
    RECONNECTS,
    RECONNECT_FAILURES,
    CONNECT_FAILURES
  }

  // Time between block frames per client: the server tick as a user sees it, reconnects included.
  private final LatencyHistogram frameGaps = new LatencyHistogram();
  private final LatencyHistogram settingsLatency = new LatencyHistogram();
  private final LatencyHistogram viewportLatency = new LatencyHistogram();
  // From starting a new socket to its STOMP session being connected.
  private final LatencyHistogram reconnectLatency = new LatencyHistogram();

  @Getter(AccessLevel.NONE)
  private final AtomicLongArray counters = new AtomicLongArray(Counter.values().length);

  @Getter(AccessLevel.NONE)
  private final AtomicLong largestFrameBytes = new AtomicLong();

  @Value("${load.reconnect-threshold-ms}")
  private long reconnectThresholdMs;

  public void count(Counter counter) {
    counters.incrementAndGet(counter.ordinal());
  }

  public void recordFrame(BlockFrame frame) {
    count(Counter.FRAMES);
    counters.addAndGet(Counter.FRAME_BYTES.ordinal(), frame.bytes());
    counters.addAndGet(Counter.FULL_BLOCKS.ordinal(), frame.fullBlocks());
    counters.addAndGet(Counter.BORDER_BLOCKS.ordinal(), frame.borderBlocks());
    largestFrameBytes.accumulateAndGet(frame.bytes(), Math::max);
  }

  public void recordFrameGap(long ms) {
    frameGaps.record(ms);
    if (ms > reconnectThresholdMs) {
      count(Counter.SLOW_FRAME_GAPS);
    }
  }

  public Map<Counter, Long> swapCounters() {
    Map<Counter, Long> snapshot = new EnumMap<>(Counter.class);
    for (Counter counter : Counter.values()) {
      snapshot.put(counter, counters.getAndSet(counter.ordinal(), 0));
    }
    return snapshot;
  }

  public long swapLargestFrameBytes() {
    return largestFrameBytes.getAndSet(0);
  }
}
