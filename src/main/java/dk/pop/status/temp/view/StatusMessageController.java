package dk.pop.status.temp.view;

import dk.pop.status.temp.services.StatusMessageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StatusMessageController {
    private final StatusMessageService service;

    public StatusMessageController(StatusMessageService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("messages", service.findAll());
        return "index";
    }

    @PostMapping("/messages")
    public String addMessage(
            @RequestParam(name = "message", required = false) String message,
            RedirectAttributes redirectAttributes) {
        try {
            service.add(message);
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/messages/delete")
    public String deleteMessage(@RequestParam long id) {
        service.delete(id);
        return "redirect:/";
    }
}
