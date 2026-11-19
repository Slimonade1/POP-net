package dk.pop.status.temp.view.status;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Component
public class ExampleData implements StatusSource {
    private static final List<String> SICK = List.of(
            "Answering slowly.",
            "One of the servers stopped answering, the others took over.",
            "Slow while a backup was running.",
            "High load after a software update.");

    private static final List<String> DOWN = List.of(
            "Not answering. Fixed by restarting the service.",
            "Not answering during planned maintenance.",
            "Not answering after a power cut in the server room.",
            "Not answering while a switch was replaced.");

    private final List<ServiceGroup> groups;

    public ExampleData() {
        ZonedDateTime start = ZonedDateTime.now(StatusPages.ZONE).truncatedTo(ChronoUnit.MINUTES);
        groups = List.of(
                new ServiceGroup("Network infrastructure", List.of(
                        service("Backup", null, start),
                        service("Internal DHCP", null, start),
                        service("K-Net RADIUS", null, start),
                        service("Network Monitor Status", null, start),
                        service("Internal RADIUS", null, start),
                        service("Locking System Server", null, start),
                        service("Management VPN", null, start))),
                new ServiceGroup("Servers", List.of(
                        service("Jane", 1, start),
                        service("John", 16, start))),
                new ServiceGroup("Web services", List.of(
                        service("POP Booking", 3, start),
                        service("POP Website", 97, start),
                        service("Network Monitor Service", 41, start),
                        withOngoing(service("POP Print", 18, start), new Incident(
                                start.minusMinutes(38), null, Status.SICK,
                                "Answering slowly. We are looking at the print server.")),
                        service("Self-service Redirection", 1, start),
                        withOngoing(service("Reset Password", 339, start), new Incident(
                                start.minusMinutes(12), null, Status.DOWN,
                                "Not answering since the last update. We are rolling it back.")),
                        service("Netgrp Password Manager", 1, start),
                        service("Netgrp Wiki", 10, start))));
    }

    @Override
    public List<ServiceGroup> groups(ZonedDateTime now) {
        return groups.stream()
                .map(group -> new ServiceGroup(group.name(), group.services().stream()
                        .map(service -> new ServiceView(null, service.name(), service.slug(), service.hasLatency(),
                                service.latencyMs(), service.incidents(), now))
                        .toList()))
                .toList();
    }

    @Override
    public List<Integer> latencies(ServiceView service, Range range, ZonedDateTime now) {
        List<ZonedDateTime> starts = range.starts(now);
        List<Integer> values = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            ZonedDateTime from = starts.get(i);
            ZonedDateTime to = i + 1 < starts.size() ? starts.get(i + 1) : now;
            values.add(latency(service, from, to, now));
        }
        return values;
    }

    @Override
    public Integer latencyNow(ServiceView service, ZonedDateTime now) {
        return latency(service, now.minusMinutes(5), now, now);
    }

    private static Integer latency(ServiceView service, ZonedDateTime from, ZonedDateTime to, ZonedDateTime now) {
        if (service.latencyMs() == null || !to.isAfter(from)) {
            return null;
        }
        double span = Duration.between(from, to).toSeconds();
        double down = 0;
        double sick = 0;
        for (Incident incident : service.incidents()) {
            long seconds = incident.overlap(from, to, now).toSeconds();
            if (incident.status() == Status.DOWN) {
                down += seconds;
            } else {
                sick += seconds;
            }
        }
        if (down * 2 >= span) {
            return null;
        }
        double busy = 1 + 0.35 * Math.max(0, Math.sin((from.getHour() - 8) / 24.0 * 2 * Math.PI));
        double noise = 0.85 + 0.3 * new Random(service.slug().hashCode() * 31L + from.toEpochSecond() / 60).nextDouble();
        return (int) Math.max(1, Math.round(service.latencyMs() * busy * noise * (1 + 7 * sick / span)));
    }

    private static ServiceView service(String name, Integer latencyMs, ZonedDateTime start) {
        return new ServiceView(null, name, ServiceView.slug(name), latencyMs != null, latencyMs, incidents(name, start), start);
    }

    private static ServiceView withOngoing(ServiceView service, Incident incident) {
        List<Incident> incidents = new ArrayList<>(service.incidents());
        incidents.add(incident);
        incidents.sort(Comparator.comparing(Incident::start));
        return new ServiceView(null, service.name(), service.slug(), service.hasLatency(), service.latencyMs(), incidents,
                service.checkedAt());
    }

    private static List<Incident> incidents(String name, ZonedDateTime start) {
        Random random = new Random(name.hashCode());
        List<Incident> incidents = new ArrayList<>();
        int count = 2 + random.nextInt(5);
        for (int i = 0; i < count; i++) {
            ZonedDateTime begin = start.minusMinutes(60 + random.nextInt(14 * 24 * 60 - 120));
            boolean down = random.nextInt(100) < 40;
            int minutes = down ? 5 + random.nextInt(70) : 15 + random.nextInt(200);
            incidents.add(new Incident(begin, begin.plusMinutes(minutes), down ? Status.DOWN : Status.SICK,
                    (down ? DOWN : SICK).get(random.nextInt(4))));
        }
        incidents.sort(Comparator.comparing(Incident::start));
        return incidents;
    }
}
