package dk.pop.status.temp.view;

import dk.pop.status.temp.SecurityConfig;
import dk.pop.status.temp.domain.WebhookDestination;
import dk.pop.status.temp.domain.WebhookDestination.Provider;
import dk.pop.status.temp.repository.WebhookDestinationRepository;
import dk.pop.status.temp.services.WebhookDestinationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Exercise the real controller, validation service, templates and access rules.
// Replacing the repository keeps this test independent of any database.
@WebMvcTest(WebhookNotificationController.class)
@Import({SecurityConfig.class, WebhookDestinationService.class})
class WebhookNotificationControllerTest {
    private static final String URL = "https://discord.com/api/webhooks/123/test-secret-token";
    @Autowired private MockMvc mvc;
    @MockitoBean private WebhookDestinationRepository destinations;
    @MockitoBean private ClientRegistrationRepository clientRegistrations;

    @Test
    void pageShowsDestinationsWithoutExposingSavedUrls() throws Exception {
        when(destinations.findAll()).thenReturn(List.of(destination(false)));

        mvc.perform(get("/admin/webhooks").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Team alerts")))
                .andExpect(content().string(containsString("Disabled")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("test-secret-token"))));
    }

    @Test
    void newDestinationIsSavedDisabledWithNormalizedName() throws Exception {
        mvc.perform(post("/admin/webhooks").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", " Team alerts ").param("provider", "DISCORD").param("url", URL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/webhooks"));

        verify(destinations).save(new WebhookDestination(null, "Team alerts", Provider.DISCORD, URL, false));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void adminCanEnableOrDisableADestination(boolean enabled) throws Exception {
        when(destinations.findById(1L)).thenReturn(Optional.of(destination(!enabled)));

        mvc.perform(post("/admin/webhooks/1/enabled").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("enabled", String.valueOf(enabled)))
                .andExpect(redirectedUrl("/admin/webhooks"));

        verify(destinations).save(destination(enabled));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " https://discord.com/api/webhooks/123/replacement-token "})
    void editPreservesEnabledStateAndRetainsOrReplacesTheUrl(String replacement) throws Exception {
        when(destinations.findById(1L)).thenReturn(Optional.of(destination(true)));

        mvc.perform(post("/admin/webhooks/1/update").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "Renamed team").param("provider", "DISCORD").param("url", replacement))
                .andExpect(redirectedUrl("/admin/webhooks"));

        verify(destinations).save(new WebhookDestination(1L, "Renamed team", Provider.DISCORD,
                replacement.isBlank() ? URL : replacement.strip(), true));
    }

    @Test
    void adminCanRemoveADestination() throws Exception {
        when(destinations.findById(1L)).thenReturn(Optional.of(destination(false)));

        mvc.perform(post("/admin/webhooks/1/delete").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/webhooks"));

        verify(destinations).deleteById(1L);
    }

    @Test
    void invalidUpdateReturnsAValidationMessageWithoutSaving() throws Exception {
        when(destinations.findById(1L)).thenReturn(Optional.of(destination(false)));

        mvc.perform(post("/admin/webhooks/1/update").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "Team").param("provider", "SLACK").param("url", URL))
                .andExpect(redirectedUrl("/admin/webhooks"))
                .andExpect(flash().attribute("error", "The webhook URL does not match the selected provider."));

        verify(destinations, never()).save(any());
    }

    @Test
    void invalidNamesAndProvidersAreRejectedWithoutSaving() throws Exception {
        mvc.perform(post("/admin/webhooks").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", " ").param("provider", "DISCORD").param("url", URL))
                .andExpect(redirectedUrl("/admin/webhooks"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/admin/webhooks").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "Team").param("provider", "UNKNOWN").param("url", URL))
                .andExpect(redirectedUrl("/admin/webhooks"))
                .andExpect(flash().attribute("error", "Choose Slack or Discord."));

        verifyNoInteractions(destinations);
    }

    @Test
    void accessRulesAndCsrfProtectAllSettingsActions() throws Exception {
        mvc.perform(get("/admin/webhooks")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/webhooks").with(user("visitor").roles("USER")))
                .andExpect(status().isForbidden());
        for (String path : new String[]{"/admin/webhooks", "/admin/webhooks/1/update", "/admin/webhooks/1/enabled", "/admin/webhooks/1/delete"}) {
            mvc.perform(post(path).with(csrf()).param("enabled", "true"))
                    .andExpect(status().is3xxRedirection());
            mvc.perform(post(path).with(user("visitor").roles("USER")).with(csrf()).param("enabled", "true"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path).with(user("admin").roles("ADMIN")).param("enabled", "true"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(destinations);
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "enabled", "delete"})
    void unknownDestinationsReturnNotFound(String action) throws Exception {
        when(destinations.findById(999L)).thenReturn(Optional.empty());

        mvc.perform(post("/admin/webhooks/999/" + action)
                        .with(user("admin").roles("ADMIN")).with(csrf()).param("enabled", "true"))
                .andExpect(status().isNotFound());

        verify(destinations, never()).save(any());
        verify(destinations, never()).deleteById(anyLong());
    }

    private static WebhookDestination destination(boolean enabled) {
        return new WebhookDestination(1L, "Team alerts", Provider.DISCORD, URL, enabled);
    }
}
