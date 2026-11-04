package org.slobben.cellserviceload.traffic;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ClientRegistry {
  private final ConcurrentHashMap<UUID, Client> clients = new ConcurrentHashMap<>();

  public void add(Client client) {
    clients.put(client.getId(), client);
  }

  public void remove(UUID id) {
    clients.remove(id);
  }

  public int size() {
    return clients.size();
  }

  public Collection<Client> all() {
    return clients.values();
  }
}
