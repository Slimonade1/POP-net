package dk.pop.status.temp.view;

import dk.pop.status.temp.services.StatusMessageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final StatusMessageService messages;

    public HomeController(StatusMessageService messages) {
        this.messages = messages;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("notices", messages.findAll());
        return "home";
    }

    @GetMapping("/service")
    public String service() {
        return "service";
    }
}
