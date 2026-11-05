package dk.pop.status.temp.view.status;

import dk.pop.status.temp.domain.StatusLog;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiveDataTest {
    private static StatusLog log(String status, int hour, int minute, String message) {
        return new StatusLog(null, "Print server", status, LocalTime.of(hour, minute), LocalDate.of(2026, 11, 5),
                message, 100);
    }

    @Test
    void aDownStateAndTheNextHealthyStateMakeOneIncident() {
        List<Incident> incidents = LiveData.incidents(List.of(
                log("healthy", 9, 0, "HTTP 200 OK"),
                log("down", 10, 0, "HTTP Fault: 503"),
                log("down", 10, 5, "Timeout Overload"),
                log("healthy", 10, 30, "HTTP 200 OK")));

        assertThat(incidents).hasSize(1);
        Incident incident = incidents.getFirst();
        assertThat(incident.status()).isEqualTo(Status.DOWN);
        assertThat(incident.summary()).isEqualTo("Not answering.");
        assertThat(incident.end()).isNotNull();
        assertThat(java.time.Duration.between(incident.start(), incident.end()).toMinutes()).isEqualTo(30);
    }

    @Test
    void diagnosticMessagesStayOutOfPublicIncidents() {
        List<Incident> incidents = LiveData.incidents(List.of(log("down", 10, 0, "Private diagnostic: connection refused")));

        assertThat(incidents.getFirst().summary()).doesNotContain("Private diagnostic");
    }

    @Test
    void aServiceThatIsStillDownHasAnOngoingIncident() {
        List<Incident> incidents = LiveData.incidents(List.of(
                log("down", 8, 0, "Packet Loss Timeout Drop"),
                log("healthy", 8, 10, ""),
                log("down", 11, 0, "")));

        assertThat(incidents).hasSize(2);
        assertThat(incidents.getLast().ongoing()).isTrue();
        assertThat(incidents.getLast().summary()).isEqualTo("Not answering.");
    }

    @Test
    void aServiceWithOnlyHealthyChecksHasNoIncidents() {
        assertThat(LiveData.incidents(List.of(log("healthy", 9, 0, "HTTP 200 OK")))).isEmpty();
    }
}
