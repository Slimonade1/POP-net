package dk.pop.status.temp.utils;

import dk.pop.status.temp.domain.WebhookDestination;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

public final class WebhookDestinationUtils {
    private WebhookDestinationUtils() {
    }

    public static URI deliveryUri(WebhookDestination.Provider provider, String url) {
        if (provider == null || url == null || url.isBlank() || url.strip().length() > 2048) {
            throw new IllegalArgumentException("Choose a provider and enter its webhook URL (up to 2048 characters).");
        }
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Enter a valid webhook URL.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("An HTTPS webhook URL is required.");
        }

        boolean valid = switch (provider) {
            case SLACK -> ("hooks.slack.com".equalsIgnoreCase(uri.getHost())
                    || "hooks.slack-gov.com".equalsIgnoreCase(uri.getHost()))
                    && uri.getPath().matches("/services/[^/]+/[^/]+/[^/]+")
                    && uri.getRawQuery() == null;
            case DISCORD -> "discord.com".equalsIgnoreCase(uri.getHost())
                    && uri.getPath().matches("/api(?:/v[0-9]+)?/webhooks/[0-9]+/[^/]+");
        };
        if (!valid) {
            throw new IllegalArgumentException("The webhook URL does not match the selected provider.");
        }
        // Discord's default wait=false can omit errors for messages that were not saved.
        return provider == WebhookDestination.Provider.DISCORD
                ? UriComponentsBuilder.fromUri(uri).replaceQueryParam("wait", "true").build(true).toUri()
                : uri;
    }
}
