package org.slobben.cellserviceload.adapter.rest;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class ServiceApiWrapper {
  private final RestTemplate restTemplate;

  @Value("${cell-service.url}")
  private String baseUrl;

  public ServiceApiWrapper() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(5));
    this.restTemplate = new RestTemplate(factory);
  }

  public Settings getNewClient() {
    ResponseEntity<Settings> response =
        restTemplate.getForEntity("https://" + baseUrl + "/gen-api/settings", Settings.class);
    assert response.getBody() != null;
    return response.getBody();
  }
}
