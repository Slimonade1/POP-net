package dk.pop.status.temp.view;

import dk.pop.status.temp.services.StatusMessageService;
import dk.pop.status.temp.domain.ServiceEntity;
import dk.pop.status.temp.domain.EmailRecipient;
import dk.pop.status.temp.repository.EmailRecipientRepository;
import dk.pop.status.temp.repository.ServiceEntityRepository;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.regex.Pattern;

@Controller
public class AdminController {
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final StatusMessageService messages;
    private final ServiceEntityRepository services;
    private final EmailRecipientRepository emailRecipients;

    public AdminController(
            StatusMessageService messages,
            ServiceEntityRepository services,
            EmailRecipientRepository emailRecipients) {
        this.messages = messages;
        this.services = services;
        this.emailRecipients = emailRecipients;
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

    @GetMapping("/email-notifications")
    public String emailNotifications(Model model) {
        model.addAttribute("recipients", emailRecipients.findAll());
        return "email-notifications";
    }

    @PostMapping("/admin/email-notifications")
    public String addEmailRecipient(
            @RequestParam String email,
            RedirectAttributes redirectAttributes) {
        String normalizedEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            redirectAttributes.addFlashAttribute("error", "Please enter a valid email address.");
            return "redirect:/email-notifications";
        }

        if (emailRecipients.findByEmail(normalizedEmail).isEmpty()) {
            emailRecipients.save(new EmailRecipient(null, normalizedEmail));
        }
        return "redirect:/email-notifications";
    }

    @PostMapping("/admin/email-notifications/delete")
    public String deleteEmailRecipient(@RequestParam long id) {
        emailRecipients.deleteById(id);
        return "redirect:/email-notifications";
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
