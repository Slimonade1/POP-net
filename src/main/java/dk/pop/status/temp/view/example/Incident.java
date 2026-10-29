package dk.pop.status.temp.view.example;

import java.time.Duration;
import java.time.ZonedDateTime;

public record Incident(ZonedDateTime start, ZonedDateTime end, Status status, String summary) {

    public boolean ongoing() {
        return end == null;
    }

    public ZonedDateTime endOr(ZonedDateTime now) {
        return end == null ? now : end;
    }

    public Duration overlap(ZonedDateTime from, ZonedDateTime to, ZonedDateTime now) {
        ZonedDateTime a = start.isAfter(from) ? start : from;
        ZonedDateTime b = endOr(now).isBefore(to) ? endOr(now) : to;
        return b.isAfter(a) ? Duration.between(a, b) : Duration.ZERO;
    }
}
