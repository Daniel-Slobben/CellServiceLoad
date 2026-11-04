package org.slobben.cellserviceload.traffic;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class TrafficStats {
  private final AtomicLong recordingClients = new AtomicLong();

  private final AtomicLong fullBytes = new AtomicLong();
  private final AtomicLong fullMessages = new AtomicLong();
  private final AtomicLong partialBytes = new AtomicLong();
  private final AtomicLong partialMessages = new AtomicLong();

  public void recordFull(int bytes) {
    fullBytes.addAndGet(bytes);
    fullMessages.incrementAndGet();
  }

  public void recordPartial(int bytes) {
    partialBytes.addAndGet(bytes);
    partialMessages.incrementAndGet();
  }

  public boolean tryAcquireRecordingSlot(int max) {
    while (true) {
      long current = recordingClients.get();
      if (current >= max) {
        return false;
      }
      if (recordingClients.compareAndSet(current, current + 1)) {
        return true;
      }
    }
  }

  public long getRecordingClients() {
    return recordingClients.get();
  }

  public long swapFullBytes() {
    return fullBytes.getAndSet(0);
  }

  public long swapFullMessages() {
    return fullMessages.getAndSet(0);
  }

  public long swapPartialBytes() {
    return partialBytes.getAndSet(0);
  }

  public long swapPartialMessages() {
    return partialMessages.getAndSet(0);
  }
}
