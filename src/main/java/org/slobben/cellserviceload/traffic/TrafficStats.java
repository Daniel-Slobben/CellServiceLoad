package org.slobben.cellserviceload.traffic;

import java.util.concurrent.atomic.AtomicLong;

import org.slobben.cellserviceload.adapter.websocket.BlockFrame;
import org.slobben.cellserviceload.adapter.websocket.HealthCheckResponse;
import org.slobben.cellserviceload.adapter.websocket.TopicListener;
import org.springframework.stereotype.Component;

@Component
public class TrafficStats implements TopicListener {
  private final AtomicLong recordingClients = new AtomicLong();

  private final AtomicLong frames = new AtomicLong();
  private final AtomicLong bytes = new AtomicLong();
  private final AtomicLong fullBlocks = new AtomicLong();
  private final AtomicLong borderBlocks = new AtomicLong();
  private final AtomicLong healthAcks = new AtomicLong();
  private final AtomicLong sessionsDead = new AtomicLong();

  @Override
  public void onBlocks(BlockFrame frame) {
    frames.incrementAndGet();
    bytes.addAndGet(frame.bytes());
    fullBlocks.addAndGet(frame.fullBlocks());
    borderBlocks.addAndGet(frame.borderBlocks());
  }

  @Override
  public void onHealthCheck(HealthCheckResponse.Type type) {
    switch (type) {
      case HEALTH_ACK -> healthAcks.incrementAndGet();
      case SESSION_DEAD -> sessionsDead.incrementAndGet();
    }
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

  public long swapFrames() {
    return frames.getAndSet(0);
  }

  public long swapBytes() {
    return bytes.getAndSet(0);
  }

  public long swapFullBlocks() {
    return fullBlocks.getAndSet(0);
  }

  public long swapBorderBlocks() {
    return borderBlocks.getAndSet(0);
  }

  public long swapHealthAcks() {
    return healthAcks.getAndSet(0);
  }

  public long swapSessionsDead() {
    return sessionsDead.getAndSet(0);
  }
}
