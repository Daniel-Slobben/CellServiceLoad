package org.slobben.cellserviceload.adapter.websocket;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Summary of a binary block message from the server. The message holds blocks back to back until
 * the end, per block little-endian:
 *
 * <pre>
 * offset  type  field
 *  0      i32   x
 *  4      i32   y
 *  8      u8    type (0 = FULL, 1 = BORDER)
 *  9      u32   generation
 * 13      u32   encodedCells length
 * 17      ...   encodedCells (LZ4 block)
 * </pre>
 *
 * Only the headers are read; the load test never decompresses cells.
 */
public record BlockFrame(int bytes, int fullBlocks, int borderBlocks) {

  static final int BLOCK_HEADER_BYTES = 17;
  private static final int TYPE_OFFSET = 8;
  private static final int LENGTH_OFFSET = 13;
  private static final int FULL = 0;
  private static final int BORDER = 1;

  public static BlockFrame read(byte[] frame) {
    ByteBuffer buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
    int full = 0;
    int border = 0;
    for (int start = 0; start < frame.length; ) {
      if (start + BLOCK_HEADER_BYTES > frame.length) {
        throw new IllegalArgumentException("truncated block header at " + start);
      }
      long end = start + BLOCK_HEADER_BYTES + Integer.toUnsignedLong(buffer.getInt(start + LENGTH_OFFSET));
      if (end > frame.length) {
        throw new IllegalArgumentException("block payload at " + start + " runs past the frame");
      }
      switch (frame[start + TYPE_OFFSET]) {
        case FULL -> full++;
        case BORDER -> border++;
        default -> throw new IllegalArgumentException("unknown block type at " + start);
      }
      start = (int) end;
    }
    return new BlockFrame(frame.length, full, border);
  }
}
