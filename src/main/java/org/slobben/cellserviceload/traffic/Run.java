package org.slobben.cellserviceload.traffic;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.Getter;
import lombok.Setter;

/** One load test run from start to stop, with a report for every window. */
@Getter
public class Run {
  private final int id;
  private final long startedAtMs = System.currentTimeMillis();
  @Setter private volatile RunConfig config;
  private final List<Report> reports = new CopyOnWriteArrayList<>();
  private volatile long endedAtMs;
  private volatile String endReason;

  public Run(int id, RunConfig config) {
    this.id = id;
    this.config = config;
  }

  public boolean isRunning() {
    return endedAtMs == 0;
  }

  void end(String reason) {
    endReason = reason;
    endedAtMs = System.currentTimeMillis();
  }
}
