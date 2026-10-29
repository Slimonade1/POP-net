package dk.pop.status.temp.view;

import dk.pop.status.temp.services.StatusNoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StatusNoteController {
    private final StatusNoteService service;

    public StatusNoteController(StatusNoteService service) {
        this.service = service;
    }

    @GetMapping("/notes")
    public String notes(Model model) {
        model.addAttribute("notes", service.findAll());
        return "notes";
    }

    @PostMapping("/notes")
    public String createNote(@RequestParam String description, @RequestParam long serviceId,
                             RedirectAttributes redirectAttributes) {
        try {
            service.create(description, serviceId);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/notes";
    }

    @PostMapping("/notes/update")
    public String updateNote(@RequestParam long id, @RequestParam String description,
                             @RequestParam long serviceId, RedirectAttributes redirectAttributes) {
        try {
            if (!service.update(id, description, serviceId)) {
                redirectAttributes.addFlashAttribute("error", "Status note not found.");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/notes";
    }

    @PostMapping("/notes/delete")
    public String deleteNote(@RequestParam long id, RedirectAttributes redirectAttributes) {
        if (!service.delete(id)) {
            redirectAttributes.addFlashAttribute("error", "Status note not found.");
        }
        return "redirect:/notes";
    }
}
