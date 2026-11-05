package dk.pop.status.temp.view;

import dk.pop.status.temp.repository.ServiceEntityRepository;
import dk.pop.status.temp.services.StatusLogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class StatusLogController {
    private final ServiceEntityRepository services;
    private final StatusLogService logs;

    public StatusLogController(ServiceEntityRepository services, StatusLogService logs) {
        this.services = services;
        this.logs = logs;
    }

    @GetMapping("/admin/services")
    public String services(Model model) {
        model.addAttribute("services", services.findAll());
        model.addAttribute("latestLogs", logs.findLatestByService());
        return "service-diagnostics";
    }

    @GetMapping("/admin/services/{id}")
    public String service(@PathVariable long id, Model model) {
        var service = services.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var history = logs.findRecentByServiceId(id);
        model.addAttribute("service", service);
        model.addAttribute("latestLog", history.isEmpty() ? null : history.getFirst());
        model.addAttribute("history", history);
        model.addAttribute("historyLimit", StatusLogService.HISTORY_LIMIT);
        return "service-diagnostic-details";
    }
}
