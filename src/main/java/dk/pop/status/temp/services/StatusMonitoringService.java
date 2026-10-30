package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.ServiceEntity;
import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import dk.pop.status.temp.repository.StatusLogRepository;
import dk.pop.status.temp.utils.Log;
import dk.pop.status.temp.utils.StatusMessageUtils;

import org.slf4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
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
    
    // Injected as an ObjectProvider to handle the custom @RequestScope transaction boundaries safely
    private final ObjectProvider<StatusLogRepository> statusLogRepositoryProvider;
    
    private final Map<String, String> liveStatuses = new ConcurrentHashMap<>();
    private final Map<String, Integer> liveLatencies = new ConcurrentHashMap<>();
    private final Map<String, String> previousStatuses = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public StatusMonitoringService(ServiceEntityRepository serviceRepository, ObjectProvider<StatusLogRepository> statusLogRepositoryProvider) {
        this.serviceRepository = serviceRepository;
        this.statusLogRepositoryProvider = statusLogRepositoryProvider;
    }

    @Scheduled(fixedDelay = 20000)
    public void monitorInfrastructure() {
        Iterable<ServiceEntity> monitoredServices = serviceRepository.findAll();
        
        // Universal clean stream row counter mapping across all Java compiler variants
        long taskCount = java.util.stream.StreamSupport.stream(monitoredServices.spliterator(), false).count();
        if (taskCount == 0) return;

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ExecutorService executor = Executors.newFixedThreadPool(Math.min((int) taskCount, 15));

        for (ServiceEntity service : monitoredServices) {
            String targetPath = service.description(); 
            if (targetPath == null || targetPath.isBlank()) continue;

            if (targetPath.startsWith("icmp://")) {
                executor.submit(() -> checkIcmp(service, targetPath, isWindows));
            } else if (targetPath.startsWith("http://") || targetPath.startsWith("https://")) {
                executor.submit(() -> checkHttp(service, targetPath));
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
        String currentStatus;
        String diagnosticMessage;

        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 400) {
                currentStatus = "healthy";
                diagnosticMessage = "HTTP " + statusCode + " OK";
            } else {
                currentStatus = "down";
                diagnosticMessage = "HTTP Fault: " + statusCode;
            }
        } catch (java.net.http.HttpConnectTimeoutException e) {
            currentStatus = "sick";
            diagnosticMessage = "Timeout Overload";
        } catch (Exception e) {
            currentStatus = "down";
            diagnosticMessage = e.getMessage() != null ? e.getMessage() : "Connection Fault";
        }

        int durationMs = (int) ((System.nanoTime() - startTime) / 1_000_000);
        saveLogEntry(service, currentStatus, diagnosticMessage, durationMs);
    }

    private void checkIcmp(ServiceEntity service, String targetPath, boolean isWindows) {
        long startTime = System.nanoTime();
        String cleanHost = targetPath.replace("icmp://", "").replaceAll("/$", "");
        
        // Safety step for bracketed IPv6 literal addresses
        if (cleanHost.startsWith("[") && cleanHost.endsWith("]")) {
            cleanHost = cleanHost.substring(1, cleanHost.length() - 1);
        }

        String currentStatus;
        String diagnosticMessage;

        try {
            ProcessBuilder pb = isWindows 
                ? new ProcessBuilder("ping", "-n", "1", "-w", "1000", cleanHost)
                : new ProcessBuilder("ping", "-c", "1", "-W", "1", cleanHost);

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                currentStatus = "healthy";
                diagnosticMessage = "ICMP Alive Check Success";
            } else {
                currentStatus = "down";
                diagnosticMessage = "Packet Loss Timeout Drop";
            }
        } catch (Exception e) {
            currentStatus = "down";
            diagnosticMessage = e.getMessage() != null ? e.getMessage() : "Runtime Drop";
        }

        int durationMs = (int) ((System.nanoTime() - startTime) / 1_000_000);
        saveLogEntry(service, currentStatus, diagnosticMessage, durationMs);
    }

    private void saveLogEntry(ServiceEntity service, String currentStatus, String msg, int latency) {
        liveStatuses.put(service.name(), currentStatus);
        liveLatencies.put(service.name(), latency);
        
        String lastKnownStatus = previousStatuses.get(service.name());

        // State Change Detection Pattern
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

            // Maps the dedicated status_log table structure
            StatusLog logRecord = new StatusLog(
                null,                   
                service.name(),         // Feeds the Name string for their custom finder lookups
                currentStatus,          
                LocalTime.now(),        // time()
                LocalDate.now(),        // date()
                sanitizedMessage,       
                latency                 // delay_ms
            );

            // RELATIONAL LIFECYCLE MANAGED TRANSACTION BLOCK
            StatusLogRepository repo = statusLogRepositoryProvider.getObject();
            try {
                repo.begin();               // Opens a clean connection transaction layer
                repo.create(logRecord);     // Fires prepared statement matrices
                repo.commit();              // Safely persists modifications to MySQL
                previousStatuses.put(service.name(), currentStatus);
            } catch (Exception e) {
                try { repo.rollback(); } catch (Exception rbEx) { log.atError().log("UnitOfWork Rollback failed: " + rbEx.getMessage()); }
                throw Log.giveUp(log, e, "Fatal status log data persistence infrastructure fault through UnitOfWork context");
            } finally {
                try { repo.close(); } catch (Exception clEx) { log.atError().log("UnitOfWork Connection close failed: " + clEx.getMessage()); }
            }
        }
    }

    public Map<String, String> getLiveStatuses() { return liveStatuses; }
    public Map<String, Integer> getLiveLatencies() { return liveLatencies; }
}
