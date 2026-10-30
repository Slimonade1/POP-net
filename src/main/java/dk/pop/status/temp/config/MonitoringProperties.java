package dk.pop.status.temp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;

@ConfigurationProperties("monitoring")
public record MonitoringProperties(List<ServiceCheck> services) {
    public MonitoringProperties {
        services = services == null ? List.of() : List.copyOf(services);
        var ids = new HashSet<String>();
        for (ServiceCheck service : services) {
            if (!ids.add(service.id())) {
                throw new IllegalArgumentException("Duplicate monitoring service ID: " + service.id());
            }
        }
    }

    public record ServiceCheck(String id, String name, List<String> command,
                               Duration interval, Duration timeout, Boolean enabled) {
        public ServiceCheck {
            if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,99}")) {
                throw new IllegalArgumentException("Monitoring IDs must be 1-100 lowercase letters, digits, _ or -");
            }
            name = name == null ? id : name;
            if (name.isBlank() || name.length() > 255) {
                throw new IllegalArgumentException("Invalid monitoring service name: " + id);
            }
            if (command == null || command.isEmpty() || command.stream().anyMatch(s -> s == null || s.isBlank())) {
                throw new IllegalArgumentException("A command and arguments are required for: " + id);
            }
            command = List.copyOf(command);
            interval = interval == null ? Duration.ofSeconds(60) : interval;
            timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
            if (interval.compareTo(Duration.ofSeconds(1)) < 0 || interval.compareTo(Duration.ofDays(1)) > 0
                    || timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofMinutes(5)) > 0) {
                throw new IllegalArgumentException("Interval must be 1s-1d and timeout 100ms-5m for: " + id);
            }
            enabled = enabled == null || enabled;
        }
    }
}
