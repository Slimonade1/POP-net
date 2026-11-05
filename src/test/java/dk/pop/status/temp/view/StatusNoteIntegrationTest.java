package dk.pop.status.temp.view;

import dk.pop.status.temp.repository.StatusNoteRepository;
import dk.pop.status.temp.services.StatusMonitoringService;
import dk.pop.status.temp.services.StatusNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
        "spring.datasource.url=jdbc:h2:mem:status-note-tests;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.schema-locations=classpath:migrations/0000.sql",
        "notifications.email.enabled=false",
        "example-data=false"
})
@AutoConfigureMockMvc
class StatusNoteIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StatusNoteRepository notes;

    @Autowired
    private StatusNoteService noteService;

    // Prevent the scheduled monitor from contacting real services during tests.
    @MockitoBean
    private StatusMonitoringService monitoring;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM status_notes");
        jdbc.update("DELETE FROM status_logs");
        jdbc.update("DELETE FROM services");
        jdbc.update("""
                INSERT INTO services (id, name, description) VALUES
                (901, 'Print server', 'http://internal-print.example.test'),
                (902, 'Website', 'http://internal-web.example.test')
                """);
        jdbc.update("""
                INSERT INTO status_logs (service_id, status, date, time, message)
                VALUES (901, 'down', '2026-11-05', '10:30:00', 'Private diagnostic: connection refused')
                """);
    }

    @Test
    void adminCanPublishANoteThatAnonymousVisitorsCanReadWithoutExampleData() throws Exception {
        mvc.perform(post("/admin/services/901/note")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("description", "  Printing is unavailable.\nWe are replacing the server.  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/services/901"))
                .andExpect(flash().attribute("noteSuccess", "Public note saved."));

        // Read the saved value from the database rather than the submitted form/model.
        assertThat(notes.findLatestByServiceId(901).orElseThrow().description())
                .isEqualTo("Printing is unavailable.\nWe are replacing the server.");
        for (String path : new String[]{"/", "/services/id/901"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Print server")))
                    .andExpect(content().string(containsString("Printing is unavailable.")))
                    .andExpect(content().string(containsString("We are replacing the server.")))
                    .andExpect(content().string(not(containsString("internal-print.example.test"))))
                    .andExpect(content().string(not(containsString("Private diagnostic"))))
                    .andExpect(content().string(not(containsString("Example status preview"))));
        }
        mvc.perform(get("/admin/services/901").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Printing is unavailable.")))
                .andExpect(content().string(containsString("Remove public note")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void editingAndRemovingANoteOnlyAffectsTheSelectedService() throws Exception {
        noteService.save(901, "Original print update");
        noteService.save(902, "Website maintenance");

        mvc.perform(post("/admin/services/901/note")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("description", "Printing is restored"))
                .andExpect(redirectedUrl("/admin/services/901"));

        assertThat(notes.findAll()).hasSize(2);
        assertThat(notes.findLatestByServiceId(901).orElseThrow().description()).isEqualTo("Printing is restored");
        mvc.perform(get("/services/id/901"))
                .andExpect(content().string(containsString("Printing is restored")))
                .andExpect(content().string(not(containsString("Original print update"))));

        mvc.perform(post("/admin/services/901/note/delete")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/services/901"));

        assertThat(notes.findLatestByServiceId(901)).isEmpty();
        assertThat(notes.findLatestByServiceId(902).orElseThrow().description()).isEqualTo("Website maintenance");
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("Website maintenance")))
                .andExpect(content().string(not(containsString("Printing is restored"))));
        mvc.perform(get("/services/id/901"))
                .andExpect(content().string(containsString("No public note has been posted for this service.")));
    }

    @Test
    void invalidInputKeepsThePublishedNoteAndRedisplaysTheDraft() throws Exception {
        noteService.save(901, "Existing public note");
        String oversized = "x".repeat(501);

        var result = mvc.perform(post("/admin/services/901/note")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("description", oversized))
                .andExpect(redirectedUrl("/admin/services/901"))
                .andExpect(flash().attribute("noteError", "Public notes must be 500 characters or fewer."))
                .andExpect(flash().attribute("noteText", oversized))
                .andReturn();

        mvc.perform(get("/admin/services/901").with(user("admin").roles("ADMIN"))
                        .flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(oversized)))
                .andExpect(model().attribute("publishedNote", "Existing public note"))
                .andExpect(content().string(containsString("class=\"public-note\">Existing public note</span>")))
                .andExpect(content().string(containsString("Public notes must be 500 characters or fewer.")));
        assertThat(notes.findLatestByServiceId(901).orElseThrow().description()).isEqualTo("Existing public note");

        mvc.perform(post("/admin/services/901/note")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("noteError", "A public note is required."));
        assertThat(notes.findLatestByServiceId(901).orElseThrow().description()).isEqualTo("Existing public note");
    }

    @Test
    void adminOverviewShowsPublishedNotesAndUpdatesAfterRemoval() throws Exception {
        noteService.save(901, "Printing unavailable during maintenance");

        mvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Service outage notes showing")))
                .andExpect(content().string(containsString("Print server")))
                .andExpect(content().string(containsString("Printing unavailable during maintenance")))
                .andExpect(content().string(containsString("href=\"/admin/services/901\"")));

        noteService.delete(901);

        mvc.perform(get("/admin").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No public service outage notes are showing.")))
                .andExpect(content().string(not(containsString("Printing unavailable during maintenance"))));
    }

    @Test
    void publicNotesAndEditorEscapeHtml() throws Exception {
        String text = "<script>alert('test')</script>";
        noteService.save(901, text);

        for (String path : new String[]{"/", "/services/id/901"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("&lt;script&gt;")))
                    .andExpect(content().string(not(containsString(text))));
        }
        mvc.perform(get("/admin/services/901").with(user("admin").roles("ADMIN")))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString(text))));
    }

    @Test
    void onlyAdminsWithCsrfTokensCanChangeNotes() throws Exception {
        noteService.save(901, "Keep this note");
        for (String path : new String[]{"/admin/services/901/note", "/admin/services/901/note/delete"}) {
            mvc.perform(post(path).with(csrf()).param("description", "Unauthorized change"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
            mvc.perform(post(path).with(user("visitor").roles("USER")).with(csrf())
                            .param("description", "Unauthorized change"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path).with(user("admin").roles("ADMIN"))
                            .param("description", "Missing CSRF token"))
                    .andExpect(status().isForbidden());
        }
        assertThat(notes.findLatestByServiceId(901).orElseThrow().description()).isEqualTo("Keep this note");
    }

    @Test
    void unknownServiceCannotBeReadOrGivenANote() throws Exception {
        mvc.perform(get("/services/id/999"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/admin/services/999/note")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("description", "Unknown service"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/admin/services/999/note/delete")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(notes.findAll()).isEmpty();
    }

    @Test
    void noteRemainsPublishedAfterRecoveryUntilAnAdminRemovesIt() throws Exception {
        noteService.save(901, "Printing was interrupted during maintenance.");
        jdbc.update("""
                INSERT INTO status_logs (service_id, status, date, time, message)
                VALUES (901, 'healthy', '2026-11-05', '11:00:00', 'HTTP 200 OK')
                """);

        mvc.perform(get("/services/id/901"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Printing was interrupted during maintenance.")));
    }
}
