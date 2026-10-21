package dk.pop.status.temp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dk.pop.status.temp.services.StatusMessageService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TempApplicationTests {

	@Autowired
	private StatusMessageService service;

	@Test
	void statusMessagesCanBeSavedAndLoadedFromMySql() {
		service.add("MySQL is working");

		var savedMessage = service.findAll().stream()
				.filter(message -> message.message().equals("MySQL is working"))
				.findFirst()
				.orElseThrow();

		assertThat(savedMessage.id()).isNotNull();

		service.delete(savedMessage.id());
		assertThat(service.findAll()).doesNotContain(savedMessage);
	}

}
