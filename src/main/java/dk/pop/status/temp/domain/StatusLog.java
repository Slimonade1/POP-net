package dk.pop.status.temp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalTime;

public record StatusLog(
        Long id,
        String service_name,
        String status,
        LocalTime checkedAtTime,
        LocalDate checkedAtDate,
        Integer delay_ms,
        String message
) { }
