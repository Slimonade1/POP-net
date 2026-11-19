package dk.pop.status.temp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("webhook_destinations")
public record WebhookDestination(
        @Id Long id,
        String name,
        Provider provider,
        String url,
        boolean enabled) {

    public enum Provider {
        SLACK,
        DISCORD
    }
}
