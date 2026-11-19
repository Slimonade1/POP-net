package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.WebhookDestination;
import dk.pop.status.temp.repository.WebhookDestinationRepository;
import dk.pop.status.temp.utils.WebhookDestinationUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebhookDestinationService {
    private final WebhookDestinationRepository destinations;

    public WebhookDestinationService(WebhookDestinationRepository destinations) {
        this.destinations = destinations;
    }

    public List<WebhookDestination> findAll() {
        return destinations.findAll();
    }

    public void create(String name, String provider, String url) {
        destinations.save(validated(null, name, provider, url, false));
    }

    public void update(long id, String name, String provider, String url) {
        var existing = destinations.findById(id).orElseThrow();
        String replacement = url == null || url.isBlank() ? existing.url() : url;
        destinations.save(validated(id, name, provider, replacement, existing.enabled()));
    }

    public void setEnabled(long id, boolean enabled) {
        var existing = destinations.findById(id).orElseThrow();
        if (enabled) {
            WebhookDestinationUtils.deliveryUri(existing.provider(), existing.url());
        }
        destinations.save(new WebhookDestination(id, existing.name(), existing.provider(), existing.url(), enabled));
    }

    public void delete(long id) {
        destinations.findById(id).orElseThrow();
        destinations.deleteById(id);
    }

    private static WebhookDestination validated(Long id, String name, String provider, String url, boolean enabled) {
        if (name == null || name.isBlank() || name.strip().length() > 100) {
            throw new IllegalArgumentException("Enter a destination name of 1 to 100 characters.");
        }
        WebhookDestination.Provider parsed;
        try {
            parsed = WebhookDestination.Provider.valueOf(provider == null ? "" : provider);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Choose Slack or Discord.");
        }
        WebhookDestinationUtils.deliveryUri(parsed, url);
        return new WebhookDestination(id, name.strip(), parsed, url.strip(), enabled);
    }
}
