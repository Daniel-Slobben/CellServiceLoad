package org.slobben.cellserviceload.traffic;

import java.util.HashSet;
import java.util.Set;

import org.slobben.cellserviceload.utils.BlockUtils;

/** The frontend's grid view: offsets in cells, zoom as screen pixels per cell. */
public class Camera {
  static final double DEFAULT_CELL_SIZE = 2.5;
  // The frontend's zoom-out limit: as many cells as a 4K screen at 0.07 px per cell, ~420 blocks of 2000.
  static final double MAX_CELLS_IN_VIEW = (3840 / 0.07) * (2160 / 0.07);
  // The backend's sectors, in cells.
  static final int SECTOR_CELLS = 50;
  static final double MAX_CELL_SIZE = 30;

  private Device device;
  private final int blockSize;
  private double cellSize = DEFAULT_CELL_SIZE;
  private double cellOffsetX;
  private double cellOffsetY;

  public Camera(Device device, int blockSize) {
    this.device = device;
    this.blockSize = blockSize;
  }

  public double cellSize() {
    return cellSize;
  }

  public Device device() {
    return device;
  }

  /** Another screen size around the same centre, like resizing the window. */
  public void setDevice(Device device) {
    double centerX = cellOffsetX + (this.device.width / cellSize) / 2;
    double centerY = cellOffsetY + (this.device.height / cellSize) / 2;
    this.device = device;
    cellSize = Math.max(cellSize, minCellSize());
    centerOn(centerX, centerY);
  }

  public double minCellSize() {
    return Math.sqrt(device.width * device.height / MAX_CELLS_IN_VIEW);
  }

  public void centerOn(double worldX, double worldY) {
    cellOffsetX = worldX - (device.width / cellSize) / 2;
    cellOffsetY = worldY - (device.height / cellSize) / 2;
  }

  /** A drag of the given screen pixels; dragging right moves the view left. */
  public void pan(double dxPixels, double dyPixels) {
    cellOffsetX -= dxPixels / cellSize;
    cellOffsetY -= dyPixels / cellSize;
  }

  /** Zooms keeping the world point under the screen point in place. Out of range zooms are ignored. */
  public void zoomAt(double screenX, double screenY, double zoomFactor) {
    double newCellSize = cellSize * zoomFactor;
    if (newCellSize < minCellSize() || newCellSize > MAX_CELL_SIZE) {
      return;
    }
    double worldX = cellOffsetX + screenX / cellSize;
    double worldY = cellOffsetY + screenY / cellSize;
    cellSize = newCellSize;
    cellOffsetX = worldX - screenX / cellSize;
    cellOffsetY = worldY - screenY / cellSize;
  }

  /** The block range the frontend requests, as {fromX, toX, fromY, toY} inclusive. */
  public int[] blockRange() {
    // Copied from updateVisibleBlocks: one ring of blocks when in the first half of a block.
    int ringX = Math.floor(cellOffsetX % blockSize) < blockSize / 2.0 ? 1 : 0;
    int ringY = Math.floor(cellOffsetY % blockSize) < blockSize / 2.0 ? 1 : 0;

    int startBlockX = (int) Math.floor(cellOffsetX / blockSize);
    int startBlockY = (int) Math.floor(cellOffsetY / blockSize);
    int endBlockX = (int) Math.floor((cellOffsetX + device.width / cellSize) / blockSize);
    int endBlockY = (int) Math.floor((cellOffsetY + device.height / cellSize) / blockSize);

    return new int[] {startBlockX - ringX, endBlockX + ringX, startBlockY - ringY, endBlockY + ringY};
  }

  /** How wide a sector is on screen, which decides sectors. */
  public double sectorPixels() {
    return cellSize * SECTOR_CELLS;
  }

  public static Set<String> blocksIn(int[] range) {
    Set<String> blocks = new HashSet<>();
    for (int x = range[0]; x <= range[1]; x++) {
      for (int y = range[2]; y <= range[3]; y++) {
        blocks.add(BlockUtils.getKey(x, y));
      }
    }
    return blocks;
  }
}
