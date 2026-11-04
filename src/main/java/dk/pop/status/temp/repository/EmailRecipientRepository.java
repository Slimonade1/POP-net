package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.EmailRecipient;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface EmailRecipientRepository extends CrudRepository<EmailRecipient, Long> {
    Optional<EmailRecipient> findByEmail(String email);
}
