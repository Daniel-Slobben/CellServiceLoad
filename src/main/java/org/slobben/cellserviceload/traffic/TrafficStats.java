package org.slobben.cellserviceload.traffic;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.slobben.cellserviceload.adapter.websocket.BlockFrame;
import org.springframework.stereotype.Component;

/** Counts for all clients, swapped out by the {@link TrafficReporter} every window. */
@Component
@Getter
public class TrafficStats {

  public enum Counter {
    FRAMES,
    FRAME_BYTES,
    FULL_BLOCKS,
    FULL_BYTES,
    BORDER_BLOCKS,
    BORDER_BYTES,
    // gaps the frontend's watchdog would have reconnected on
    SLOW_FRAME_GAPS,
    NO_BLOCKS,
    CLIENTS_STARTED,
    // clients whose /settings or first socket failed
    START_FAILURES,
    // why a reconnect started: the socket closed, the server went silent, or the last try failed
    RECONNECTS_CLOSED,
    RECONNECTS_SILENT,
    RECONNECTS_RETRY
  }

  // Time between block frames per client: the server tick as a user sees it, reconnects included.
  private final LatencyHistogram frameGaps = new LatencyHistogram();
  private final LatencyHistogram settingsLatency = new LatencyHistogram();
  private final LatencyHistogram viewportLatency = new LatencyHistogram();
  // From starting a new socket to its STOMP session being connected; its failures are failed reconnects.
  private final LatencyHistogram reconnectLatency = new LatencyHistogram();

  @Getter(AccessLevel.NONE)
  private final AtomicLongArray counters = new AtomicLongArray(Counter.values().length);

  @Getter(AccessLevel.NONE)
  private final AtomicLong largestFrameBytes = new AtomicLong();

  // set by the run
  @Setter private volatile long reconnectThresholdMs;

  public void count(Counter counter) {
    counters.incrementAndGet(counter.ordinal());
  }

  public void recordFrame(BlockFrame frame) {
    count(Counter.FRAMES);
    counters.addAndGet(Counter.FRAME_BYTES.ordinal(), frame.bytes());
    counters.addAndGet(Counter.FULL_BLOCKS.ordinal(), frame.fullBlocks());
    counters.addAndGet(Counter.FULL_BYTES.ordinal(), frame.fullBytes());
    counters.addAndGet(Counter.BORDER_BLOCKS.ordinal(), frame.borderBlocks());
    counters.addAndGet(Counter.BORDER_BYTES.ordinal(), frame.borderBytes());
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

  /** Forgets everything counted so far, so a new run starts clean. */
  public void reset() {
    swapCounters();
    swapLargestFrameBytes();
    frameGaps.swap();
    settingsLatency.swap();
    viewportLatency.swap();
    reconnectLatency.swap();
  }
}
