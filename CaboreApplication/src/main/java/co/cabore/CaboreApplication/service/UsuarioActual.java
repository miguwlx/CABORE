package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.Rol;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilidad central para leer al usuario autenticado desde el contexto de
 * seguridad. Todas las operaciones del módulo cliente (carrito, checkout,
 * pedidos, favoritos, reseñas, perfil y facturas) se ejecutan SIEMPRE sobre
 * el id del usuario autenticado — nunca sobre un id enviado por el navegador.
 */
public final class UsuarioActual {

    private UsuarioActual() {}

    /** Devuelve el principal autenticado o null si no hay sesión. */
    public static UsuarioPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal up) {
            return up;
        }
        return null;
    }

    /** Id del usuario autenticado. Lanza IllegalStateException si no hay sesión. */
    public static Integer id() {
        UsuarioPrincipal up = principal();
        if (up == null) {
            throw new IllegalStateException("No hay un usuario autenticado.");
        }
        return up.getId();
    }

    public static boolean esCliente() {
        UsuarioPrincipal up = principal();
        return up != null && up.getRol() == Rol.cliente;
    }
}
