package org.slobben.cellserviceload.traffic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrafficReporter {
  private static final long WINDOW_MS = 10_000L;

  private final TrafficStats trafficStats;
  private final ClientRegistry clientRegistry;

  @Scheduled(fixedDelay = WINDOW_MS)
  public void report() {
    long frames = trafficStats.swapFrames();
    long bytes = trafficStats.swapBytes();
    long fullBlocks = trafficStats.swapFullBlocks();
    long borderBlocks = trafficStats.swapBorderBlocks();
    long healthAcks = trafficStats.swapHealthAcks();
    long sessionsDead = trafficStats.swapSessionsDead();
    long reconnects = trafficStats.swapReconnects();
    long reconnectFailures = trafficStats.swapReconnectFailures();
    long recording = trafficStats.getRecordingClients();

    log.info(
        "clients={} reconnects={} reconnectFailures={}",
        clientRegistry.size(),
        reconnects,
        reconnectFailures);
    if (recording == 0) {
      return;
    }
    double seconds = WINDOW_MS / 1000.0;
    log.info(
        "traffic[{}recording] frames={} {}KB ({}KB/s, {}KB/s per client), blocks full={} border={}, healthAcks={} sessionsDead={}",
        recording,
        frames,
        bytes / 1024,
        (long) (bytes / 1024 / seconds),
        (long) (bytes / 1024.0 / seconds / recording),
        fullBlocks,
        borderBlocks,
        healthAcks,
        sessionsDead);
  }
}
