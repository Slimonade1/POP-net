package dk.pop.status.temp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("services")
public record ServiceEntity(
    @Id Long id,
    String name,
    String description,
    int retryThreshold
) {
    public boolean isIcmp() {
        return description != null && description.startsWith("icmp://");
    }

    public boolean isWeb() {
        return description != null && (description.startsWith("http://") || description.startsWith("https://"));
    }
}
