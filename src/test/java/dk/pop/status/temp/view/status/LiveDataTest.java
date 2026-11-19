package dk.pop.status.temp.view.status;

import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.repository.ResponseTimeRepository;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import dk.pop.status.temp.repository.StatusLogRepository;
import dk.pop.status.temp.services.StatusMonitoringService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void statusStripLatencyUsesTheLatestSavedReadingInEachTimeSlice() {
        ZonedDateTime now = ZonedDateTime.parse("2026-11-05T12:30:00+01:00[Europe/Copenhagen]");
        List<ZonedDateTime> starts = Range.DAY.starts(now);
        ServiceView service = new ServiceView(1L, "Print server", "print-server", false, null, List.of(), now,
                List.of(new ServiceView.LatencySample(starts.get(0).plusMinutes(5), 80),
                        new ServiceView.LatencySample(starts.get(0).plusMinutes(10), 95),
                        new ServiceView.LatencySample(starts.get(1), 110)));

        List<Integer> latencies = service.statusLatencies(Range.DAY, now);

        assertThat(latencies).hasSize(starts.size());
        assertThat(latencies.get(0)).isEqualTo(95);
        assertThat(latencies.get(1)).isEqualTo(110);
        assertThat(latencies.get(2)).isEqualTo(110);
    }

    @Test
    void statusStripCarriesForwardTheLatestSavedLatencyIntoLaterSlices() {
        ZonedDateTime now = ZonedDateTime.parse("2026-11-05T12:30:00+01:00[Europe/Copenhagen]");
        List<ZonedDateTime> starts = Range.DAY.starts(now);
        ServiceView service = new ServiceView(1L, "Print server", "print-server", false, null, List.of(), now,
                List.of(new ServiceView.LatencySample(starts.getFirst().minusHours(1), 80)));

        List<Integer> latencies = service.statusLatencies(Range.DAY, now);

        assertThat(latencies).containsOnly(80);
    }

    @Test
    void serviceGraphUsesSavedResponseTimeSamples() {
        ResponseTimeRepository responseTimes = mock(ResponseTimeRepository.class);
        ZonedDateTime now = ZonedDateTime.parse("2026-11-05T12:30:00+01:00[Europe/Copenhagen]");
        ZonedDateTime start = Range.DAY.starts(now).getFirst();
        when(responseTimes.hours(eq(1L), any(), any())).thenReturn(List.of(
                new ResponseTimeRepository.Hour(start.plusHours(9).toLocalDateTime(), 200, 2),
                new ResponseTimeRepository.Hour(start.plusHours(10).toLocalDateTime(), 150, 1)));
        LiveData liveData = new LiveData(mock(ServiceEntityRepository.class), mock(StatusLogRepository.class),
                responseTimes, mock(StatusMonitoringService.class));
        ServiceView service = new ServiceView(1L, "Ping target", "ping-target", true, 150,
                List.of(), now, List.of());

        List<Integer> values = liveData.latencies(service, Range.DAY, now);

        assertThat(values).hasSize(24);
        assertThat(values.get(9)).isEqualTo(100);
        assertThat(values.get(10)).isEqualTo(150);
    }
}
