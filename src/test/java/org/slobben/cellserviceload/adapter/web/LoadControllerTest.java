package org.slobben.cellserviceload.adapter.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A run with no clients, so nothing connects to a backend. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoadControllerTest {
  private static final String CONFIG =
      """
      {"clients":0,"rampUpPerSecond":2,"durationMinutes":0,"phoneShare":0.7,"zoomedOutShare":0.15,"reconnectThresholdMs":%d}
      """;
  private static final JsonMapper MAPPER = new JsonMapper();
  private final HttpClient http = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Test
  void startsUpdatesAndStopsARun() throws Exception {
    assertEquals(200, post("start", CONFIG.formatted(4000)).statusCode());
    assertEquals(409, post("start", CONFIG.formatted(4000)).statusCode());

    HttpResponse<String> bad = post("update", CONFIG.formatted(100));
    assertEquals(400, bad.statusCode());
    assertTrue(bad.body().contains("watchdog"), bad.body());

    assertEquals(200, post("update", CONFIG.formatted(3000)).statusCode());
    JsonNode live = MAPPER.readTree(get("live").body());
    assertEquals(0, live.get("clients").get("clients").asInt());
    int runId = live.get("runId").asInt();

    assertEquals(200, post("stop", "").statusCode());
    JsonNode run = MAPPER.readTree(get("runs").body()).get(0);
    assertEquals(runId, run.get("id").asInt());
    assertEquals(3000, run.get("config").get("reconnectThresholdMs").asInt());
    assertFalse(run.get("running").asBoolean());
    assertEquals("stopped", run.get("endReason").asString());
    assertTrue(MAPPER.readTree(get("live").body()).get("runId").isNull());
  }

  private HttpResponse<String> get(String path) throws Exception {
    return http.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, String json) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + "/api/" + path);
  }
}
