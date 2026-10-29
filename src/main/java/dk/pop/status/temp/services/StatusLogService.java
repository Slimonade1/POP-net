package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.repository.StatusLogRepository;
import org.springframework.stereotype.Service;

@Service
public class StatusLogService {
    private final StatusLogRepository repository;

    StatusLogService(StatusLogRepository statusLogRepository){
        repository = statusLogRepository;
    }

    public void createLog(StatusLog statusLog){
        repository.create(statusLog);
    }
}
