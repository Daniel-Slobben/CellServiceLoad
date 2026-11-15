package org.slobben.cellserviceload.traffic;

/** What a run does; the page can change it while the run goes. */
public record RunConfig(
    int clients,
    double rampUpPerSecond,
    // 0 runs until stopped
    int durationMinutes,
    double phoneShare,
    double zoomedOutShare,
    // the frontend's watchdog: reconnect after this long without a message from the server
    long reconnectThresholdMs) {

  public RunConfig {
    check(clients >= 0 && clients <= 20_000, "clients must be 0 to 20000");
    check(rampUpPerSecond > 0 && rampUpPerSecond <= 200, "ramp-up must be above 0 and at most 200 a second");
    check(durationMinutes >= 0, "duration can't be negative");
    check(phoneShare >= 0 && phoneShare <= 1, "phone share must be 0 to 1");
    check(zoomedOutShare >= 0 && zoomedOutShare <= 1, "zoomed out share must be 0 to 1");
    check(reconnectThresholdMs >= 500, "watchdog must be at least 500 ms");
  }

  private static void check(boolean ok, String message) {
    if (!ok) {
      throw new IllegalArgumentException(message);
    }
  }
}
