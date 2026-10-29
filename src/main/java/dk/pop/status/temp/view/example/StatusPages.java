package dk.pop.status.temp.view.example;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class StatusPages {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);
    private static final Range DEFAULT = Range.WEEK;

    private final ExampleData data;

    public StatusPages(ExampleData data) {
        this.data = data;
    }

    public record Row(String name, String slug, Status status, String note, String noteId, List<String> cells,
                      String uptime, String uptimes, String patterns, String incidents) {
    }

    public record GroupRows(String name, List<Row> rows) {
    }

    public record Entry(String id, String when, String start, String end, String service, String slug,
                        Status status, String length, String summary) {
    }

    public record Day(String label, List<Entry> entries) {
    }

    public void home(Model model) {
        ZonedDateTime now = data.now();
        List<GroupRows> groups = data.groups().stream()
                .map(group -> new GroupRows(group.name(), group.services().stream().map(s -> row(s, now)).toList()))
                .toList();
        List<Row> problems = groups.stream()
                .flatMap(group -> group.rows().stream())
                .filter(row -> row.status() != Status.WORKING)
                .sorted(Comparator.comparing(Row::status).reversed())
                .toList();

        ZonedDateTime oldest = Range.TWO_WEEKS.starts(now).getFirst();
        record Found(ExampleService service, Incident incident) {
        }
        List<Entry> entries = data.groups().stream()
                .flatMap(group -> group.services().stream())
                .flatMap(service -> service.incidents().stream()
                        .filter(incident -> !incident.overlap(oldest, now, now).isZero())
                        .map(incident -> new Found(service, incident)))
                .sorted(Comparator.comparing((Found found) -> found.incident().start()).reversed())
                .map(found -> entry(found.service(), found.incident(), now, TIME))
                .toList();

        model.addAttribute("groups", groups);
        model.addAttribute("overall", Status.worst(problems.stream().map(Row::status).toList()));
        model.addAttribute("headline", headline(problems));
        model.addAttribute("checkedAt", TIME.format(now));
        model.addAttribute("days", days(entries, now));
        addCommon(model, now);
    }

    public boolean service(String slug, Model model) {
        Optional<ExampleService> found = data.find(slug);
        if (found.isEmpty()) {
            return false;
        }
        ExampleService service = found.get();
        ZonedDateTime now = data.now();
        Row row = row(service, now);
        Integer latencyNow = ExampleData.latency(service, now.minusMinutes(5), now, now);

        Map<String, Object> values = new LinkedHashMap<>();
        for (Range range : Range.values()) {
            values.put(range.key(), ExampleData.latencies(service, range, now));
        }

        model.addAttribute("service", service);
        model.addAttribute("row", row);
        model.addAttribute("nowText", nowText(row.status(), latencyNow));
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
        model.addAttribute("period", DEFAULT.period());
        model.addAttribute("axisStart", DEFAULT.axisStart(now));
    }

    private static Row row(ExampleService service, ZonedDateTime now) {
        Map<String, Object> patterns = new LinkedHashMap<>();
        Map<String, Object> uptimes = new LinkedHashMap<>();
        for (Range range : Range.values()) {
            patterns.put(range.key(), ExampleData.pattern(service, range, now));
            uptimes.put(range.key(), ExampleData.uptime(service, range, now));
        }
        List<String> cells = ExampleData.pattern(service, DEFAULT, now).chars()
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
        Optional<Incident> ongoing = ExampleData.ongoing(service);
        return new Row(service.name(), service.slug(), ExampleData.current(service),
                ongoing.map(Incident::summary).orElse(null),
                ongoing.map(incident -> anchor(service, incident)).orElse(null), cells,
                ExampleData.uptime(service, DEFAULT, now), Json.of(uptimes), Json.of(patterns), Json.of(incidents));
    }

    private static Entry entry(ExampleService service, Incident incident, ZonedDateTime now, DateTimeFormatter when) {
        long minutes = Math.max(1, Duration.between(incident.start(), incident.endOr(now)).toMinutes());
        String length = minutes < 60 ? minutes + " min"
                : minutes / 60 + " h" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + " min");
        return new Entry(anchor(service, incident), when.format(incident.start()), iso(incident.start()),
                iso(incident.endOr(now)),
                service.name(), service.slug(), incident.status(),
                "for " + length + (incident.ongoing() ? " so far" : ""), incident.summary());
    }

    private static String anchor(ExampleService service, Incident incident) {
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

    private static String headline(List<Row> problems) {
        if (problems.isEmpty()) {
            return "Everything is working.";
        }
        boolean allDown = problems.stream().allMatch(row -> row.status() == Status.DOWN);
        if (problems.size() == 1) {
            return problems.getFirst().name() + (allDown ? " is down." : " is having problems.");
        }
        return problems.size() + (allDown ? " services are down." : " services are having problems.");
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
