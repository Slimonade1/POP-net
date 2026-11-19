package dk.pop.status.temp.view.status;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record ServiceView(Long id, String name, String slug, boolean hasLatency, Integer latencyMs,
                          List<Incident> incidents, ZonedDateTime checkedAt, List<LatencySample> latencySamples) {

    public record LatencySample(ZonedDateTime checkedAt, int latencyMs) {
    }

    public Status current() {
        return Status.worst(incidents.stream().filter(Incident::ongoing).map(Incident::status).toList());
    }

    public Optional<Incident> ongoing() {
        return incidents.stream().filter(Incident::ongoing).findFirst();
    }

    public String pattern(Range range, ZonedDateTime now) {
        List<ZonedDateTime> starts = range.starts(now);
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < starts.size(); i++) {
            ZonedDateTime from = starts.get(i);
            ZonedDateTime to = i + 1 < starts.size() ? starts.get(i + 1) : now;
            pattern.append(Status.worst(incidents.stream()
                    .filter(incident -> !incident.overlap(from, to, now).isZero())
                    .map(Incident::status)
                    .toList()).code());
        }
        return pattern.toString();
    }

    public String uptime(Range range, ZonedDateTime now) {
        ZonedDateTime from = range.starts(now).getFirst();
        long down = incidents.stream()
                .filter(incident -> incident.status() == Status.DOWN)
                .mapToLong(incident -> incident.overlap(from, now, now).toSeconds())
                .sum();
        double percent = 100.0 * (1 - (double) down / range.length(now).toSeconds());
        return percent >= 100 ? "100 %" : String.format(Locale.ROOT, "%.2f %%", percent);
    }

    public List<Integer> statusLatencies(Range range, ZonedDateTime now) {
        List<ZonedDateTime> starts = range.starts(now);
        List<Integer> values = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            ZonedDateTime to = i + 1 < starts.size() ? starts.get(i + 1) : now;
            values.add(latencySamples.stream()
                    .filter(sample -> sample.checkedAt().isBefore(to))
                    .max(Comparator.comparing(LatencySample::checkedAt))
                    .map(LatencySample::latencyMs)
                    .orElse(null));
        }
        return values;
    }

    public static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
