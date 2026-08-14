package org.slobben.cellserviceload.adapter.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceApiWrapper {
  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${cell-service.url}")
  private String baseUrl;

  public Settings getNewClient() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<Settings> response =
        restTemplate.getForEntity("https://" + baseUrl + "/gen-api/settings", Settings.class);
    assert response.getBody() != null;
    log.info("ClientID: {}", response.getBody().clientId());
    return response.getBody();
  }
}
