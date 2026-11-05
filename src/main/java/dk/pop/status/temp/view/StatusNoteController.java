package dk.pop.status.temp.view;

import dk.pop.status.temp.services.StatusNoteService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.NoSuchElementException;

@Controller
public class StatusNoteController {
    private final StatusNoteService notes;

    public StatusNoteController(StatusNoteService notes) {
        this.notes = notes;
    }

    @PostMapping("/admin/services/{id}/note")
    public String save(@PathVariable long id,
                       @RequestParam(required = false) String description,
                       RedirectAttributes redirectAttributes) {
        try {
            notes.save(id, description);
            redirectAttributes.addFlashAttribute("noteSuccess", "Public note saved.");
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("noteError", exception.getMessage());
            redirectAttributes.addFlashAttribute("noteText", description == null ? "" : description);
        }
        return "redirect:/admin/services/" + id;
    }

    @PostMapping("/admin/services/{id}/note/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirectAttributes) {
        try {
            notes.delete(id);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        redirectAttributes.addFlashAttribute("noteSuccess", "Public note removed.");
        return "redirect:/admin/services/" + id;
    }
}
