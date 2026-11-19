package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.WebhookDestination;
import dk.pop.status.temp.domain.WebhookDestination.Provider;
import dk.pop.status.temp.repository.WebhookDestinationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class WebhookNotificationServiceTest {
    private static final String SLACK_URL = "https://hooks.slack.com/services/T123/B123/secret-slack-token";
    private static final String DISCORD_URL = "https://discord.com/api/webhooks/123/secret-discord-token";
    @Mock private WebhookDestinationRepository destinations;
    @Mock private HttpClient http;
    @Mock private HttpResponse<Void> response;
    private final JsonMapper json = JsonMapper.builder().build();
    private WebhookNotificationService service;

    @BeforeEach
    void setUp() {
        service = new WebhookNotificationService(destinations, json, http);
    }

    @ParameterizedTest
    @EnumSource(Provider.class)
    void sendsProviderSpecificJsonWithEscapingAndTimeout(Provider provider) throws Exception {
        when(destinations.findByEnabledTrue()).thenReturn(List.of(destination(1L, provider)));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);

        service.notifyStatusChange("Printer \"A\"", "healthy", "down", "Error\n<@everyone> & æøå");

        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any());
        assertThat(request.getValue().method()).isEqualTo("POST");
        assertThat(request.getValue().timeout()).contains(Duration.ofSeconds(3));
        assertThat(request.getValue().headers().firstValue("Content-Type")).contains("application/json; charset=utf-8");
        var body = json.readTree(bodyOf(request.getValue()));
        if (provider == Provider.SLACK) {
            assertThat(request.getValue().uri().toString()).isEqualTo(SLACK_URL);
            assertThat(body.get("text").asText()).contains("Printer \"A\"", "healthy to down", "\n", "&lt;@everyone&gt; &amp; æøå");
            assertThat(body.has("content")).isFalse();
        } else {
            assertThat(request.getValue().uri().toString()).isEqualTo(DISCORD_URL + "?wait=true");
            assertThat(body.get("content").asText()).contains("Printer \"A\"", "healthy to down", "Error\n<@everyone> & æøå");
            assertThat(body.at("/allowed_mentions/parse").isArray()).isTrue();
            assertThat(body.at("/allowed_mentions/parse").size()).isZero();
        }
    }

    @Test
    void noEnabledDestinationsMeansNoHttpRequest() {
        when(destinations.findByEnabledTrue()).thenReturn(List.of());
        service.notifyStatusChange("Printing", "healthy", "down", "Failure");
        verifyNoInteractions(http);
    }

    @Test
    void routineChecksAndInitialObservationDoNotQueryDestinations() {
        service.notifyStatusChange("Printing", null, "healthy", "OK");
        service.notifyStatusChange("Printing", "DOWN", "down", "Failure");
        verifyNoInteractions(destinations, http);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 429, 500})
    void httpFailuresAreLoggedAndDoNotStopTheNextDestination(int status, CapturedOutput output) throws Exception {
        when(destinations.findByEnabledTrue()).thenReturn(List.of(destination(1L, Provider.SLACK), destination(2L, Provider.DISCORD)));
        @SuppressWarnings("unchecked")
        HttpResponse<Void> failure = mock(HttpResponse.class);
        when(failure.statusCode()).thenReturn(status);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenReturn(failure, response);
        when(response.statusCode()).thenReturn(200);

        service.notifyStatusChange("Printing", "healthy", "down", "Failure");

        verify(http, times(2)).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any());
        assertThat(output.getAll()).contains("Webhook delivery failed", "HTTP " + status, "destination 1", "Printing", "Webhook delivered to destination 2")
                .doesNotContain(SLACK_URL, DISCORD_URL, "secret-slack-token", "secret-discord-token");
    }

    @Test
    void timeoutIsLoggedAndTheNextDestinationIsStillAttempted(CapturedOutput output) throws Exception {
        when(destinations.findByEnabledTrue()).thenReturn(List.of(destination(1L, Provider.SLACK), destination(2L, Provider.DISCORD)));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenThrow(new HttpTimeoutException(SLACK_URL)).thenReturn(response);
        when(response.statusCode()).thenReturn(200);

        service.notifyStatusChange("Printing", "healthy", "down", "Failure");

        verify(http, times(2)).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any());
        assertThat(output.getAll()).contains("timed out after 3 seconds", "destination 1", "Webhook delivered to destination 2")
                .doesNotContain(SLACK_URL, "secret-slack-token");
    }

    @Test
    void networkFailureDoesNotExposeTheUrlOrStopOtherDestinations(CapturedOutput output) throws Exception {
        when(destinations.findByEnabledTrue()).thenReturn(List.of(destination(1L, Provider.SLACK), destination(2L, Provider.DISCORD)));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenThrow(new IOException(SLACK_URL)).thenReturn(response);
        when(response.statusCode()).thenReturn(200);

        service.notifyStatusChange("Printing", "healthy", "down", "Failure");

        assertThat(output.getAll()).contains("IOException", "destination 1", "Webhook delivered to destination 2")
                .doesNotContain(SLACK_URL, "secret-slack-token");
    }

    @Test
    void invalidDestinationIsSkippedWithoutSendingToAnUnapprovedHost(CapturedOutput output) throws Exception {
        when(destinations.findByEnabledTrue()).thenReturn(List.of(
                new WebhookDestination(1L, "Invalid", Provider.DISCORD, "https://example.test/secret", true),
                destination(2L, Provider.DISCORD)));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenReturn(response);
        when(response.statusCode()).thenReturn(200);

        service.notifyStatusChange("Printing", "healthy", "down", "Failure");

        verify(http).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any());
        assertThat(output.getAll()).contains("destination 1", "IllegalArgumentException", "Webhook delivered to destination 2")
                .doesNotContain("https://example.test/secret");
    }

    @Test
    void interruptionRestoresTheThreadFlagAndStopsFurtherDelivery(CapturedOutput output) throws Exception {
        when(destinations.findByEnabledTrue()).thenReturn(List.of(destination(1L, Provider.SLACK), destination(2L, Provider.DISCORD)));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any()))
                .thenThrow(new InterruptedException(SLACK_URL));
        try {
            service.notifyStatusChange("Printing", "healthy", "down", "Failure");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(http).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<Void>>any());
            assertThat(output.getAll()).contains("Webhook delivery interrupted").doesNotContain(SLACK_URL);
        } finally {
            Thread.interrupted();
        }
    }

    private static WebhookDestination destination(long id, Provider provider) {
        return new WebhookDestination(id, "Team alerts", provider, provider == Provider.SLACK ? SLACK_URL : DISCORD_URL, true);
    }

    private static String bodyOf(HttpRequest request) throws Exception {
        var result = new CompletableFuture<String>();
        var bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return result.get(5, TimeUnit.SECONDS);
    }
}
