package dk.pop.status.temp.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AdminController {

    @GetMapping("/admin")
    @ResponseBody
    public String adminPage() {
        return "Du er logget ind som admin!";
    }
}

