package dk.pop.status.temp.view.status;

import dk.pop.status.temp.domain.ServiceEntity;
import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.repository.ResponseTimeRepository;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import dk.pop.status.temp.repository.StatusLogRepository;
import dk.pop.status.temp.services.StatusMonitoringService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

@Component
public class LiveData implements StatusSource {
    private static final String NETWORK = "Network and servers";
    private static final String WEB = "Web services";
    private static final String OTHER = "Other services";
    private static final String NOT_ANSWERING = "Not answering.";

    private final ServiceEntityRepository services;
    private final StatusLogRepository logs;
    private final ResponseTimeRepository responseTimes;
    private final StatusMonitoringService monitoring;

    public LiveData(ServiceEntityRepository services, StatusLogRepository logs, ResponseTimeRepository responseTimes,
                    StatusMonitoringService monitoring) {
        this.services = services;
        this.logs = logs;
        this.responseTimes = responseTimes;
        this.monitoring = monitoring;
    }

    @Override
    public List<ServiceGroup> groups(ZonedDateTime now) {
        Map<String, List<StatusLog>> history = new LinkedHashMap<>();
        for (StatusLog log : logs.findSince(now.toLocalDate().minusDays(15))) {
            history.computeIfAbsent(log.service_name(), name -> new ArrayList<>()).add(log);
        }

        Map<String, List<ServiceView>> groups = new LinkedHashMap<>();
        groups.put(NETWORK, new ArrayList<>());
        groups.put(WEB, new ArrayList<>());
        groups.put(OTHER, new ArrayList<>());
        StreamSupport.stream(services.findAll().spliterator(), false)
                .sorted(Comparator.comparing(ServiceEntity::id))
                .forEach(service -> groups.get(group(service))
                        .add(view(service, history.getOrDefault(service.name(), List.of()))));

        return groups.entrySet().stream()
                .filter(group -> !group.getValue().isEmpty())
                .map(group -> new ServiceGroup(group.getKey(), group.getValue()))
                .toList();
    }

    @Override
    public List<Integer> latencies(ServiceView service, Range range, ZonedDateTime now) {
        List<ZonedDateTime> starts = range.starts(now);
        List<Integer> values = new ArrayList<>(starts.size());
        if (service.id() == null || !service.hasLatency()) {
            return values;
        }
        Map<Integer, Integer> averages = responseTimes.averages(service.id(), local(starts.getFirst()), local(now),
                range.step().toSeconds());
        for (int i = 0; i < starts.size(); i++) {
            values.add(averages.get(i));
        }
        return values;
    }

    @Override
    public Integer latencyNow(ServiceView service, ZonedDateTime now) {
        return service.hasLatency() ? service.latencyMs() : null;
    }

    private ServiceView view(ServiceEntity service, List<StatusLog> history) {
        Instant checked = monitoring.getLastChecked().get(service.name());
        return new ServiceView(service.id(), service.name(), ServiceView.slug(service.name()),
                isWeb(service), monitoring.getLiveLatencies().get(service.name()), incidents(history),
                checked == null ? null : checked.atZone(StatusPages.ZONE));
    }

    static List<Incident> incidents(List<StatusLog> history) {
        List<Incident> incidents = new ArrayList<>();
        ZonedDateTime start = null;
        for (StatusLog log : history) {
            if (log.date() == null || log.time() == null) {
                continue;
            }
            ZonedDateTime at = LocalDateTime.of(log.date(), log.time())
                    .atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(StatusPages.ZONE);
            boolean down = "down".equalsIgnoreCase(log.status());
            if (down && start == null) {
                start = at;
            } else if (!down && start != null) {
                incidents.add(new Incident(start, at, Status.DOWN, NOT_ANSWERING));
                start = null;
            }
        }
        if (start != null) {
            incidents.add(new Incident(start, null, Status.DOWN, NOT_ANSWERING));
        }
        return incidents;
    }

    private static String group(ServiceEntity service) {
        String target = target(service);
        if (target.startsWith("icmp://")) {
            return NETWORK;
        }
        return isWeb(service) ? WEB : OTHER;
    }

    private static boolean isWeb(ServiceEntity service) {
        String target = target(service);
        return target.startsWith("http://") || target.startsWith("https://");
    }

    private static String target(ServiceEntity service) {
        return service.description() == null ? "" : service.description().strip();
    }

    private static LocalDateTime local(ZonedDateTime time) {
        return time.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
