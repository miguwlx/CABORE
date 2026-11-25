package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.service.HttpGoogleTokenVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaginasController {

    private final String googleClientId;

    public PaginasController(
            @Value("${cabore.google.client-id:" + HttpGoogleTokenVerifier.DEFAULT_CLIENT_ID + "}") String googleClientId) {
        String id = googleClientId == null ? "" : googleClientId.trim();
        this.googleClientId = id.isEmpty() ? HttpGoogleTokenVerifier.DEFAULT_CLIENT_ID : id;
    }

    @GetMapping("/")
    public String inicio() {
        return "landing";
    }

    @GetMapping("/landing")
    public String landing() {
        return "landing";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("googleClientId", googleClientId);
        return "login";
    }

    @GetMapping("/registro")
    public String registro(Model model) {
        model.addAttribute("googleClientId", googleClientId);
        return "registro";
    }
}
