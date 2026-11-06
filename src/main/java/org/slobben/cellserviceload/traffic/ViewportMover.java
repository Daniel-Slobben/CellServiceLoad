package org.slobben.cellserviceload.traffic;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.adapter.rest.Coordinates;
import org.slobben.cellserviceload.adapter.rest.ServiceApiWrapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Moves each client's camera like a user would: idle most of the time, now and then a pan, a zoom
 * or a jump to a new viewport. Visible blocks are sent when they change, at most every 150 ms like
 * the frontend's publish window.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ViewportMover {
  static final int TICK_MS = 150;

  private static final int IDLE_MIN_MS = 2_000;
  private static final int IDLE_MAX_MS = 15_000;
  private static final int PAN_MIN_MS = 300;
  private static final int PAN_MAX_MS = 2_000;
  private static final int PAN_MIN_PX_PER_S = 300;
  private static final int PAN_MAX_PX_PER_S = 1_500;
  // About eight wheel notches of 1.05 a second.
  private static final double ZOOM_FACTOR_PER_S = 1.5;
  private static final int ZOOM_MAX_MS = 10_000;
  private static final int PAN_CHANCE_PERCENT = 55;
  private static final int ZOOM_CHANCE_PERCENT = 30;

  public enum Action {
    IDLE,
    PAN,
    ZOOM
  }

  private final ClientRegistry clientRegistry;
  private final ServiceApiWrapper serviceApiWrapper;
  private final Executor virtualThreadExecutor;
  private final TrafficStats trafficStats;

  @Scheduled(fixedDelay = TICK_MS)
  public void tick() {
    long now = System.currentTimeMillis();
    for (Client client : clientRegistry.all()) {
      if (client.isReconnecting()
          || (client.getAction() == Action.IDLE && client.getActionEndsAtMs() > now)) {
        continue;
      }
      virtualThreadExecutor.execute(
          () -> {
            if (!client.getLock().tryLock()) {
              return;
            }
            try {
              step(client, now);
            } catch (RuntimeException e) {
              log.warn("move failed for {}: {}", client.getId(), e.toString());
            } finally {
              client.getLock().unlock();
            }
          });
    }
  }

  private void step(Client client, long now) {
    double seconds = Math.min(now - client.getLastMoveAtMs(), 1_000) / 1000.0;
    client.setLastMoveAtMs(now);
    Camera camera = client.getCamera();

    switch (client.getAction()) {
      case IDLE -> startNextAction(client, now);
      case PAN -> camera.pan(client.getPanVelocityX() * seconds, client.getPanVelocityY() * seconds);
      case ZOOM -> {
        double toTarget = client.getZoomTarget() / camera.cellSize();
        double factor = Math.pow(ZOOM_FACTOR_PER_S, seconds);
        factor = toTarget < 1 ? Math.max(1 / factor, toTarget) : Math.min(factor, toTarget);
        camera.zoomAt(client.getZoomPointX(), client.getZoomPointY(), factor);
        if (factor == toTarget) {
          idle(client, now);
        }
      }
    }
    if (client.getAction() != Action.IDLE && client.getActionEndsAtMs() <= now) {
      idle(client, now);
    }
    publish(client);
  }

  private void startNextAction(Client client, long now) {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    int roll = rng.nextInt(100);
    if (roll < PAN_CHANCE_PERCENT) {
      double angle = rng.nextDouble(2 * Math.PI);
      double speed = rng.nextInt(PAN_MIN_PX_PER_S, PAN_MAX_PX_PER_S);
      client.setPanVelocityX(Math.cos(angle) * speed);
      client.setPanVelocityY(Math.sin(angle) * speed);
      client.setAction(Action.PAN);
      client.setActionEndsAtMs(now + rng.nextInt(PAN_MIN_MS, PAN_MAX_MS));
    } else if (roll < PAN_CHANCE_PERCENT + ZOOM_CHANCE_PERCENT) {
      Device device = client.getCamera().device();
      client.setZoomTarget(zoomTarget(client.isZoomsOut(), rng));
      client.setZoomPointX(rng.nextDouble(device.width));
      client.setZoomPointY(rng.nextDouble(device.height));
      client.setAction(Action.ZOOM);
      client.setActionEndsAtMs(now + ZOOM_MAX_MS);
    } else {
      // The previous/next button: the frontend centres on a fresh interesting viewport.
      Coordinates viewport = trafficStats.getViewportLatency().time(serviceApiWrapper::getViewport);
      client.getCamera().centerOn(viewport.x(), viewport.y());
      idle(client, now);
    }
  }

  // Log-uniform, so zooming in and out are equally likely around the default.
  private static double zoomTarget(boolean zoomsOut, ThreadLocalRandom rng) {
    double min = zoomsOut ? Camera.MIN_CELL_SIZE : 0.8;
    double max = zoomsOut ? 0.4 : 8;
    return Math.exp(rng.nextDouble(Math.log(min), Math.log(max)));
  }

  private static void idle(Client client, long now) {
    client.setAction(Action.IDLE);
    client.setActionEndsAtMs(now + ThreadLocalRandom.current().nextInt(IDLE_MIN_MS, IDLE_MAX_MS));
  }

  private static void publish(Client client) {
    int[] range = client.getCamera().blockRange();
    if (Arrays.equals(range, client.getViewRange())) {
      return;
    }
    client.setViewRange(range);
    Set<String> oldBlocks = client.getBlocksInView();
    Set<String> newBlocks = Camera.blocksIn(range);
    String[] toRemove = diff(oldBlocks, newBlocks);
    String[] toAdd = diff(newBlocks, oldBlocks);

    client.getConnection().sendClientUpdateRequest(toRemove, toAdd);
    oldBlocks.clear();
    oldBlocks.addAll(newBlocks);
  }

  private static String[] diff(Set<String> a, Set<String> b) {
    Set<String> result = new HashSet<>(a);
    result.removeAll(b);
    return result.toArray(new String[0]);
  }
}
