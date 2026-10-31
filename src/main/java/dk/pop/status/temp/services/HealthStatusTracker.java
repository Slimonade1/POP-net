package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.ServiceEntity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HealthStatusTracker {
    private final Map<Long, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<Long, String> effectiveStatuses = new ConcurrentHashMap<>();

    public String recordResult(ServiceEntity service, boolean healthy) {
        if (healthy) {
            consecutiveFailures.remove(service.id());
            effectiveStatuses.put(service.id(), "healthy");
            return "healthy";
        }

        int failures = consecutiveFailures.merge(service.id(), 1, Integer::sum);
        int threshold = Math.max(1, service.retryThreshold());
        if (failures >= threshold) {
            effectiveStatuses.put(service.id(), "down");
        }

        return effectiveStatuses.getOrDefault(service.id(), "healthy");
    }
}
