package org.slobben.cellserviceload.adapter.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.slobben.cellserviceload.adapter.websocket.StatusResponse;
import tools.jackson.databind.json.JsonMapper;

/** JSON as the backend writes it. */
class DtoMappingTest {
  private static final JsonMapper MAPPER = new JsonMapper();

  @Test
  void readsSettings() {
    Settings settings =
        MAPPER.readValue(
            """
            {"blockSize":2000,"viewport":{"x":-4100,"y":250},"targetSpeed":250}
            """,
            Settings.class);

    assertEquals(2000, settings.blockSize());
    assertEquals(new Coordinates(-4100, 250), settings.viewport());
  }

  @Test
  void readsStatusResponse() {
    assertEquals(
        StatusResponse.Type.NO_BLOCKS,
        MAPPER.readValue("{\"type\":\"NO_BLOCKS\"}", StatusResponse.class).type());
  }
}
