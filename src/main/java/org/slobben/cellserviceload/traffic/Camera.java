package org.slobben.cellserviceload.traffic;

/** The frontend's grid view: offsets in cells, zoom as screen pixels per cell. */
public class Camera {
  static final double DEFAULT_CELL_SIZE = 2.5;
  // The frontend's zoom-out limit: a block of 2000 cells at 8 px.
  static final double MIN_CELL_SIZE = 0.004;
  // The backend's sectors, in cells.
  static final int SECTOR_CELLS = 50;
  // A big screen watches sectors once its view holds this many blocks, like the frontend.
  static final int MAX_CELL_BLOCKS = 250;
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
    centerOn(centerX, centerY);
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
    if (newCellSize < MIN_CELL_SIZE || newCellSize > MAX_CELL_SIZE) {
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

  /** At or below this sectorPixels the frontend watches sectors: 1 px, or earlier once the view holds 250 blocks. */
  public double sectorSwitchPixels() {
    double budgetPixels = Math.sqrt((double) device.width * device.height / MAX_CELL_BLOCKS) * SECTOR_CELLS / blockSize;
    return Math.max(ViewportMover.SECTOR_PIXELS, budgetPixels);
  }
}
