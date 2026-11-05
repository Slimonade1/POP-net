package dk.pop.status.temp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("status_notes")
public record StatusNote(@Id Long id, String description, Long serviceId) {
}
