package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.Rol;
import co.cabore.CaboreApplication.model.Tienda;
import co.cabore.CaboreApplication.model.Usuario;
import co.cabore.CaboreApplication.repository.TiendaRepository;
import co.cabore.CaboreApplication.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Servicio de inicio de sesión y registro con Google (FASE 4 — Miguel).
 *
 * Migra google_auth.php:
 *  - Verifica el token de Google con GoogleTokenVerifier.
 *  - Busca usuario existente por google_id o por correo.
 *  - Si existe y está suspendido (activo = false), rechaza el acceso.
 *  - Si existe sin google_id, vincula su google_id.
 *  - Si no existe, crea la cuenta con el rol solicitado (cliente o emprendedor,
 *    nunca administrador) y, si es emprendedor, crea su tienda inicial.
 */
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleTokenVerifier tokenVerifier;
    private final UsuarioRepository usuarios;
    private final TiendaRepository tiendas;

    @Transactional
    public Usuario autenticar(String credential, String rolSolicitado) {
        GoogleTokenVerifier.DatosGoogle datos = tokenVerifier.verificar(credential);
        if (!datos.emailVerificado()) {
            throw new OperacionInvalidaException("El correo de Google no está verificado.");
        }

        Optional<Usuario> existente = usuarios.findByGoogleId(datos.googleId())
                .or(() -> usuarios.findByCorreo(datos.correo()))
                .or(() -> usuarios.findByCorreoIgnoreCase(datos.correo()));

        if (existente.isPresent()) {
            Usuario u = existente.get();
            if (!u.isActivo()) {
                throw new OperacionInvalidaException("Esta cuenta está suspendida.");
            }
            if (u.getGoogleId() == null || u.getGoogleId().isBlank()) {
                u.setGoogleId(datos.googleId());
                u = usuarios.save(u);
            }
            asegurarTiendaSiEsEmprendedor(u);
            return u;
        }

        Rol rolFinal = "emprendedor".equalsIgnoreCase(rolSolicitado == null ? "" : rolSolicitado.trim())
                ? Rol.emprendedor
                : Rol.cliente;

        Usuario nuevo = new Usuario();
        nuevo.setNombre(datos.nombre() == null || datos.nombre().isBlank() ? datos.correo() : datos.nombre().trim());
        nuevo.setCorreo(datos.correo().trim());
        nuevo.setContrasena(null);
        nuevo.setRol(rolFinal);
        nuevo.setGoogleId(datos.googleId());
        nuevo.setActivo(true);
        nuevo = usuarios.save(nuevo);

        asegurarTiendaSiEsEmprendedor(nuevo);
        return nuevo;
    }

    private void asegurarTiendaSiEsEmprendedor(Usuario u) {
        if (u.getRol() == Rol.emprendedor && tiendas.findByUsuarioId(u.getId()).isEmpty()) {
            Tienda t = new Tienda();
            t.setUsuario(u);
            tiendas.save(t);
        }
    }

    public static String destinoPorRol(Rol rol) {
        if (rol == null) return "/cliente";
        return switch (rol) {
            case emprendedor -> "/emprendedor";
            case administrador -> "/admin";
            default -> "/cliente";
        };
    }
}
