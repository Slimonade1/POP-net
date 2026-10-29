package dk.pop.status.temp.view.example;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum Range {
    DAY("day", "past day", 1, 24, "EEE HH:mm"),
    WEEK("week", "past week", 4, 42, "EEE d MMM"),
    TWO_WEEKS("2weeks", "past 2 weeks", 8, 42, "EEE d MMM");

    private final String key;
    private final String period;
    private final int hours;
    private final int count;
    private final DateTimeFormatter axis;

    Range(String key, String period, int hours, int count, String axis) {
        this.key = key;
        this.period = period;
        this.hours = hours;
        this.count = count;
        this.axis = DateTimeFormatter.ofPattern(axis, Locale.ENGLISH);
    }

    public String key() {
        return key;
    }

    public String period() {
        return period;
    }

    public List<ZonedDateTime> starts(ZonedDateTime now) {
        LocalDateTime local = now.minusNanos(1).toLocalDateTime();
        LocalDateTime last = local.withHour(local.getHour() - local.getHour() % hours)
                .withMinute(0).withSecond(0).withNano(0);
        List<ZonedDateTime> starts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            starts.add(last.minusHours((long) hours * (count - 1 - i)).atZone(now.getZone()));
        }
        return starts;
    }

    public String axisStart(ZonedDateTime now) {
        return axis.format(starts(now).getFirst());
    }

    public Duration length(ZonedDateTime now) {
        return Duration.between(starts(now).getFirst(), now);
    }
}
