package dk.pop.status.temp.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("email_recipients")
public record EmailRecipient(@Id Long id, String email) {}
