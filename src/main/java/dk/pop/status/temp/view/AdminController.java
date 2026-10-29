package dk.pop.status.temp.view;

import dk.pop.status.temp.services.StatusMessageService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {
    private final StatusMessageService messages;

    public AdminController(StatusMessageService messages) {
        this.messages = messages;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/admin")
    public String adminPage(Principal principal, Model model) {
        model.addAttribute("username", principal.getName());
        model.addAttribute("announcementCount", messages.findAll().size());
        return "admin";
    }
}
