package org.slobben.cellserviceload.traffic;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.adapter.rest.ServiceApiWrapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettingsRefresher {
  static final long REFRESH_INTERVAL_MS = 5 * 60 * 1_000L;
  private static final int MAX_CONCURRENT_REFRESHES = 4;

  private final ClientRegistry clientRegistry;
  private final ServiceApiWrapper serviceApiWrapper;
  private final Executor virtualThreadExecutor;

  private final Semaphore inFlight = new Semaphore(MAX_CONCURRENT_REFRESHES);

  @Scheduled(fixedDelay = 10_000)
  public void tick() {
    long now = System.currentTimeMillis();
    for (Client client : clientRegistry.all()) {
      if (client.getNextRefreshAtMs() > now) {
        continue;
      }
      if (!inFlight.tryAcquire()) {
        return;
      }
      virtualThreadExecutor.execute(
          () -> {
            if (!client.getLock().tryLock()) {
              inFlight.release();
              return;
            }
            try {
              client.setNextRefreshAtMs(System.currentTimeMillis() + REFRESH_INTERVAL_MS);
              serviceApiWrapper.getSettings();
            } catch (RuntimeException e) {
              log.debug("settings refresh failed for {}: {}", client.getId(), e.toString());
            } finally {
              client.getLock().unlock();
              inFlight.release();
            }
          });
    }
  }
}
