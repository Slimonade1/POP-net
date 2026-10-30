package dk.pop.status.temp.view;

import dk.pop.status.temp.services.StatusMonitoringService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StatusHomeController {

    private final StatusMonitoringService monitoringService;

    public StatusHomeController(StatusMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/")
    public String showStatusPage(Model model) {
        // 1. Pass the live health states map vector (e.g. {"Jane": "healthy", "POP Booking": "down"})
        model.addAttribute("liveStatuses", monitoringService.getLiveStatuses());
        
        // 2. Pass the live processing latency map vector (e.g. {"Jane": 0, "POP Booking": 3})
        model.addAttribute("liveLatencies", monitoringService.getLiveLatencies());
        
        // This returns the existing src/main/resources/templates/index.html layout
        return "index"; 
    }
}
