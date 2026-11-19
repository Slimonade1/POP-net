package dk.pop.status.temp.view;

import dk.pop.status.temp.domain.WebhookDestination;
import dk.pop.status.temp.services.WebhookDestinationService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.NoSuchElementException;

@Controller
public class WebhookNotificationController {
    private final WebhookDestinationService destinations;

    public WebhookNotificationController(WebhookDestinationService destinations) {
        this.destinations = destinations;
    }

    @GetMapping("/admin/webhooks")
    public String settings(Model model) {
        model.addAttribute("destinations", destinations.findAll());
        model.addAttribute("providers", WebhookDestination.Provider.values());
        return "webhook-notifications";
    }

    @PostMapping("/admin/webhooks")
    public String create(@RequestParam(required = false) String name,
                         @RequestParam(required = false) String provider,
                         @RequestParam(required = false) String url, RedirectAttributes redirect) {
        destinations.create(name, provider, url);
        redirect.addFlashAttribute("success", "Destination saved. Enable it when you are ready to receive alerts.");
        return "redirect:/admin/webhooks";
    }

    @PostMapping("/admin/webhooks/{id}/update")
    public String update(@PathVariable long id, @RequestParam(required = false) String name,
                         @RequestParam(required = false) String provider,
                         @RequestParam(required = false) String url, RedirectAttributes redirect) {
        destinations.update(id, name, provider, url);
        redirect.addFlashAttribute("success", "Destination updated.");
        return "redirect:/admin/webhooks";
    }

    @PostMapping("/admin/webhooks/{id}/enabled")
    public String enabled(@PathVariable long id, @RequestParam boolean enabled, RedirectAttributes redirect) {
        destinations.setEnabled(id, enabled);
        redirect.addFlashAttribute("success", enabled ? "Destination enabled." : "Destination disabled.");
        return "redirect:/admin/webhooks";
    }

    @PostMapping("/admin/webhooks/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirect) {
        destinations.delete(id);
        redirect.addFlashAttribute("success", "Destination removed.");
        return "redirect:/admin/webhooks";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String invalid(IllegalArgumentException exception, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", exception.getMessage());
        return "redirect:/admin/webhooks";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }
}
