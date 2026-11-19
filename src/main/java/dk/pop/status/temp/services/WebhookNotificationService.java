package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.WebhookDestination;
import dk.pop.status.temp.repository.WebhookDestinationRepository;
import dk.pop.status.temp.utils.Log;
import dk.pop.status.temp.utils.WebhookDestinationUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class WebhookNotificationService {
    private static final Logger log = Log.getLogger(WebhookNotificationService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final WebhookDestinationRepository destinations;
    private final ObjectMapper json;
    private final HttpClient httpClient;

    @Autowired
    public WebhookNotificationService(WebhookDestinationRepository destinations, ObjectMapper json) {
        this(destinations, json, HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    WebhookNotificationService(WebhookDestinationRepository destinations, ObjectMapper json, HttpClient httpClient) {
        this.destinations = destinations;
        this.json = json;
        this.httpClient = httpClient;
    }

    public void notifyStatusChange(String serviceName, String previousStatus,
                                   String currentStatus, String diagnosticMessage) {
        if (previousStatus == null || previousStatus.equalsIgnoreCase(currentStatus)) {
            return;
        }

        String message = "POP-net: %s changed from %s to %s.\nDetails: %s"
                .formatted(serviceName, previousStatus, currentStatus, diagnosticMessage);

        for (WebhookDestination destination : destinations.findByEnabledTrue()) {
            try {
                var payload = switch (destination.provider()) {
                    // Escape Slack's special characters to avoid interpreting service text as mentions.
                    case SLACK -> Map.of("text", message.replace("&", "&amp;")
                            .replace("<", "&lt;").replace(">", "&gt;"));
                    case DISCORD -> Map.of("content", message,
                            "allowed_mentions", Map.of("parse", List.of()));
                };
                var request = HttpRequest.newBuilder(WebhookDestinationUtils.deliveryUri(destination.provider(), destination.url()))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                        .build();
                var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.atError().log("Webhook delivery failed for destination {} ({}) and service '{}': HTTP {}",
                            destination.id(), destination.provider(), serviceName, response.statusCode());
                } else {
                    log.atInfo().log("Webhook delivered to destination {} ({}) for service '{}'",
                            destination.id(), destination.provider(), serviceName);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.atError().log("Webhook delivery interrupted for destination {} ({}) and service '{}'",
                        destination.id(), destination.provider(), serviceName);
                return;
            } catch (HttpTimeoutException e) {
                log.atError().log("Webhook delivery timed out after {} seconds for destination {} ({}) and service '{}'",
                        REQUEST_TIMEOUT.toSeconds(), destination.id(), destination.provider(), serviceName);
            } catch (IOException | RuntimeException e) {
                // Exception messages and response bodies can contain the secret webhook URL.
                log.atError().log("Webhook delivery failed for destination {} ({}) and service '{}' ({})",
                        destination.id(), destination.provider(), serviceName, e.getClass().getSimpleName());
            }
        }
    }

}
