package org.slobben.cellserviceload.traffic;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskExecutors {

  @Bean
  public Executor virtualThreadExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }
}
