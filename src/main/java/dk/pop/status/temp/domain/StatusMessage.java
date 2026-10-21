package dk.pop.status.temp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("status_message")
public record StatusMessage(@Id Long id, String message) {
}
