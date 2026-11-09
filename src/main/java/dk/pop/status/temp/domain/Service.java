package dk.pop.status.temp.domain;

import java.time.LocalDate;
import java.time.LocalTime;

public record Service(
    String name,
    String description
) { }
