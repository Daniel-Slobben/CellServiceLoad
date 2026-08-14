package org.slobben.cellserviceload.traffic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.adapter.rest.ServiceApiWrapper;
import org.slobben.cellserviceload.adapter.rest.Settings;
import org.slobben.cellserviceload.adapter.websocket.WebSocketClientConnection;
import org.slobben.cellserviceload.utils.BlockUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoadScheduler {
  private static final int MAX_CLIENTS = 350;
  private static final int CLIENT_GROWTH_RATE_MS = 200;

  private final List<Client> clientList = new ArrayList<>();

  private final ServiceApiWrapper serviceApiWrapper;
  @Value("${cell-service.url}")
  private String url;

  @Scheduled(fixedDelay = CLIENT_GROWTH_RATE_MS)
  public void addClient() throws ExecutionException, InterruptedException, TimeoutException {
    log.info("current amount of clients: {}", clientList.size()) ;
    if (clientList.size() > MAX_CLIENTS) {
      return;
    }
    Settings settings = serviceApiWrapper.getNewClient();

    WebSocketClientConnection webSocketClientConnection = new WebSocketClientConnection(settings.clientId(), url);

    final int initBlockX = settings.chaosHit().worldX() / settings.blockSize();
    final int initBlockY = settings.chaosHit().worldY() / settings.blockSize();
    int blockX = initBlockX;
    int blockY = initBlockY;

    int xLength = 2;
    String[] initialBlocksToAdd = new String[9];
    for (int i = 0; i < initialBlocksToAdd.length; i++) {
      if (i % xLength == 0) {
        blockX = initBlockX;
        blockY++;
      }
      initialBlocksToAdd[i] = BlockUtils.getKey(blockX, blockY);
      blockX++;
    }

    Client client = new Client(settings.clientId(), Arrays.asList(initialBlocksToAdd), webSocketClientConnection);
    client.webSocketClientConnection().sendClientUpdateRequest(null, initialBlocksToAdd);

    clientList.add(client);
  }
}
