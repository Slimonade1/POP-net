package dk.pop.status.temp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Table("status_logs")
public record StatusLog(
    @Id Long id,
    String service_name,        // Foreign Key pointing to the service item being checked
    String status,              // Text string holding: "healthy", "sick", or "down"
    LocalTime time,             // Split clock runtime tracker
    LocalDate date,             // Split calendar date tracking property
    String message,             // The troubleshooting error message or network info string
    Integer delay_ms            // Network response delay/latency in milliseconds
) {
    public String day() {
        return date == null ? "Unknown date" : date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH));
    }

    public String clock() {
        return time == null ? "Unknown time" : time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}