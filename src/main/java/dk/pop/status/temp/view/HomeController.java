package dk.pop.status.temp.view;

import dk.pop.status.temp.services.StatusMessageService;
import dk.pop.status.temp.services.StatusNoteService;
import dk.pop.status.temp.services.StatusMonitoringService; // Used to update liveStatuses and liveLatencies
import dk.pop.status.temp.view.status.StatusPages;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class HomeController {
    private final StatusMessageService messages;
    private final StatusPages pages;
    private final StatusMonitoringService monitoringService; // Used to update liveStatuses and liveLatencies
    private final StatusNoteService notes;

    public HomeController(StatusMessageService messages, StatusPages pages, StatusMonitoringService monitoringService,
                          StatusNoteService notes) {
        this.messages = messages;
        this.pages = pages;
        this.monitoringService = monitoringService;
        this.notes = notes;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("notices", messages.findAll());
        model.addAttribute("serviceUpdates", notes.findPublicNotes());
        
        model.addAttribute("liveStatuses", monitoringService.getLiveStatuses());
        model.addAttribute("liveLatencies", monitoringService.getLiveLatencies());
        
        pages.home(model);
        return "home";
    }

    @GetMapping("/services/id/{id}")
    public String publicService(@PathVariable long id, Model model) {
        model.addAttribute("service", notes.findPublicService(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "public-service-note";
    }

    @GetMapping("/services/{slug}")
    public String service(@PathVariable String slug, Model model) {
        if (!pages.service(slug, model)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "service";
    }
}
