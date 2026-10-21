package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.StatusMessage;
import dk.pop.status.temp.repository.StatusMessageRepository;
import dk.pop.status.temp.utils.StatusMessageUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatusMessageService {
    private final StatusMessageRepository repository;

    public StatusMessageService(StatusMessageRepository repository) {
        this.repository = repository;
    }

    public List<StatusMessage> findAll() {
        return repository.findAll();
    }

    public void add(String message) {
        repository.save(new StatusMessage(null, StatusMessageUtils.normalize(message)));
    }

    public void delete(long id) {
        repository.deleteById(id);
    }
}
