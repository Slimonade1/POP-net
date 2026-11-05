package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.StatusNote;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import dk.pop.status.temp.repository.StatusNoteRepository;
import dk.pop.status.temp.utils.StatusNoteUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class StatusNoteService {
    private final StatusNoteRepository notes;
    private final ServiceEntityRepository services;

    public StatusNoteService(StatusNoteRepository notes, ServiceEntityRepository services) {
        this.notes = notes;
        this.services = services;
    }

    // Public pages receive only the service name and the deliberately published note.
    public record PublicService(long id, String name, String note) {
    }

    public Optional<StatusNote> findByServiceId(long serviceId) {
        return notes.findLatestByServiceId(serviceId);
    }

    public List<PublicService> findPublicNotes() {
        Map<Long, StatusNote> byService = notes.findLatestByService().stream()
                .collect(Collectors.toMap(StatusNote::serviceId, Function.identity()));
        return StreamSupport.stream(services.findAll().spliterator(), false)
                .filter(service -> byService.containsKey(service.id()))
                .map(service -> new PublicService(service.id(), service.name(), byService.get(service.id()).description()))
                .sorted(Comparator.comparing(PublicService::name))
                .toList();
    }

    public Optional<PublicService> findPublicService(long serviceId) {
        return services.findById(serviceId)
                .map(service -> new PublicService(service.id(), service.name(),
                        findByServiceId(serviceId).map(StatusNote::description).orElse(null)));
    }

    @Transactional
    public void save(long serviceId, String description) {
        String normalized = StatusNoteUtils.normalize(description);
        // Serialize writes per service so simultaneous saves still leave one current note.
        services.findByIdForUpdate(serviceId).orElseThrow();
        notes.deleteByServiceId(serviceId);
        notes.save(new StatusNote(null, normalized, serviceId));
    }

    @Transactional
    public void delete(long serviceId) {
        services.findByIdForUpdate(serviceId).orElseThrow();
        notes.deleteByServiceId(serviceId);
    }
}
