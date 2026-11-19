package dk.pop.status.temp.view.status;

import dk.pop.status.temp.domain.StatusNote;
import dk.pop.status.temp.services.StatusNoteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusPagesTest {
    @Mock private ExampleData examples;
    @Mock private LiveData live;
    @Mock private StatusNoteService notes;

    @Test
    void realServiceNotesUseTheExistingPageEvenWhenExampleModeIsEnabled() {
        var service = new ServiceView(7L, "Management VPN Gateway", "management-vpn-gateway",
                false, null, List.of(), null);
        when(live.groups(any())).thenReturn(List.of(new ServiceGroup("Network", List.of(service))));
        when(notes.findByServiceId(7L)).thenReturn(Optional.of(new StatusNote(1L, "We are replacing the gateway.", 7L)));
        var model = new ExtendedModelMap();

        assertThat(new StatusPages(examples, live, true, notes).service("management-vpn-gateway", model)).isTrue();

        assertThat(model.get("service")).isEqualTo(service);
        assertThat(model.get("publicNote")).isEqualTo("We are replacing the gateway.");
        assertThat(model.get("example")).isEqualTo(false);
        verifyNoInteractions(examples);
    }

    @Test
    void exampleOnlyPagesStillWorkWithoutShowingUnrelatedDatabaseNotes() {
        var service = new ServiceView(null, "Demo", "demo", false, null, List.of(), null);
        when(live.groups(any())).thenReturn(List.of());
        when(examples.groups(any())).thenReturn(List.of(new ServiceGroup("Examples", List.of(service))));
        var model = new ExtendedModelMap();

        assertThat(new StatusPages(examples, live, true, notes).service("demo", model)).isTrue();

        assertThat(model.get("service")).isEqualTo(service);
        assertThat(model.get("publicNote")).isNull();
        assertThat(model.get("example")).isEqualTo(true);
        verifyNoInteractions(notes);
    }
}
