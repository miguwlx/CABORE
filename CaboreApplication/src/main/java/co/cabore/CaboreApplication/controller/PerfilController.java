package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.Usuario;
import co.cabore.CaboreApplication.repository.PedidoRepository;
import co.cabore.CaboreApplication.service.PerfilService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Perfil del usuario autenticado: datos personales, dirección de entrega y
 * contraseña. No recibe id de usuario por parámetro: SIEMPRE opera sobre la
 * sesión activa.
 */
@Controller
@RequestMapping("/cliente/perfil")
@RequiredArgsConstructor
public class PerfilController extends BaseClienteController {

    private final PerfilService perfil;
    private final PedidoRepository pedidos;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping
    public String ver(Model model) {
        Integer uid = usuarioId();
        var listaPedidos = pedidos.findByUsuarioIdOrderByCreadoEnDesc(uid);
        var resumen = co.cabore.CaboreApplication.service.PedidoService.calcularResumen(listaPedidos);
        model.addAttribute("usuario", perfil.usuario(uid));
        model.addAttribute("tieneContrasena", perfil.tieneContrasena(uid));
        model.addAttribute("totalPedidos", listaPedidos.size());
        model.addAttribute("resumenPedidos", resumen);
        return "perfil";
    }

    @PostMapping("/datos")
    public String actualizarDatos(@RequestParam("nombre") String nombre,
                                  @RequestParam(name = "telefono", required = false) String telefono,
                                  @RequestParam(name = "ciudad", required = false) String ciudad,
                                  @RequestParam(name = "direccion", required = false) String direccion,
                                  HttpServletRequest req,
                                  HttpServletResponse res,
                                  RedirectAttributes flash) {
        Integer uid = usuarioId();
        perfil.actualizarDatos(uid, nombre, telefono, ciudad, direccion);

        // Actualizar el UsuarioPrincipal en la sesión activa para que la barra superior
        // refleje inmediatamente el nuevo nombre e inicial sin requerir cerrar sesión.
        Usuario actualizado = perfil.usuario(uid);
        UsuarioPrincipal nuevoPrincipal = new UsuarioPrincipal(actualizado);
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                nuevoPrincipal, null, nuevoPrincipal.getAuthorities());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        securityContextRepository.saveContext(ctx, req, res);

        flash.addFlashAttribute("flashOk", "Tus datos fueron actualizados.");
        return "redirect:/cliente/perfil";
    }

    @PostMapping("/contrasena")
    public String cambiarContrasena(@RequestParam(name = "actual", required = false) String actual,
                                    @RequestParam("nueva") String nueva,
                                    @RequestParam("confirmar") String confirmar,
                                    RedirectAttributes flash) {
        perfil.cambiarContrasena(usuarioId(), actual, nueva, confirmar);
        flash.addFlashAttribute("flashOk", "Tu contraseña fue actualizada correctamente.");
        return "redirect:/cliente/perfil";
    }
}
