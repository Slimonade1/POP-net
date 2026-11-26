package dk.pop.status.temp.view;

import dk.pop.status.temp.services.StatusMessageService;
import dk.pop.status.temp.services.StatusMonitoringService;
import dk.pop.status.temp.services.StatusNoteService;
import dk.pop.status.temp.view.status.StatusPages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HomeControllerTest {
    @Mock
    private StatusMessageService messages;

    @Mock
    private StatusPages pages;

    @Mock
    private StatusMonitoringService monitoring;

    @Mock
    private StatusNoteService notes;

    @Test
    void statusFragmentReusesTheHomepageDataAndTemplate() {
        given(messages.findAll()).willReturn(List.of());
        given(notes.findPublishedServiceNotes()).willReturn(List.of());
        Model model = new ExtendedModelMap();

        assertThat(new HomeController(messages, pages, monitoring, notes).statusFragment(model))
                .isEqualTo("home :: status-content");

        assertThat(model.getAttribute("notices")).isEqualTo(List.of());
        assertThat(model.getAttribute("serviceUpdates")).isEqualTo(List.of());
        verify(pages).home(model);
    }
}
