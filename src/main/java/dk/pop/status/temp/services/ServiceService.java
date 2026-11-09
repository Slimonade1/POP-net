package dk.pop.status.temp.services;

import dk.pop.status.temp.repository.ServiceRepository;
import org.springframework.stereotype.Service;

@Service
public class ServiceService {
    private final ServiceRepository repository;

    ServiceService(ServiceRepository serviceRepository){
        repository = serviceRepository;
    }

    public void createService(dk.pop.status.temp.domain.Service service){
        repository.create(service);
    }
}
