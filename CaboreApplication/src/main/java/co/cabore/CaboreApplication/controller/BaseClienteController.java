package co.cabore.CaboreApplication.controller;

/**
 * Base común de los controladores del módulo cliente: expone el id del
 * usuario autenticado y valida destinos de retorno contra open-redirect.
 */
public abstract class BaseClienteController {

    /** Id del usuario autenticado (garantizado por SecurityConfig: ROLE_CLIENTE). */
    protected Integer usuarioId() {
        return co.cabore.CaboreApplication.service.UsuarioActual.id();
    }

    /**
     * Destino de retorno seguro: solo se aceptan rutas internas que empiezan
     * por "/" y sin dobles barras (evita redirecciones a sitios externos).
     * Si viene vacío o inválido, se usa el destino por defecto.
     */
    protected String volverSeguro(String volver, String porDefecto) {
        if (volver == null || volver.isBlank()) {
            return porDefecto;
        }
        String v = volver.trim();
        if (!v.startsWith("/") || v.startsWith("//") || v.contains("://") || v.contains("\\")) {
            return porDefecto;
        }
        return v;
    }
}
