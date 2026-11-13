package org.slobben.cellserviceload.adapter.rest;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ServiceApiWrapper {
  private final RestTemplate restTemplate;

  @Value("${cell-service.http-url}")
  private String baseUrl;

  public ServiceApiWrapper() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(5));
    this.restTemplate = new RestTemplate(factory);
  }

  public Settings getSettings() {
    return Objects.requireNonNull(
        restTemplate.getForObject(baseUrl + "/gen-api/settings", Settings.class));
  }

  public Coordinates getViewport() {
    return Objects.requireNonNull(
        restTemplate.postForObject(baseUrl + "/gen-api/viewport", List.of(), Coordinates.class));
  }
}
