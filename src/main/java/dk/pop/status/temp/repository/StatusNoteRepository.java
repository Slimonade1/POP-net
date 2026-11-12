package dk.pop.status.temp.repository;

import dk.pop.status.temp.domain.StatusNote;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;

public interface StatusNoteRepository extends ListCrudRepository<StatusNote, Long> {
    @Query("SELECT * FROM status_notes WHERE service_id = :serviceId ORDER BY id DESC LIMIT 1")
    Optional<StatusNote> findLatestByServiceId(long serviceId);

    @Query("""
            SELECT n.* FROM status_notes n
            WHERE n.id = (SELECT MAX(recent.id) FROM status_notes recent WHERE recent.service_id = n.service_id)
            """)
    List<StatusNote> findLatestByService();

    @Query("SELECT * FROM status_notes WHERE service_id = :serviceId ORDER BY id DESC")
    List<StatusNote> findAllByServiceId(long serviceId);

    @Modifying
    @Query("DELETE FROM status_notes WHERE id = :id AND service_id = :serviceId")
    int deleteByIdAndServiceId(long id, long serviceId);
}
