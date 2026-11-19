package dk.pop.status.temp.view.status;

import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.domain.ServiceEntity;
import dk.pop.status.temp.repository.ResponseTimeRepository;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import dk.pop.status.temp.repository.StatusLogRepository;
import dk.pop.status.temp.services.StatusMonitoringService;
import dk.pop.status.temp.utils.ServiceSlugUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LiveDataTest {
    @Test
    void sameNamedServicesKeepTheirOwnHistoryAndUseTheSharedServiceUrls() {
        var services = mock(ServiceEntityRepository.class);
        var logs = mock(StatusLogRepository.class);
        var responseTimes = mock(ResponseTimeRepository.class);
        var monitoring = mock(StatusMonitoringService.class);
        var first = new ServiceEntity(1L, "Print server", "https://first.example.test", 3);
        var second = new ServiceEntity(2L, "Print server", "https://second.example.test", 3);
        var allServices = List.of(second, first);
        var now = ZonedDateTime.of(2026, 11, 5, 12, 0, 0, 0, StatusPages.ZONE);
        var from = now.toLocalDate().minusDays(15);
        when(services.findAll()).thenReturn(allServices);
        when(logs.findSinceByServiceId(1L, from)).thenReturn(List.of(log("down", 10, 0, "Private diagnostic")));
        when(logs.findSinceByServiceId(2L, from)).thenReturn(List.of(log("healthy", 10, 0, "OK")));
        when(monitoring.getLastChecked()).thenReturn(Map.of(1L, now.toInstant(), 2L, now.minusSeconds(5).toInstant()));
        when(monitoring.getLiveLatencies()).thenReturn(Map.of(1L, 100, 2L, 200));

        var views = new LiveData(services, logs, responseTimes, monitoring).groups(now).stream()
                .flatMap(group -> group.services().stream())
                .collect(Collectors.toMap(ServiceView::id, Function.identity()));

        assertThat(views.get(1L).current()).isEqualTo(Status.DOWN);
        assertThat(views.get(2L).current()).isEqualTo(Status.WORKING);
        assertThat(views.get(1L).latencyMs()).isEqualTo(100);
        assertThat(views.get(2L).latencyMs()).isEqualTo(200);
        assertThat(views.get(1L).checkedAt()).isEqualTo(now);
        assertThat(views.get(2L).checkedAt()).isEqualTo(now.minusSeconds(5));
        var slugs = ServiceSlugUtils.slugs(allServices);
        assertThat(views.get(1L).slug()).isEqualTo(slugs.get(1L));
        assertThat(views.get(2L).slug()).isEqualTo(slugs.get(2L)).isNotEqualTo(views.get(1L).slug());
        verify(logs).findLastBeforeByServiceId(1L, from);
        verify(logs).findLastBeforeByServiceId(2L, from);
        verify(logs, never()).findSince(any());
        verify(logs, never()).findLastBefore(any());
    }

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
