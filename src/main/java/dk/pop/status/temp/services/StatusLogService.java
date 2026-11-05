package dk.pop.status.temp.services;

import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.repository.StatusLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class StatusLogService {
    public static final int HISTORY_LIMIT = 50;
    private final StatusLogRepository repository;

    StatusLogService(StatusLogRepository statusLogRepository){
        repository = statusLogRepository;
    }

    public void createLog(StatusLog statusLog){
        repository.create(statusLog);
    }

    public Map<Long, StatusLog> findLatestByService() {
        return repository.findLatestByService();
    }

    public List<StatusLog> findRecentByServiceId(long serviceId) {
        return repository.findRecentByServiceId(serviceId, HISTORY_LIMIT);
    }
}
