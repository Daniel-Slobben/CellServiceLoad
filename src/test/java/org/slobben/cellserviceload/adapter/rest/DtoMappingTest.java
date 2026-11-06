package org.slobben.cellserviceload.adapter.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slobben.cellserviceload.adapter.websocket.HealthCheckResponse;
import tools.jackson.databind.json.JsonMapper;

/** JSON as the backend writes it. */
class DtoMappingTest {
  private static final JsonMapper MAPPER = new JsonMapper();
  private static final String CLIENT_ID = "0f8fad5b-d9cb-469f-a165-70867728950e";

  @Test
  void readsSettings() {
    Settings settings =
        MAPPER.readValue(
            """
            {"blockSize":2000,"clientId":"%s","viewport":{"x":-4100,"y":250}}
            """
                .formatted(CLIENT_ID),
            Settings.class);

    assertEquals(2000, settings.blockSize());
    assertEquals(UUID.fromString(CLIENT_ID), settings.clientId());
    assertEquals(new Coordinates(-4100, 250), settings.viewport());
  }

  @Test
  void readsReconnectResponseWithoutViewport() {
    ReconnectResponse response =
        MAPPER.readValue(
            """
            {"clientId":"%s","viewport":null}
            """.formatted(CLIENT_ID),
            ReconnectResponse.class);

    assertEquals(UUID.fromString(CLIENT_ID), response.clientId());
    assertNull(response.viewport());
  }

  @Test
  void readsHealthCheckResponse() {
    assertEquals(
        HealthCheckResponse.Type.SESSION_DEAD,
        MAPPER.readValue("{\"type\":\"SESSION_DEAD\"}", HealthCheckResponse.class).type());
  }
}
