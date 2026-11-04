package org.slobben.cellserviceload.traffic;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.utils.BlockUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ViewportMover {
  static final int MAX_DRIFT = 5;
  static final int MIN_VIEW_SIDE = 2;
  static final int MAX_VIEW_SIDE = 4;
  static final int MOVE_INTERVAL_MS = 6_000;
  static final int TICKS_PER_DIRECTION = 1;
  static final int ZOOM_CHANCE_PERCENT = 20;

  private static final int[][] DIRECTIONS = {
    {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
  };

  private final ClientRegistry clientRegistry;
  private final Executor virtualThreadExecutor;

  @Scheduled(fixedDelay = 500)
  public void tick() {
    long now = System.currentTimeMillis();
    for (Client client : clientRegistry.all()) {
      if (client.getNextMoveAtMs() > now) {
        continue;
      }
      virtualThreadExecutor.execute(
          () -> {
            if (!client.getLock().tryLock()) {
              return;
            }
            try {
              moveOne(client, now);
            } catch (RuntimeException e) {
              log.warn("move failed for {}: {}", client.getId(), e.toString());
            } finally {
              client.getLock().unlock();
            }
          });
    }
  }

  private void moveOne(Client client, long now) {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    boolean zoom = rng.nextInt(100) < ZOOM_CHANCE_PERCENT;

    Set<String> oldBlocks = client.getBlocksInView();
    Set<String> newBlocks;

    if (zoom) {
      int delta = rng.nextBoolean() ? 1 : -1;
      int newSide = clamp(client.getViewSide() + delta, MIN_VIEW_SIDE, MAX_VIEW_SIDE);
      if (newSide == client.getViewSide()) {
        client.setNextMoveAtMs(now + MOVE_INTERVAL_MS);
        return;
      }
      client.setViewSide(newSide);
      newBlocks = computeView(client.getOriginX(), client.getOriginY(), newSide);
    } else {
      if (client.getTicksInDirection() <= 0 || (client.getDx() == 0 && client.getDy() == 0)) {
        int[] d = DIRECTIONS[rng.nextInt(DIRECTIONS.length)];
        client.setDx(d[0]);
        client.setDy(d[1]);
        client.setTicksInDirection(TICKS_PER_DIRECTION);
      }

      int candidateX = client.getOriginX() + client.getDx();
      int candidateY = client.getOriginY() + client.getDy();
      int minX = client.getAnchorX() - MAX_DRIFT;
      int maxX = client.getAnchorX() + MAX_DRIFT;
      int minY = client.getAnchorY() - MAX_DRIFT;
      int maxY = client.getAnchorY() + MAX_DRIFT;

      if (candidateX < minX || candidateX > maxX) {
        client.setDx(-client.getDx());
        candidateX = client.getOriginX() + client.getDx();
      }
      if (candidateY < minY || candidateY > maxY) {
        client.setDy(-client.getDy());
        candidateY = client.getOriginY() + client.getDy();
      }

      client.setOriginX(candidateX);
      client.setOriginY(candidateY);
      client.setTicksInDirection(client.getTicksInDirection() - 1);
      newBlocks = computeView(candidateX, candidateY, client.getViewSide());
    }

    String[] toRemove = diff(oldBlocks, newBlocks);
    String[] toAdd = diff(newBlocks, oldBlocks);

    if (toRemove.length > 0 || toAdd.length > 0) {
      client.getConnection().sendClientUpdateRequest(toRemove, toAdd);
      oldBlocks.removeAll(Set.of(toRemove));
      for (String b : toAdd) {
        oldBlocks.add(b);
      }
    }
    client.setNextMoveAtMs(now + MOVE_INTERVAL_MS);
  }

  static Set<String> computeView(int originX, int originY, int side) {
    Set<String> blocks = new HashSet<>(side * side);
    for (int dy = 0; dy < side; dy++) {
      for (int dx = 0; dx < side; dx++) {
        blocks.add(BlockUtils.getKey(originX + dx, originY + dy));
      }
    }
    return blocks;
  }

  private static String[] diff(Set<String> a, Set<String> b) {
    Set<String> result = new HashSet<>(a);
    result.removeAll(b);
    return result.toArray(new String[0]);
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }
}
