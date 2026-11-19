package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.StatusNote;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import dk.pop.status.temp.repository.StatusNoteRepository;
import dk.pop.status.temp.utils.StatusNoteUtils;
import dk.pop.status.temp.utils.ServiceSlugUtils;
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
public class  StatusNoteService {
    private final StatusNoteRepository notes;
    private final ServiceEntityRepository services;

    public StatusNoteService(StatusNoteRepository notes, ServiceEntityRepository services) {
        this.notes = notes;
        this.services = services;
    }

    // Public pages receive only the service name and the deliberately published note.
    public record ServiceSummary(long id, String name, String slug, String note) {
    }

    public Optional<StatusNote> findByServiceId(long serviceId) {
        return notes.findLatestByServiceId(serviceId);
    }

    public List<StatusNote> findAllByServiceId(long serviceId) {
        return notes.findAllByServiceId(serviceId);
    }

    public List<ServiceSummary> findPublishedServiceNotes() {
        Map<Long, StatusNote> byService = notes.findLatestByService().stream()
                .collect(Collectors.toMap(StatusNote::serviceId, Function.identity()));
        var allServices = StreamSupport.stream(services.findAll().spliterator(), false).toList();
        Map<Long, String> slugs = ServiceSlugUtils.slugs(allServices);
        return allServices.stream()
                .filter(service -> byService.containsKey(service.id()) && slugs.containsKey(service.id()))
                .map(service -> new ServiceSummary(service.id(), service.name(), slugs.get(service.id()),
                        byService.get(service.id()).description()))
                .sorted(Comparator.comparing(ServiceSummary::name))
                .toList();
    }

    public Optional<ServiceSummary> findServiceSummary(long serviceId) {
        return services.findById(serviceId)
                .flatMap(service -> {
                    String slug = ServiceSlugUtils.slugs(services.findAll()).get(serviceId);
                    return slug == null ? Optional.empty() : Optional.of(new ServiceSummary(
                            service.id(), service.name(), slug,
                            findByServiceId(serviceId).map(StatusNote::description).orElse(null)));
                });
    }

    @Transactional
    public void add(long serviceId, String description) {
        String normalized = StatusNoteUtils.normalize(description);
        services.findById(serviceId).orElseThrow();
        notes.save(new StatusNote(null, normalized, serviceId));
    }

    @Transactional
    public void delete(long serviceId, long noteId) {
        services.findById(serviceId).orElseThrow();
        notes.deleteByIdAndServiceId(noteId, serviceId);
    }
}
