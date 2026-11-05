package dk.pop.status.temp.view;

import dk.pop.status.temp.SecurityConfig;
import dk.pop.status.temp.domain.ServiceEntity;
import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import dk.pop.status.temp.services.StatusLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatusLogController.class)
@Import(SecurityConfig.class)
class StatusLogControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServiceEntityRepository services;

    @MockitoBean
    private StatusLogService logs;

    private final ServiceEntity service = new ServiceEntity(7L, "Print server", "https://print.example.test", 3);
    private final StatusLog failure = new StatusLog(20L, "Print server", "down",
            LocalTime.of(10, 30), LocalDate.of(2026, 11, 5), "HTTP Fault: 503", 125);

    @Test
    void anonymousVisitorsMustLogInForBothPages() throws Exception {
        mvc.perform(get("/admin/services"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/services/7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(services, logs);
    }

    @Test
    void authenticatedNonAdminsCannotReadDiagnostics() throws Exception {
        mvc.perform(get("/admin/services").with(user("visitor").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/services/7").with(user("visitor").roles("USER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(services, logs);
    }

    @Test
    void overviewRendersLatestDiagnosticAndServicesWithoutResults() throws Exception {
        given(services.findAll()).willReturn(List.of(service, new ServiceEntity(8L, "Unchecked", null, 3)));
        given(logs.findLatestByService()).willReturn(Map.of(7L, failure));

        mvc.perform(get("/admin/services").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("service-diagnostics"))
                .andExpect(content().string(containsString("href=\"/admin/services/7\"")))
                .andExpect(content().string(containsString("HTTP Fault: 503")))
                .andExpect(content().string(containsString("2026-11-05")))
                .andExpect(content().string(containsString("No monitoring results recorded yet.")));
    }

    @Test
    void emptyOverviewExplainsThatNoServicesAreConfigured() throws Exception {
        given(services.findAll()).willReturn(List.of());
        given(logs.findLatestByService()).willReturn(Map.of());

        mvc.perform(get("/admin/services").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No services configured.")));
    }

    @Test
    void detailsRenderTargetLatestStatusAndHistoryWithEscapedDiagnostics() throws Exception {
        String diagnostic = "<script>alert('diagnostic')</script>";
        var olderFailure = new StatusLog(19L, "Print server", "down",
                LocalTime.of(9, 0), failure.date(), diagnostic, 90);
        given(services.findById(7L)).willReturn(Optional.of(service));
        given(logs.findRecentByServiceId(7L)).willReturn(List.of(failure, olderFailure));

        mvc.perform(get("/admin/services/7").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("service-diagnostic-details"))
                .andExpect(model().attribute("latestLog", failure))
                .andExpect(content().string(containsString("https://print.example.test")))
                .andExpect(content().string(containsString("HTTP Fault: 503")))
                .andExpect(content().string(containsString("125 ms")))
                .andExpect(content().string(containsString("50 most recent records")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString(diagnostic))));
    }

    @Test
    void knownServiceWithoutLogsShowsEmptyState() throws Exception {
        given(services.findById(7L)).willReturn(Optional.of(service));
        given(logs.findRecentByServiceId(7L)).willReturn(List.of());

        mvc.perform(get("/admin/services/7").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No monitoring results recorded yet.")))
                .andExpect(content().string(not(containsString("Recent status history"))));
    }

    @Test
    void missingServiceReturnsNotFound() throws Exception {
        given(services.findById(99L)).willReturn(Optional.empty());

        mvc.perform(get("/admin/services/99").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
        verifyNoInteractions(logs);
    }

    @Test
    void invalidServiceIdReturnsBadRequest() throws Exception {
        mvc.perform(get("/admin/services/not-a-number").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(services, logs);
    }

    @Test
    void recoveredServiceShowsHealthyStatusAndKeepsFailureInHistory() throws Exception {
        var recovery = new StatusLog(21L, service.name(), "healthy",
                LocalTime.of(11, 0), failure.date(), "HTTP 200 OK", 80);
        given(services.findById(7L)).willReturn(Optional.of(service));
        given(logs.findRecentByServiceId(7L)).willReturn(List.of(recovery, failure));

        mvc.perform(get("/admin/services/7").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("latestLog", recovery))
                .andExpect(model().attribute("history", List.of(recovery, failure)))
                .andExpect(content().string(containsString(">healthy</dd>")))
                .andExpect(content().string(containsString("HTTP 200 OK")))
                .andExpect(content().string(containsString("HTTP Fault: 503")));
    }

    @Test
    void zeroDurationIsDisplayedAsZeroInsteadOfMissing() throws Exception {
        var quickCheck = new StatusLog(21L, service.name(), "healthy",
                LocalTime.of(11, 0), failure.date(), "HTTP 200 OK", 0);
        given(services.findById(7L)).willReturn(Optional.of(service));
        given(logs.findRecentByServiceId(7L)).willReturn(List.of(quickCheck));

        mvc.perform(get("/admin/services/7").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("0 ms")))
                .andExpect(content().string(not(containsString("Not recorded"))));
    }

    @Test
    void missingDiagnosticFieldsRenderFallbacks() throws Exception {
        given(services.findById(7L)).willReturn(Optional.of(service));
        given(logs.findRecentByServiceId(7L)).willReturn(List.of(
                new StatusLog(20L, service.name(), "down", null, null, null, null)));

        mvc.perform(get("/admin/services/7").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Unknown date")))
                .andExpect(content().string(containsString("Unknown time")))
                .andExpect(content().string(containsString("No diagnostic message recorded.")))
                .andExpect(content().string(containsString("Not recorded")));
    }
}
