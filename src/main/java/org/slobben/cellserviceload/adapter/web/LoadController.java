package org.slobben.cellserviceload.adapter.web;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.slobben.cellserviceload.traffic.LoadScheduler;
import org.slobben.cellserviceload.traffic.Report;
import org.slobben.cellserviceload.traffic.Report.ClientCounts;
import org.slobben.cellserviceload.traffic.Run;
import org.slobben.cellserviceload.traffic.RunConfig;
import org.slobben.cellserviceload.traffic.TrafficReporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The API behind the page in static/index.html. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LoadController {
  private final LoadScheduler loadScheduler;
  private final TrafficReporter trafficReporter;

  @Value("${cell-service.http-url}")
  private String httpUrl;

  @Value("${cell-service.ws-url}")
  private String wsUrl;

  public record Setup(String httpUrl, String wsUrl, RunConfig defaults) {}

  /** The clients now, plus the reports of the current run from {@code reports} on. */
  public record Live(ClientCounts clients, Integer runId, List<Report> newReports) {}

  @GetMapping("/setup")
  public Setup setup() {
    return new Setup(httpUrl, wsUrl, loadScheduler.defaults());
  }

  @GetMapping("/runs")
  public List<Run> runs() {
    return loadScheduler.runs();
  }

  @GetMapping("/live")
  public Live live(@RequestParam(defaultValue = "0") int reports) {
    Run run = loadScheduler.current();
    if (run == null) {
      return new Live(trafficReporter.countClients(), null, List.of());
    }
    List<Report> all = run.getReports();
    return new Live(trafficReporter.countClients(), run.getId(), all.subList(Math.min(reports, all.size()), all.size()));
  }

  @PostMapping("/start")
  public Run start(@RequestBody RunConfig config) {
    return loadScheduler.start(config);
  }

  @PostMapping("/update")
  public Run update(@RequestBody RunConfig config) {
    return loadScheduler.update(config);
  }

  @PostMapping("/stop")
  public void stop() {
    loadScheduler.stop("stopped");
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<String> conflict(IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
  }

  // RunConfig's checks end up here
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<String> badConfig(HttpMessageNotReadableException e) {
    return ResponseEntity.badRequest().body(e.getMostSpecificCause().getMessage());
  }
}
