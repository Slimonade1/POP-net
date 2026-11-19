package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.WebhookDestination;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface WebhookDestinationRepository extends ListCrudRepository<WebhookDestination, Long> {
    List<WebhookDestination> findByEnabledTrue();
}
