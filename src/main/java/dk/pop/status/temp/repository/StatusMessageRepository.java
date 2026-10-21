package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.StatusMessage;
import org.springframework.data.repository.ListCrudRepository;

public interface StatusMessageRepository extends ListCrudRepository<StatusMessage, Long> {
}
