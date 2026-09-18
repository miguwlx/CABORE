package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.Rol;
import co.cabore.CaboreApplication.model.Usuario;
import co.cabore.CaboreApplication.service.RegistroException;
import co.cabore.CaboreApplication.service.RegistroService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class RegistroController {

    private final RegistroService registroService;
    private final SecurityContextRepository contexto;

    @PostMapping("/registro")
    public String registrar(@RequestParam String nombre,
                            @RequestParam String correo,
                            @RequestParam String contrasena,
                            @RequestParam(defaultValue = "cliente") String rol,
                            HttpServletRequest req,
                            HttpServletResponse res,
                            RedirectAttributes flash) {
        try {
            Rol elegido = "emprendedor".equals(rol) ? Rol.emprendedor : Rol.cliente;
            Usuario u = registroService.registrar(nombre.trim(), correo.trim(), contrasena, elegido);

            iniciarSesion(new UsuarioPrincipal(u), req, res);
            return "redirect:" + (elegido == Rol.emprendedor ? "/emprendedor" : "/cliente");
        } catch (RegistroException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/registro";
        }
    }

    private void iniciarSesion(UsuarioPrincipal p, HttpServletRequest req, HttpServletResponse res) {
        var auth = UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contexto.saveContext(ctx, req, res);
    }
}