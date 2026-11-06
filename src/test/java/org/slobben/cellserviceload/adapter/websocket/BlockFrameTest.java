package org.slobben.cellserviceload.adapter.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class BlockFrameTest {

  @Test
  void countsFullAndBorderBlocks() {
    byte[] frame = frame(block(0, 3), block(1, 0), block(1, 5));

    assertEquals(new BlockFrame(frame.length, 1, 2), BlockFrame.read(frame));
  }

  @Test
  void emptyFrameHasNoBlocks() {
    assertEquals(new BlockFrame(0, 0, 0), BlockFrame.read(new byte[0]));
  }

  @Test
  void rejectsTruncatedFrames() {
    byte[] frame = frame(block(0, 4));

    assertThrows(IllegalArgumentException.class, () -> BlockFrame.read(Arrays.copyOf(frame, frame.length - 1)));
    assertThrows(IllegalArgumentException.class, () -> BlockFrame.read(Arrays.copyOf(frame, 10)));
  }

  // Same layout as the backend's BlockFrame.write
  private static byte[] block(int type, int cellBytes) {
    return ByteBuffer.allocate(BlockFrame.BLOCK_HEADER_BYTES + cellBytes)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(-3)
        .putInt(7)
        .put((byte) type)
        .putInt(42)
        .putInt(cellBytes)
        .put(new byte[cellBytes])
        .array();
  }

  private static byte[] frame(byte[]... blocks) {
    ByteBuffer buffer = ByteBuffer.allocate(Arrays.stream(blocks).mapToInt(b -> b.length).sum());
    for (byte[] block : blocks) {
      buffer.put(block);
    }
    return buffer.array();
  }
}
