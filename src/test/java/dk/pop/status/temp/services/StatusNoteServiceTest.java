package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.ServiceEntity;
import dk.pop.status.temp.domain.StatusNote;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import dk.pop.status.temp.repository.StatusNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StatusNoteServiceTest {
    @Mock
    private StatusNoteRepository notes;

    @Mock
    private ServiceEntityRepository services;

    private StatusNoteService service;

    @BeforeEach
    void setUp() {
        service = new StatusNoteService(notes, services);
    }

    @Test
    void publicUpdatesOnlyIncludePublishedNotesAndAreSortedByServiceName() {
        given(services.findAll()).willReturn(List.of(
                new ServiceEntity(1L, "Website", "https://internal-web.test", 3),
                new ServiceEntity(2L, "Backup", "icmp://internal-backup.test", 3),
                new ServiceEntity(3L, "Printing", "https://internal-print.test", 3)));
        given(notes.findLatestByService()).willReturn(List.of(
                new StatusNote(10L, "Website maintenance", 1L),
                new StatusNote(11L, "Backup is delayed", 2L)));

        assertThat(service.findPublicNotes()).containsExactly(
                new StatusNoteService.PublicService(2L, "Backup", "Backup is delayed"),
                new StatusNoteService.PublicService(1L, "Website", "Website maintenance"));
    }

    @Test
    void servicesWithTheSameNameKeepTheirOwnNotes() {
        given(services.findAll()).willReturn(List.of(
                new ServiceEntity(1L, "Print server", "https://printer-one.test", 3),
                new ServiceEntity(2L, "Print server", "https://printer-two.test", 3)));
        given(notes.findLatestByService()).willReturn(List.of(
                new StatusNote(10L, "First printer is being replaced", 1L),
                new StatusNote(11L, "Second printer is out of paper", 2L)));

        assertThat(service.findPublicNotes()).containsExactlyInAnyOrder(
                new StatusNoteService.PublicService(1L, "Print server", "First printer is being replaced"),
                new StatusNoteService.PublicService(2L, "Print server", "Second printer is out of paper"));
    }

    @Test
    void knownServiceWithoutANoteStillHasAPublicPage() {
        given(services.findById(1L)).willReturn(Optional.of(
                new ServiceEntity(1L, "Print server", "https://internal-print.test", 3)));
        given(notes.findLatestByServiceId(1L)).willReturn(Optional.empty());

        assertThat(service.findPublicService(1L))
                .contains(new StatusNoteService.PublicService(1L, "Print server", null));
    }

    @Test
    void unknownServiceHasNoPublicPage() {
        given(services.findById(99L)).willReturn(Optional.empty());

        assertThat(service.findPublicService(99L)).isEmpty();
        verifyNoInteractions(notes);
    }
}
