package dk.pop.status.temp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dk.pop.status.temp.services.StatusNoteService;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class TempApplicationTests {

	@Autowired
	private StatusNoteService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void statusNotesSupportCrud() {
		String serviceName = "test-service-" + java.util.UUID.randomUUID();
		jdbcTemplate.update("INSERT INTO services (name, description) VALUES (?, ?)", serviceName, "Test service");
		Long serviceId = jdbcTemplate.queryForObject("SELECT id FROM services WHERE name = ?", Long.class, serviceName);
		assertNotNull(serviceId);

		try {
			var created = service.create("Initial note", serviceId);
			assertThat(service.findById(created.id())).contains(created);
			assertThat(service.findAll()).contains(created);

			assertThat(service.update(created.id(), "Updated note", serviceId)).isTrue();
			var updated = service.findById(created.id()).orElseThrow();
			assertThat(updated.description()).isEqualTo("Updated note");

			assertThat(service.delete(created.id())).isTrue();
			assertThat(service.findById(created.id())).isEmpty();
		} finally {
			jdbcTemplate.update("DELETE FROM services WHERE id = ?", serviceId);
		}
	}

}
