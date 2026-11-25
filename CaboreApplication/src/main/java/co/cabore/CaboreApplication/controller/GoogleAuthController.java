package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.Usuario;
import co.cabore.CaboreApplication.service.GoogleAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controlador de autenticación con Google (FASE 4 — Miguel).
 * Atiende POST /auth/google (y el alias legacy /google_auth.php).
 */
@Controller
@RequiredArgsConstructor
public class GoogleAuthController {

    private final GoogleAuthService googleAuthService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping({"/auth/google", "/google_auth.php"})
    @ResponseBody
    public Map<String, Object> autenticarConGoogle(
            @RequestParam(name = "credential", defaultValue = "") String credential,
            @RequestParam(name = "rol", defaultValue = "cliente") String rol,
            HttpServletRequest req,
            HttpServletResponse res) {

        try {
            Usuario usuario = googleAuthService.autenticar(credential, rol);
            UsuarioPrincipal principal = new UsuarioPrincipal(usuario);

            var auth = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, principal.getAuthorities());
            var ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);
            securityContextRepository.saveContext(ctx, req, res);

            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("ok", true);
            ok.put("redirect", GoogleAuthService.destinoPorRol(usuario.getRol()));
            return ok;
        } catch (RuntimeException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage() == null ? "No se pudo continuar con Google." : e.getMessage());
            return err;
        }
    }
}
