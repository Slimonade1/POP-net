package dk.pop.status.temp.view;

import dk.pop.status.temp.services.StatusMessageService;
import dk.pop.status.temp.domain.ServiceEntity;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminController {
    private final StatusMessageService messages;
    private final ServiceEntityRepository services;

    public AdminController(StatusMessageService messages, ServiceEntityRepository services) {
        this.messages = messages;
        this.services = services;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/admin")
    public String adminPage(Principal principal, Model model) {
        model.addAttribute("username", principal.getName());
        model.addAttribute("notices", messages.findAll());
        return "admin";
    }

    @GetMapping("/retry-thresholds")
    public String retryThresholds(Model model) {
        model.addAttribute("services", services.findAll());
        return "retry-thresholds";
    }

    @PostMapping("/admin/services/retry-threshold")
    public String updateRetryThreshold(
            @RequestParam long id,
            @RequestParam int retryThreshold,
            RedirectAttributes redirectAttributes) {
        if (retryThreshold < 1) {
            redirectAttributes.addFlashAttribute("error", "Retry threshold must be at least 1.");
            return "redirect:/retry-thresholds";
        }

        ServiceEntity service = services.findById(id).orElseThrow();
        services.save(new ServiceEntity(service.id(), service.name(), service.description(), retryThreshold));
        return "redirect:/retry-thresholds";
    }
}
