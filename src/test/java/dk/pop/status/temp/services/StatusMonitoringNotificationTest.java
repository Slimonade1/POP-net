package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.ServiceEntity;
import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.repository.ResponseTimeRepository;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import dk.pop.status.temp.repository.StatusLogRepository;
import dk.pop.status.temp.utils.UnitOfWork.UnitOfWorkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusMonitoringNotificationTest {
    @Mock private ServiceEntityRepository services;
    @Mock private StatusLogRepository logs;
    @Mock private StatusChangeNotificationService notifications;
    @Mock private ResponseTimeRepository responseTimes;
    private final ServiceEntity printer = new ServiceEntity(1L, "Printing", "https://example.test", 3);
    private StatusMonitoringService monitor;
    private Map<String, String> previous;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        monitor = new StatusMonitoringService(services, logs, new HealthStatusTracker(), notifications, responseTimes);
        previous = (Map<String, String>) ReflectionTestUtils.getField(monitor, "previousStatuses");
        previous.put(printer.name(), "healthy");
    }

    @Test
    void commitsAndReleasesConnectionBeforeNotifying() throws Exception {
        record("down");

        var order = inOrder(logs, notifications);
        order.verify(logs).begin();
        order.verify(logs).create(any(StatusLog.class));
        order.verify(logs).commit();
        order.verify(logs).close();
        order.verify(notifications).notifyStatusChange("Printing", "healthy", "down", "Check result");
        assertThat(previous.get("Printing")).isEqualTo("down");
    }

    @Test
    void failedCommitDoesNotNotifyOrAdvanceTheSavedStatus() throws Exception {
        doThrow(new UnitOfWorkException("Database failure")).when(logs).commit();

        assertThatThrownBy(() -> record("down")).isInstanceOf(RuntimeException.class);

        verify(logs).rollback();
        verify(logs).close();
        verifyNoInteractions(notifications);
        assertThat(previous.get("Printing")).isEqualTo("healthy");
    }

    @Test
    void deliveryFailureDoesNotRollbackOrResendOnRoutineChecks() throws Exception {
        doThrow(new IllegalStateException("Delivery failed")).when(notifications)
                .notifyStatusChange("Printing", "healthy", "down", "Check result");

        record("down");
        record("down");

        verify(notifications).notifyStatusChange("Printing", "healthy", "down", "Check result");
        verify(logs).create(any(StatusLog.class));
        verify(logs, never()).rollback();
        assertThat(previous.get("Printing")).isEqualTo("down");
    }

    private void record(String status) {
        ReflectionTestUtils.invokeMethod(monitor, "saveLogEntry", printer, status, "Check result", 100);
    }
}
