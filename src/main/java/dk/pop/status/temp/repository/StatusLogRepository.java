package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.StatusLog;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StatusLogRepository extends CrudRepository<StatusLog, Long> {
    
    // Custom query allowing Thymeleaf views to look up the logs for a specific service ID
    List<StatusLog> findByServiceId(Long serviceId);
}
