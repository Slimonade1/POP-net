package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.ServiceEntity;
import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.repository.ResponseTimeRepository;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import dk.pop.status.temp.repository.StatusLogRepository;
import dk.pop.status.temp.utils.Log;
import dk.pop.status.temp.utils.StatusMessageUtils;

import org.slf4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class StatusMonitoringService {

    private static final Logger log = Log.getLogger(StatusMonitoringService.class);

    private final ServiceEntityRepository serviceRepository;
    private final StatusLogRepository statusLogRepository;
    private final HealthStatusTracker healthStatusTracker;
    private final EmailNotificationService emailNotificationService;
    private final ResponseTimeRepository responseTimes;
    
    private final Map<String, String> liveStatuses = new ConcurrentHashMap<>();
    private final Map<String, Integer> liveLatencies = new ConcurrentHashMap<>();
    private final Map<String, String> previousStatuses = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastChecked = new ConcurrentHashMap<>();
    private boolean rememberedLastStatuses;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    // Standard constructor injection setup
    public StatusMonitoringService(
            ServiceEntityRepository serviceRepository,
            StatusLogRepository statusLogRepository,
            HealthStatusTracker healthStatusTracker,
            EmailNotificationService emailNotificationService,
            ResponseTimeRepository responseTimes) {
        this.serviceRepository = serviceRepository;
        this.statusLogRepository = statusLogRepository;
        this.healthStatusTracker = healthStatusTracker;
        this.emailNotificationService = emailNotificationService;
        this.responseTimes = responseTimes;
    }

    @Scheduled(fixedDelay = 20000)
    public void monitorInfrastructure() {
        rememberLastStatuses();
        Iterable<ServiceEntity> monitoredServices = serviceRepository.findAll();
        
        long taskCount = java.util.stream.StreamSupport.stream(monitoredServices.spliterator(), false).count();
        if (taskCount == 0) return;

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ExecutorService executor = Executors.newFixedThreadPool(Math.min((int) taskCount, 15));

        for (ServiceEntity service : monitoredServices) {
            String targetPath = service.description(); 
            if (targetPath == null || targetPath.isBlank()) continue;

            if (service.isIcmp()) {
                executor.submit(() -> checkIcmp(service, targetPath, isWindows));
            } else if (service.isWeb()) {
                executor.submit(() -> checkHttp(service, targetPath));
            } else if (service.isScript()) {
                executor.submit(() -> checkScript(service, targetPath));
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkHttp(ServiceEntity service, String url) {
        long startTime = System.nanoTime();
        boolean healthy;
        String diagnosticMessage;

        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 400) {
                healthy = true;
                diagnosticMessage = "HTTP " + statusCode + " OK";
            } else {
                healthy = false;
                diagnosticMessage = "HTTP Fault: " + statusCode;
            }
        } catch (java.net.http.HttpConnectTimeoutException e) {
            healthy = false;
            diagnosticMessage = "Timeout Overload";
        } catch (Exception e) {
            healthy = false;
            diagnosticMessage = e.getMessage() != null ? e.getMessage() : "Connection Fault";
        }

        int durationMs = (int) ((System.nanoTime() - startTime) / 1_000_000);
        if (healthy) {
            saveResponseTime(service, durationMs);
        }
        saveLogEntry(service, healthStatusTracker.recordResult(service, healthy), diagnosticMessage, durationMs);
        if (!healthy) {
            liveLatencies.remove(service.name());
        }
    }

    private void rememberLastStatuses() {
        if (rememberedLastStatuses) {
            return;
        }
        try {
            for (Map.Entry<Long, StatusLog> last : statusLogRepository.findLatestByService().entrySet()) {
                previousStatuses.put(last.getValue().service_name(), last.getValue().status());
                healthStatusTracker.remember(last.getKey(), last.getValue().status());
            }
            rememberedLastStatuses = true;
        } catch (RuntimeException e) {
            log.atError().log("Could not read the last known statuses: " + e.getMessage());
        }
    }

    private void saveResponseTime(ServiceEntity service, int durationMs) {
        try {
            responseTimes.add(service.id(), LocalDateTime.now(), durationMs);
        } catch (RuntimeException e) {
            log.atError().log("Could not save response time for " + service.name() + ": " + e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 3600000)
    public void forgetOldResponseTimes() {
        responseTimes.deleteBefore(LocalDateTime.now().minusDays(15));
    }

    private void checkIcmp(ServiceEntity service, String targetPath, boolean isWindows) {
        long startTime = System.nanoTime();
        String cleanHost = targetPath.replace("icmp://", "").replaceAll("/$", "");
        
        if (cleanHost.startsWith("[") && cleanHost.endsWith("]")) {
            cleanHost = cleanHost.substring(1, cleanHost.length() - 1);
        }

        boolean healthy;
        String diagnosticMessage;

        try {
            String os = System.getProperty("os.name").toLowerCase();
            boolean isMac = os.contains("mac");
            boolean isIpv6 = cleanHost.contains(":");

            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder("ping", "-n", "1", "-w", "1000", cleanHost);
            } else if (isMac && isIpv6) {
                pb = new ProcessBuilder("ping6", "-c", "1", cleanHost);
            } else if (isMac) {
                pb = new ProcessBuilder("ping", "-c", "1", "-t", "1", cleanHost);
            } else {
                pb = new ProcessBuilder("ping", "-c", "1", "-W", "1", cleanHost);
            }

            Process process = pb.start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            int exitCode = finished ? process.exitValue() : -1;

            if (exitCode == 0) {
                healthy = true;
                diagnosticMessage = "ICMP Alive Check Success";
            } else {
                healthy = false;
                diagnosticMessage = "Packet Loss Timeout Drop";
            }
        } catch (Exception e) {
            healthy = false;
            diagnosticMessage = e.getMessage() != null ? e.getMessage() : "Runtime Drop";
        }

        int durationMs = (int) ((System.nanoTime() - startTime) / 1_000_000);
        saveLogEntry(service, healthStatusTracker.recordResult(service, healthy), diagnosticMessage, durationMs);
    }

    private void checkScript(ServiceEntity service, String targetPath) {
        long startTime = System.nanoTime();
        String scriptPath = targetPath.replace("script://", "");

        boolean healthy;
        String diagnosticMessage;

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", scriptPath);
            Process process = pb.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);   // waits at most 10 seconds

            if (!finished) {                // If not finished in 10 seconds it kills script and counts as down
                process.destroyForcibly();
                healthy = false;
                diagnosticMessage = "Script timed out";
            } else {
                String firstLine;       // script finished in time, reading first line of output
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    firstLine = reader.readLine();
                }
                if (process.exitValue() == 0) {     // exit code 0 = healthy
                    healthy = true;
                    diagnosticMessage = firstLine != null ? firstLine : "Script OK";
                } else {
                    healthy = false;         // every other exit code = down
                    diagnosticMessage = firstLine != null ? firstLine : "Script exited with code " + process.exitValue();
                }
            }
        } catch (Exception e) {
            healthy = false;
            diagnosticMessage = e.getMessage() != null ? e.getMessage() : "Script could not run";
        }

        int durationMs = (int) ((System.nanoTime() - startTime) / 1_000_000);       // how long it took in ms
        saveLogEntry(service, healthStatusTracker.recordResult(service, healthy), diagnosticMessage, durationMs);     // saving result
    }

    private void saveLogEntry(ServiceEntity service, String currentStatus, String msg, int latency) {
        liveStatuses.put(service.name(), currentStatus);
        liveLatencies.put(service.name(), latency);
        lastChecked.put(service.name(), Instant.now());
        
        String lastKnownStatus = previousStatuses.get(service.name());

        if (lastKnownStatus == null || !lastKnownStatus.equalsIgnoreCase(currentStatus)) {
            log.atInfo().log(String.format("STATE CHANGE DETECTED -> Service '%s' shifted to [%s]", service.name(), currentStatus));

            String sanitizedMessage;
            try {
                sanitizedMessage = StatusMessageUtils.normalize(msg);
            } catch (Exception e) {
                sanitizedMessage = msg.length() > StatusMessageUtils.MAX_MESSAGE_LENGTH 
                    ? msg.substring(0, StatusMessageUtils.MAX_MESSAGE_LENGTH) 
                    : "Status Fault Change Trigger";
            }

            StatusLog logRecord = new StatusLog(
                null,                   
                service.name(),         
                currentStatus,          
                LocalTime.now(),        // maps to time
                LocalDate.now(),        // maps to date
                sanitizedMessage,       
                latency                 // delay_ms
            );

            try {
                statusLogRepository.begin();           
                statusLogRepository.create(logRecord);  
                statusLogRepository.commit();          
                previousStatuses.put(service.name(), currentStatus);

                if (lastKnownStatus != null) {
                    emailNotificationService.notifyStatusChange(
                            service.name(), lastKnownStatus, currentStatus, sanitizedMessage);
                }
            } catch (Exception e) {
                try { statusLogRepository.rollback(); } catch (Exception rbEx) { log.atError().log("UnitOfWork Rollback failed: " + rbEx.getMessage()); }
                throw Log.giveUp(log, e, "Fatal status log data persistence infrastructure fault through Singleton context");
            } finally {
                try { statusLogRepository.close(); } catch (Exception clEx) { log.atError().log("UnitOfWork Connection close failed: " + clEx.getMessage()); }
            }
        }
    }

    public Map<String, String> getLiveStatuses() { return liveStatuses; }
    public Map<String, Integer> getLiveLatencies() { return liveLatencies; }
    public Map<String, Instant> getLastChecked() { return lastChecked; }
}
