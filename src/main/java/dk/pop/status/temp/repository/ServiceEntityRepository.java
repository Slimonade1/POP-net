package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.ServiceEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceEntityRepository extends CrudRepository<ServiceEntity, Long> {
    // Spring Data JDBC automatically generates all standard database operations
}
