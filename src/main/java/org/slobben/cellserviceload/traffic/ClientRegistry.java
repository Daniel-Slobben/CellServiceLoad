package org.slobben.cellserviceload.traffic;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ClientRegistry {
  private final Set<Client> clients = ConcurrentHashMap.newKeySet();

  public void add(Client client) {
    clients.add(client);
  }

  public int size() {
    return clients.size();
  }

  public Collection<Client> all() {
    return clients;
  }
}
