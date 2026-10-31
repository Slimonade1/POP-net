package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.ServiceEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthStatusTrackerTest {
    private final HealthStatusTracker tracker = new HealthStatusTracker();
    private final ServiceEntity service = new ServiceEntity(1L, "Test service", "https://example.test", 3);

    @Test
    void reportsDownOnlyAfterConfiguredConsecutiveFailures() {
        assertThat(tracker.recordResult(service, false)).isEqualTo("healthy");
        assertThat(tracker.recordResult(service, false)).isEqualTo("healthy");
        assertThat(tracker.recordResult(service, false)).isEqualTo("down");
    }

    @Test
    void successfulCheckResetsFailureCount() {
        tracker.recordResult(service, false);
        tracker.recordResult(service, false);

        assertThat(tracker.recordResult(service, true)).isEqualTo("healthy");
        assertThat(tracker.recordResult(service, false)).isEqualTo("healthy");
    }

    @Test
    void keepsServiceDownUntilAHealthCheckSucceeds() {
        tracker.recordResult(service, false);
        tracker.recordResult(service, false);
        assertThat(tracker.recordResult(service, false)).isEqualTo("down");

        assertThat(tracker.recordResult(service, false)).isEqualTo("down");
        assertThat(tracker.recordResult(service, true)).isEqualTo("healthy");
    }

    @Test
    void treatsNonPositiveThresholdAsImmediateFailure() {
        ServiceEntity immediateService = new ServiceEntity(2L, "Immediate service", "https://example.test", 0);

        assertThat(tracker.recordResult(immediateService, false)).isEqualTo("down");
    }
}
