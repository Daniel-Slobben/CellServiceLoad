package org.slobben.cellserviceload.traffic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Expected ranges worked out by hand from the frontend's updateVisibleBlocks. */
class CameraTest {
  private static final int BLOCK_SIZE = 2000;

  @Test
  void desktopAtDefaultZoomInSecondHalfOfABlockHasNoRing() {
    Camera camera = new Camera(Device.DESKTOP, BLOCK_SIZE);
    // 1920x1050 px at 2.5 px/cell is 768x420 cells, offset (1616, 1290)
    camera.centerOn(2000, 1500);

    assertArrayEquals(new int[] {0, 1, 0, 0}, camera.blockRange());
  }

  @Test
  void firstHalfOfABlockAddsARingOnBothSides() {
    Camera camera = new Camera(Device.PHONE, BLOCK_SIZE);
    // 412x785 px is 164.8x314 cells, offset (417.6, 343)
    camera.centerOn(500, 500);

    assertArrayEquals(new int[] {-1, 1, -1, 1}, camera.blockRange());
  }

  @Test
  void negativeOffsetsAlwaysGetTheRingLikeJavascriptRemainder() {
    Camera camera = new Camera(Device.PHONE, BLOCK_SIZE);
    // offset (-1582.4, -157); -1582.4 % 2000 is negative, so below half a block
    camera.centerOn(-1500, 0);

    assertArrayEquals(new int[] {-2, 0, -2, 1}, camera.blockRange());
  }

  @Test
  void zoomedAllTheWayOutOnDesktopWatchesAboutAHundredBlocks() {
    Camera camera = new Camera(Device.DESKTOP, BLOCK_SIZE);
    camera.centerOn(0, 0);
    while (camera.cellSize() * 0.95 >= Camera.MIN_CELL_SIZE) {
      camera.zoomAt(960, 525, 0.95);
    }
    int[] range = camera.blockRange();
    int blocks = (range[1] - range[0] + 1) * (range[3] - range[2] + 1);

    assertEquals(Camera.blocksIn(range).size(), blocks);
    assertEquals(true, blocks >= 80 && blocks <= 120, "blocks: " + blocks);
  }
}
