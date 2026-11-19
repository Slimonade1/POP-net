package dk.pop.status.temp.view;

import dk.pop.status.temp.domain.WebhookDestination.Provider;
import dk.pop.status.temp.repository.WebhookDestinationRepository;
import dk.pop.status.temp.services.StatusMonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:webhook-tests;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.sql.init.schema-locations=classpath:migrations/0000.sql,classpath:migrations/0004_webhook_destinations.sql",
        "notifications.email.enabled=false"
})
@AutoConfigureMockMvc
class WebhookNotificationIntegrationTest {
    private static final String URL = "https://discord.com/api/webhooks/123/test-secret-token";
    @Autowired private MockMvc mvc;
    @Autowired private WebhookDestinationRepository destinations;
    @MockitoBean private StatusMonitoringService monitoring;
    // Authentication is supplied by the test; do not contact the real identity provider.
    @MockitoBean private ClientRegistrationRepository clientRegistrations;

    @BeforeEach
    void setUp() {
        destinations.deleteAll();
    }

    @Test
    void adminCanCreateEnableEditDisableAndRemoveADestination() throws Exception {
        createDestination();
        var saved = destinations.findAll().getFirst();
        assertThat(saved.name()).isEqualTo("Team alerts");
        assertThat(saved.provider()).isEqualTo(Provider.DISCORD);
        assertThat(saved.url()).isEqualTo(URL);
        assertThat(saved.enabled()).isFalse();
        assertThat(destinations.findByEnabledTrue()).isEmpty();

        mvc.perform(get("/admin/webhooks").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Team alerts")))
                .andExpect(content().string(containsString("Disabled")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString(URL))))
                .andExpect(content().string(not(containsString("test-secret-token"))));

        mvc.perform(post("/admin/webhooks/{id}/enabled", saved.id())
                        .with(user("admin").roles("ADMIN")).with(csrf()).param("enabled", "true"))
                .andExpect(redirectedUrl("/admin/webhooks"));
        assertThat(destinations.findByEnabledTrue()).hasSize(1);

        mvc.perform(post("/admin/webhooks/{id}/update", saved.id())
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "Renamed team").param("provider", "DISCORD").param("url", ""))
                .andExpect(redirectedUrl("/admin/webhooks"));
        var edited = destinations.findById(saved.id()).orElseThrow();
        assertThat(edited.name()).isEqualTo("Renamed team");
        assertThat(edited.url()).isEqualTo(URL);
        assertThat(edited.enabled()).isTrue();

        mvc.perform(post("/admin/webhooks/{id}/enabled", saved.id())
                        .with(user("admin").roles("ADMIN")).with(csrf()).param("enabled", "false"))
                .andExpect(redirectedUrl("/admin/webhooks"));
        assertThat(destinations.findByEnabledTrue()).isEmpty();

        mvc.perform(post("/admin/webhooks/{id}/delete", saved.id())
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/webhooks"));
        assertThat(destinations.findAll()).isEmpty();
    }

    @Test
    void invalidUpdatesKeepTheSavedSettingsAndReturnAValidationMessage() throws Exception {
        createDestination();
        var saved = destinations.findAll().getFirst();

        mvc.perform(post("/admin/webhooks/{id}/update", saved.id())
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "Team").param("provider", "SLACK").param("url", URL))
                .andExpect(redirectedUrl("/admin/webhooks"))
                .andExpect(flash().attribute("error", "The webhook URL does not match the selected provider."));
        assertThat(destinations.findById(saved.id())).contains(saved);
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
        assertThat(destinations.findAll()).isEmpty();
    }

    @Test
    void anonymousVisitorsAndNonAdminsCannotReadSettingsOrChangeDestinations() throws Exception {
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
        assertThat(destinations.findAll()).isEmpty();
    }

    @Test
    void unknownDestinationsReturnNotFound() throws Exception {
        for (String action : new String[]{"update", "enabled", "delete"}) {
            mvc.perform(post("/admin/webhooks/999/" + action)
                            .with(user("admin").roles("ADMIN")).with(csrf()).param("enabled", "true"))
                    .andExpect(status().isNotFound());
        }
    }

    private void createDestination() throws Exception {
        mvc.perform(post("/admin/webhooks").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", " Team alerts ").param("provider", "DISCORD").param("url", URL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/webhooks"));
    }
}
