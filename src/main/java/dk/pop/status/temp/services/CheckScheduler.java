package dk.pop.status.temp.services;

import dk.pop.status.temp.config.MonitoringProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CheckScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(CheckScheduler.class);
    private final MonitoringProperties properties;
    private final ScriptCheckService checks;
    private final StatusLogService logs;
    private final Map<String, Long> nextChecks = new HashMap<>();
    private volatile boolean ready;

    public CheckScheduler(MonitoringProperties properties, ScriptCheckService checks, StatusLogService logs) {
        this.properties = properties;
        this.checks = checks;
        this.logs = logs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ready = true;
        LOG.info("Monitoring configured with {} enabled service(s)",
                properties.services().stream().filter(MonitoringProperties.ServiceCheck::enabled).count());
    }

    // One sequential worker: no overlapping checks, and each interval starts at completion.
    @Scheduled(fixedDelay = 1000)
    public void checkDueServices() {
        if (!ready) {
            return;
        }
        for (var service : properties.services()) {
            Long next = nextChecks.get(service.id());
            if (!service.enabled() || (next != null && System.nanoTime() - next < 0)) {
                continue;
            }
            try {
                var result = checks.check(service);
                logs.createLog(result);
                LOG.info("Check {}: {} ({} ms)", service.id(), result.status(), result.durationMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // A failed insert must not stop checks for the remaining services.
                LOG.error("Could not check or save result for {}", service.id(), e);
            } finally {
                nextChecks.put(service.id(), System.nanoTime() + service.interval().toNanos());
            }
        }
    }
}
