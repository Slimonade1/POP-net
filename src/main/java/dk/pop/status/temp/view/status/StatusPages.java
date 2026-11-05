package dk.pop.status.temp.view.status;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class StatusPages {
    public static final ZoneId ZONE = ZoneId.of("Europe/Copenhagen");
    private static final Duration STALE = Duration.ofMinutes(2);
    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);
    private static final Range DEFAULT = Range.WEEK;

    private final StatusSource data;
    private final boolean example;

    public StatusPages(ExampleData exampleData, LiveData liveData, @Value("${example-data:false}") boolean example) {
        this.data = example ? exampleData : liveData;
        this.example = example;
    }

    public record Row(String name, String slug, Status status, String note, String noteId, List<String> cells,
                      String uptime, String uptimes, String patterns, String incidents, String checked) {
    }

    public record GroupRows(String name, List<Row> rows) {
    }

    public record Entry(String id, String when, String start, String end, String service, String slug,
                        Status status, String length, String summary) {
    }

    public record Day(String label, List<Entry> entries) {
    }

    public record Problem(String name, String slug, Status status, String since, String summary, String anchor) {
    }

    public void home(Model model) {
        ZonedDateTime now = now();
        List<ServiceGroup> sources = data.groups(now);
        List<GroupRows> groups = sources.stream()
                .map(group -> new GroupRows(group.name(), group.services().stream().map(s -> row(s, now)).toList()))
                .toList();
        List<Row> problems = groups.stream()
                .flatMap(group -> group.rows().stream())
                .filter(row -> row.status() != Status.WORKING)
                .sorted(Comparator.comparing(Row::status).reversed())
                .toList();

        ZonedDateTime oldest = Range.TWO_WEEKS.starts(now).getFirst();
        record Found(ServiceView service, Incident incident) {
        }
        List<Entry> entries = sources.stream()
                .flatMap(group -> group.services().stream())
                .flatMap(service -> service.incidents().stream()
                        .filter(incident -> !incident.overlap(oldest, now, now).isZero())
                        .map(incident -> new Found(service, incident)))
                .sorted(Comparator.comparing((Found found) -> found.incident().start()).reversed())
                .map(found -> entry(found.service(), found.incident(), now, TIME))
                .toList();

        List<Problem> details = sources.stream()
                .flatMap(group -> group.services().stream())
                .flatMap(service -> service.ongoing().stream().map(incident -> problem(service, incident, now)))
                .sorted(Comparator.comparing(Problem::status).reversed())
                .toList();

        model.addAttribute("groups", groups);
        model.addAttribute("problems", details);
        model.addAttribute("overall", Status.worst(problems.stream().map(Row::status).toList()));
        model.addAttribute("headline", headline(problems));
        model.addAttribute("checkedAt", lastCheck(sources).map(SECONDS::format).orElse(null));
        model.addAttribute("days", days(entries, now));
        addCommon(model, now);
    }

    public boolean service(String slug, Model model) {
        ZonedDateTime now = now();
        Optional<ServiceView> found = data.groups(now).stream()
                .flatMap(group -> group.services().stream())
                .filter(service -> service.slug().equals(slug))
                .findFirst();
        if (found.isEmpty()) {
            return false;
        }
        ServiceView service = found.get();
        Row row = row(service, now);
        Integer latencyNow = data.latencyNow(service, now);

        Map<String, Object> values = new LinkedHashMap<>();
        for (Range range : Range.values()) {
            values.put(range.key(), data.latencies(service, range, now));
        }

        model.addAttribute("service", service);
        model.addAttribute("row", row);
        model.addAttribute("nowText", nowText(row.status(), latencyNow));
        model.addAttribute("checkedText", checkedText(service.checkedAt(), now));
        model.addAttribute("values", Json.of(values));
        model.addAttribute("incidents", service.incidents().stream()
                .sorted(Comparator.comparing(Incident::start).reversed())
                .map(incident -> entry(service, incident, now, DAY_TIME))
                .toList());
        addCommon(model, now);
        return true;
    }

    private void addCommon(Model model, ZonedDateTime now) {
        Map<String, Object> ranges = new LinkedHashMap<>();
        for (Range range : Range.values()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("period", range.period());
            info.put("from", iso(range.starts(now).getFirst()));
            info.put("axisStart", range.axisStart(now));
            info.put("starts", range.starts(now).stream().map(StatusPages::iso).toList());
            ranges.put(range.key(), info);
        }
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("now", iso(now));
        page.put("ranges", ranges);

        model.addAttribute("pageData", Json.of(page));
        model.addAttribute("example", example);
        model.addAttribute("period", DEFAULT.period());
        model.addAttribute("axisStart", DEFAULT.axisStart(now));
    }

    public static ZonedDateTime now() {
        return ZonedDateTime.now(ZONE).truncatedTo(ChronoUnit.MINUTES);
    }

    private static Optional<ZonedDateTime> lastCheck(List<ServiceGroup> groups) {
        return groups.stream()
                .flatMap(group -> group.services().stream())
                .map(ServiceView::checkedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder());
    }

    private static String checkedText(ZonedDateTime checked, ZonedDateTime now) {
        if (checked == null) {
            return "Not checked yet since the monitor started.";
        }
        long seconds = Math.max(0, Duration.between(checked, ZonedDateTime.now(ZONE)).toSeconds());
        String ago = seconds < 60 ? seconds + " s ago" : seconds / 60 + " min ago";
        return "Last checked at " + SECONDS.format(checked) + ", " + ago + ".";
    }

    private static String checked(ServiceView service) {
        if (service.checkedAt() == null) {
            return "Not checked yet";
        }
        return "Checked at " + SECONDS.format(service.checkedAt());
    }

    private static boolean stale(ServiceView service) {
        return service.checkedAt() != null
                && Duration.between(service.checkedAt(), ZonedDateTime.now(ZONE)).compareTo(STALE) > 0;
    }

    private static Row row(ServiceView service, ZonedDateTime now) {
        Map<String, Object> patterns = new LinkedHashMap<>();
        Map<String, Object> uptimes = new LinkedHashMap<>();
        for (Range range : Range.values()) {
            patterns.put(range.key(), service.pattern(range, now));
            uptimes.put(range.key(), service.uptime(range, now));
        }
        List<String> cells = service.pattern(DEFAULT, now).chars()
                .mapToObj(code -> code == 'd' ? "down" : code == 's' ? "sick" : "operational")
                .toList();
        List<Map<String, Object>> incidents = service.incidents().stream().map(incident -> {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("start", iso(incident.start()));
            json.put("end", incident.end() == null ? null : iso(incident.end()));
            json.put("status", String.valueOf(incident.status().code()));
            json.put("summary", incident.summary());
            return json;
        }).toList();
        Optional<Incident> ongoing = service.ongoing();
        String note = ongoing.map(Incident::summary)
                .orElse(stale(service) ? "Not checked since " + TIME.format(service.checkedAt()) + "." : null);
        return new Row(service.name(), service.slug(), service.current(), note,
                ongoing.map(incident -> anchor(service, incident)).orElse(null), cells,
                service.uptime(DEFAULT, now), Json.of(uptimes), Json.of(patterns), Json.of(incidents),
                checked(service));
    }

    private static Entry entry(ServiceView service, Incident incident, ZonedDateTime now, DateTimeFormatter when) {
        long minutes = Math.max(1, Duration.between(incident.start(), incident.endOr(now)).toMinutes());
        String length = minutes < 60 ? minutes + " min"
                : minutes / 60 + " h" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + " min");
        return new Entry(anchor(service, incident), when.format(incident.start()), iso(incident.start()),
                iso(incident.endOr(now)),
                service.name(), service.slug(), incident.status(),
                "for " + length + (incident.ongoing() ? " so far" : ""), incident.summary());
    }

    private static String anchor(ServiceView service, Incident incident) {
        return "incident-" + service.slug() + "-" + incident.start().toEpochSecond() / 60;
    }

    private static List<Day> days(List<Entry> entries, ZonedDateTime now) {
        Map<String, List<Entry>> byDay = new LinkedHashMap<>();
        for (Entry entry : entries) {
            ZonedDateTime start = ZonedDateTime.parse(entry.start());
            String label = start.toLocalDate().equals(now.toLocalDate()) ? "Today"
                    : start.toLocalDate().equals(now.toLocalDate().minusDays(1)) ? "Yesterday"
                    : DAY.format(start);
            byDay.computeIfAbsent(label, key -> new ArrayList<>()).add(entry);
        }
        return byDay.entrySet().stream().map(e -> new Day(e.getKey(), e.getValue())).toList();
    }

    private static Problem problem(ServiceView service, Incident incident, ZonedDateTime now) {
        long minutes = Math.max(1, Duration.between(incident.start(), now).toMinutes());
        String length = minutes < 60 ? minutes + " min"
                : minutes / 60 + " h" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + " min");
        return new Problem(service.name(), service.slug(), incident.status(),
                "Since " + TIME.format(incident.start()) + ", " + length, incident.summary(), anchor(service, incident));
    }

    private static String headline(List<Row> problems) {
        if (problems.isEmpty()) {
            return "Everything is working.";
        }
        List<String> parts = problems.stream()
                .map(row -> row.name() + (row.status() == Status.DOWN ? " is down" : " is having problems"))
                .toList();
        if (parts.size() == 1) {
            return parts.getFirst() + ".";
        }
        if (parts.size() == 2) {
            return parts.get(0) + " and " + parts.get(1) + ".";
        }
        return problems.getFirst().name() + " and " + (problems.size() - 1) + " other services have problems.";
    }

    private static String nowText(Status status, Integer latency) {
        if (status == Status.DOWN) {
            return ", not answering.";
        }
        return latency == null ? "." : ", answering in " + latency + " ms.";
    }

    private static String iso(ZonedDateTime time) {
        return time.toOffsetDateTime().toString();
    }
}
