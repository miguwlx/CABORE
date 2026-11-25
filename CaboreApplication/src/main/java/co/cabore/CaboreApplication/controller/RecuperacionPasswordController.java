package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.service.OperacionInvalidaException;
import co.cabore.CaboreApplication.service.RecuperacionPasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controlador de recuperación y restablecimiento de contraseña (FASE 4 — Miguel).
 *
 * Migra:
 *  - olvide_password.html      -> GET /olvide-password (y alias /olvide_password.html)
 *  - enviar_recuperacion.php   -> POST /olvide-password
 *  - restablecer_password.php  -> GET y POST /restablecer-password
 */
@Controller
@RequiredArgsConstructor
public class RecuperacionPasswordController {

    private final RecuperacionPasswordService recuperacionService;

    @GetMapping({"/olvide-password", "/olvide_password.html"})
    public String formularioOlvide() {
        return "olvide-password";
    }

    @PostMapping("/olvide-password")
    public String procesarSolicitud(@RequestParam(name = "correo", defaultValue = "") String correo,
                                    Model model) {
        try {
            recuperacionService.solicitarRecuperacion(correo);
            model.addAttribute("esError", false);
            model.addAttribute("mensaje", RecuperacionPasswordService.MENSAJE_GENERICO);
        } catch (OperacionInvalidaException e) {
            model.addAttribute("esError", true);
            model.addAttribute("mensaje", e.getMessage());
        }
        return "recuperacion-enviada";
    }

    @GetMapping({"/restablecer-password", "/restablecer_password.php"})
    public String formularioRestablecer(@RequestParam(name = "token", defaultValue = "") String token,
                                        Model model) {
        if (token == null || token.isBlank()) {
            return "redirect:/olvide-password";
        }
        boolean valido = recuperacionService.esTokenValido(token);
        model.addAttribute("token", token.trim());
        model.addAttribute("tokenValido", valido);
        model.addAttribute("exito", false);
        return "restablecer-password";
    }

    @PostMapping({"/restablecer-password", "/restablecer_password.php"})
    public String procesarRestablecimiento(
            @RequestParam(name = "token", defaultValue = "") String token,
            @RequestParam(name = "nueva_contrasena", defaultValue = "") String nueva,
            @RequestParam(name = "confirmar_contrasena", defaultValue = "") String confirmar,
            Model model) {

        if (token == null || token.isBlank()) {
            return "redirect:/olvide-password";
        }

        try {
            recuperacionService.restablecerContrasena(token, nueva, confirmar);
            model.addAttribute("token", "");
            model.addAttribute("tokenValido", true);
            model.addAttribute("exito", true);
        } catch (OperacionInvalidaException e) {
            boolean sigueValido = recuperacionService.esTokenValido(token);
            model.addAttribute("token", token.trim());
            model.addAttribute("tokenValido", sigueValido);
            model.addAttribute("exito", false);
            model.addAttribute("error", e.getMessage());
        }
        return "restablecer-password";
    }
}
