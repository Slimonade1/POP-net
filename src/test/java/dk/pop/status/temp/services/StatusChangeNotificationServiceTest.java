package dk.pop.status.temp.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StatusChangeNotificationServiceTest {
    @Mock private EmailNotificationService email;
    @Mock private WebhookNotificationService webhooks;
    private StatusChangeNotificationService service;

    @BeforeEach
    void setUp() {
        service = new StatusChangeNotificationService(email, webhooks);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"healthy", "HEALTHY"})
    void initialObservationAndUnchangedStatusesDoNotAlert(String previous) {
        service.notifyStatusChange("Printing", previous, "healthy", "OK");
        verifyNoInteractions(email, webhooks);
    }

    @ParameterizedTest
    @CsvSource({"healthy, down", "down, healthy"})
    void failureAndRecoveryEachNotifyBothChannelsOnce(String previous, String current) {
        service.notifyStatusChange("Printing", previous, current, "Check result");
        verify(email).notifyStatusChange("Printing", previous, current, "Check result");
        verify(webhooks).notifyStatusChange("Printing", previous, current, "Check result");
        verifyNoMoreInteractions(email, webhooks);
    }

    @Test
    void emailFailureDoesNotPreventWebhookDelivery(CapturedOutput output) {
        doThrow(new IllegalStateException("private credential"))
                .when(email).notifyStatusChange("Printing", "healthy", "down", "Failure");

        assertThatCode(() -> service.notifyStatusChange("Printing", "healthy", "down", "Failure"))
                .doesNotThrowAnyException();

        verify(webhooks).notifyStatusChange("Printing", "healthy", "down", "Failure");
        assertThat(output.getAll()).contains("Email notification failed", "Printing")
                .doesNotContain("private credential");
    }

    @Test
    void webhookFailureDoesNotEscapeOrRepeatEmailDelivery(CapturedOutput output) {
        doThrow(new IllegalStateException("secret webhook URL"))
                .when(webhooks).notifyStatusChange("Printing", "down", "healthy", "Recovery");

        assertThatCode(() -> service.notifyStatusChange("Printing", "down", "healthy", "Recovery"))
                .doesNotThrowAnyException();

        verify(email).notifyStatusChange("Printing", "down", "healthy", "Recovery");
        assertThat(output.getAll()).contains("Webhook notifications failed", "Printing")
                .doesNotContain("secret webhook URL");
    }
}
