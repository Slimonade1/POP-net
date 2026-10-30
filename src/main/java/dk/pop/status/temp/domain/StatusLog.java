package dk.pop.status.temp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;
import java.time.LocalTime;

@Table("status_log")
public record StatusLog(
    @Id Long id,
    Long serviceId,      // Foreign Key pointing to the service item being checked
    String status,       // Text string holding: "healthy", "sick", or "down"
    LocalTime time,      // Split clock runtime tracker
    LocalDate date,      // Split calendar date tracking property
    String message,      // The troubleshooting error message or network info string
    Integer delayMs      // Network response delay/latency in milliseconds
) {}
