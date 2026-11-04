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

  @Scheduled(fixedDelay = WINDOW_MS)
  public void report() {
    long fullBytes = trafficStats.swapFullBytes();
    long fullMsgs = trafficStats.swapFullMessages();
    long partBytes = trafficStats.swapPartialBytes();
    long partMsgs = trafficStats.swapPartialMessages();
    long recording = trafficStats.getRecordingClients();

    if (recording == 0) {
      return;
    }
    double seconds = WINDOW_MS / 1000.0;
    log.info(
        "traffic[{}clients] full={}msg {}KB ({}KB/s), partial={}msg {}KB ({}KB/s), avgPerClient full={}KB/s partial={}KB/s",
        recording,
        fullMsgs,
        fullBytes / 1024,
        (long) (fullBytes / 1024 / seconds),
        partMsgs,
        partBytes / 1024,
        (long) (partBytes / 1024 / seconds),
        (long) (fullBytes / 1024.0 / seconds / recording),
        (long) (partBytes / 1024.0 / seconds / recording));
  }
}
