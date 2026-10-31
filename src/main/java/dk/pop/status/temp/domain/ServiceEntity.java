package dk.pop.status.temp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("services")
public record ServiceEntity(
    @Id Long id,
    String name,
    String description,
    int retryThreshold
) {}
