package dk.pop.status.temp.view.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Component
public class ExampleData {
    public static final ZoneId ZONE = ZoneId.of("Europe/Copenhagen");

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

    private final List<ExampleGroup> groups;

    public ExampleData(@Value("${example-data:true}") boolean enabled) {
        if (!enabled) {
            groups = List.of();
            return;
        }
        ZonedDateTime start = ZonedDateTime.now(ZONE).truncatedTo(ChronoUnit.MINUTES);
        groups = List.of(
                new ExampleGroup("Network infrastructure", List.of(
                        service("Backup", null, start),
                        service("Internal DHCP", null, start),
                        service("K-Net RADIUS", null, start),
                        service("Network Monitor Status", null, start),
                        service("Internal RADIUS", null, start),
                        service("Locking System Server", null, start),
                        service("Management VPN", null, start))),
                new ExampleGroup("Servers", List.of(
                        service("Jane", 1, start),
                        service("John", 16, start))),
                new ExampleGroup("Web services", List.of(
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

    public List<ExampleGroup> groups() {
        return groups;
    }

    public Optional<ExampleService> find(String slug) {
        return groups.stream()
                .flatMap(group -> group.services().stream())
                .filter(service -> service.slug().equals(slug))
                .findFirst();
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(ZONE).truncatedTo(ChronoUnit.MINUTES);
    }

    public static Status current(ExampleService service) {
        return Status.worst(service.incidents().stream()
                .filter(Incident::ongoing)
                .map(Incident::status)
                .toList());
    }

    public static Optional<Incident> ongoing(ExampleService service) {
        return service.incidents().stream().filter(Incident::ongoing).findFirst();
    }

    public static String pattern(ExampleService service, Range range, ZonedDateTime now) {
        List<ZonedDateTime> starts = range.starts(now);
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < starts.size(); i++) {
            ZonedDateTime from = starts.get(i);
            ZonedDateTime to = i + 1 < starts.size() ? starts.get(i + 1) : now;
            pattern.append(Status.worst(service.incidents().stream()
                    .filter(incident -> !incident.overlap(from, to, now).isZero())
                    .map(Incident::status)
                    .toList()).code());
        }
        return pattern.toString();
    }

    public static String uptime(ExampleService service, Range range, ZonedDateTime now) {
        ZonedDateTime from = range.starts(now).getFirst();
        long down = service.incidents().stream()
                .filter(incident -> incident.status() == Status.DOWN)
                .mapToLong(incident -> incident.overlap(from, now, now).toSeconds())
                .sum();
        double percent = 100.0 * (1 - (double) down / range.length(now).toSeconds());
        return percent >= 100 ? "100 %" : String.format(java.util.Locale.ROOT, "%.2f %%", percent);
    }

    public static List<Integer> latencies(ExampleService service, Range range, ZonedDateTime now) {
        List<ZonedDateTime> starts = range.starts(now);
        List<Integer> values = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            ZonedDateTime from = starts.get(i);
            ZonedDateTime to = i + 1 < starts.size() ? starts.get(i + 1) : now;
            values.add(latency(service, from, to, now));
        }
        return values;
    }

    public static Integer latency(ExampleService service, ZonedDateTime from, ZonedDateTime to, ZonedDateTime now) {
        if (!service.hasLatency() || !to.isAfter(from)) {
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

    private static ExampleService service(String name, Integer latencyMs, ZonedDateTime start) {
        return new ExampleService(name, slug(name), latencyMs, incidents(name, start));
    }

    private static ExampleService withOngoing(ExampleService service, Incident incident) {
        List<Incident> incidents = new ArrayList<>(service.incidents());
        incidents.add(incident);
        incidents.sort(Comparator.comparing(Incident::start));
        return new ExampleService(service.name(), service.slug(), service.latencyMs(), incidents);
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

    private static String slug(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
