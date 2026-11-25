package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.Rol;
import co.cabore.CaboreApplication.repository.MensajeRepository;
import co.cabore.CaboreApplication.service.CarritoService;
import co.cabore.CaboreApplication.service.CheckoutException;
import co.cabore.CaboreApplication.service.OperacionInvalidaException;
import co.cabore.CaboreApplication.service.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.NoSuchElementException;

/**
 * Atributos comunes y manejo de errores para las vistas del cliente.
 *
 * - Agrega el nombre del usuario y el contador del carrito a la barra superior.
 * - Convierte errores de negocio (producto inexistente, operación no permitida,
 *   problema de compra) en redirecciones al catálogo con un mensaje visible,
 *   sin exponer trazas internas (coherente con server.error.include-* = never).
 */
@ControllerAdvice(assignableTypes = {
        ClienteController.class,
        CarritoController.class,
        CheckoutController.class,
        PedidosController.class,
        FavoritosController.class,
        PerfilController.class,
        ResenasController.class
})
@RequiredArgsConstructor
public class ClienteModelAdvice {

    private final CarritoService carrito;
    private final MensajeRepository mensajes;
    private final co.cabore.CaboreApplication.service.FavoritoService favoritos;

    @ModelAttribute
    public void comunes(Model model) {
        UsuarioPrincipal up = UsuarioActual.principal();
        if (up == null || up.getRol() != Rol.cliente) {
            return;
        }
        model.addAttribute("usuarioNombre", up.getNombre());
        model.addAttribute("usuarioInicial",
                up.getNombre() == null || up.getNombre().isBlank() ? "C"
                        : up.getNombre().trim().substring(0, 1).toUpperCase());
        model.addAttribute("correoSesion", up.getUsername());
        model.addAttribute("carritoCount", (int) carrito.contar(up.getId()));
        model.addAttribute("mensajesNoLeidosCount", (int) mensajes.contarNoLeidosCliente(up.getId()));
        model.addAttribute("favoritosCount", favoritos.idsFavoritos(up.getId()).size());
    }

    /* ── Errores de negocio → redirect con aviso ─────────────────────────── */

    @ExceptionHandler({NoSuchElementException.class, CheckoutException.class, OperacionInvalidaException.class})
    public String errorNegocio(RuntimeException ex, HttpServletRequest req, RedirectAttributes flash) {
        flash.addFlashAttribute("flashErr", ex.getMessage() == null
                ? "La operación no pudo completarse." : ex.getMessage());
        return "redirect:" + destinoSeguro(req);
    }

    /**
     * Vuelve a la página de origen si la URL Referer es interna y pertenece al
     * módulo del cliente; en caso contrario, al catálogo. Nunca a un destino
     * externo (anti open-redirect).
     */
    private String destinoSeguro(HttpServletRequest req) {
        String ref = req.getHeader("Referer");
        if (ref != null) {
            try {
                java.net.URI uri = java.net.URI.create(ref);
                String path = uri.getPath();
                if (path != null && (path.startsWith("/cliente") || path.startsWith("/tienda/"))) {
                    return path + (uri.getQuery() == null ? "" : "?" + uri.getQuery());
                }
            } catch (IllegalArgumentException ignored) {
                // Referer malformado: se ignora y se cae al catálogo.
            }
        }
        return "/cliente";
    }
}
