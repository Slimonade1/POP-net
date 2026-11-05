package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.ServiceEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceEntityRepository extends CrudRepository<ServiceEntity, Long> {
    // Spring Data JDBC automatically generates all standard database operations

    @Query("SELECT * FROM services WHERE id = :id FOR UPDATE")
    Optional<ServiceEntity> findByIdForUpdate(long id);
}
