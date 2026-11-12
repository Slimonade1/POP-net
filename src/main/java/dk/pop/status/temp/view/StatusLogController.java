package dk.pop.status.temp.view;

import dk.pop.status.temp.domain.ServiceEntity;
import dk.pop.status.temp.domain.StatusLog;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import dk.pop.status.temp.services.StatusLogService;
import dk.pop.status.temp.services.StatusNoteService;
import dk.pop.status.temp.utils.StatusNoteUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class StatusLogController {
    private final ServiceEntityRepository services;
    private final StatusLogService logs;
    private final StatusNoteService notes;

    public StatusLogController(ServiceEntityRepository services, StatusLogService logs, StatusNoteService notes) {
        this.services = services;
        this.logs = logs;
        this.notes = notes;
    }

    @GetMapping("/admin/services")
    public String services(Model model) {
        Map<Long, StatusLog> latest = logs.findLatestByService();
        List<ServiceEntity> down = new ArrayList<>();
        List<ServiceEntity> others = new ArrayList<>();
        for (ServiceEntity service : services.findAll()) {
            StatusLog log = latest.get(service.id());
            if (log != null && "down".equals(log.status())) {
                down.add(service);
            } else {
                others.add(service);
            }
        }
        down.addAll(others);

        Map<Long, String> noteText = new HashMap<>();
        for (StatusNoteService.PublicService note : notes.findPublicNotes()) {
            noteText.put(note.id(), note.note());
        }

        model.addAttribute("services", down);
        model.addAttribute("latestLogs", latest);
        model.addAttribute("noteText", noteText);
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
        model.addAttribute("notes", notes.findAllByServiceId(id));
        if (!model.containsAttribute("noteText")) {
            model.addAttribute("noteText", "");
        }
        model.addAttribute("noteMaxLength", StatusNoteUtils.MAX_NOTE_LENGTH);
        return "service-diagnostic-details";
    }
}
