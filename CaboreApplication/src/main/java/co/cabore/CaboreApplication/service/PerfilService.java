package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.Usuario;
import co.cabore.CaboreApplication.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Perfil del cliente autenticado: datos personales y contraseña.
 * Todas las escrituras aplican sobre el usuario autenticado; la contraseña
 * se verifica y cifra con el mismo BCrypt usado en el registro (coordina con
 * el módulo de autenticación de Yulieth: usuarios.contrasena + PasswordEncoder).
 */
@Service
@RequiredArgsConstructor
public class PerfilService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    @Transactional(readOnly = true)
    public Usuario usuario(Integer usuarioId) {
        return usuarios.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado."));
    }

    /** Actualiza nombre y datos de contacto/entrega del propio usuario. */
    @Transactional
    public void actualizarDatos(Integer usuarioId, String nombre, String telefono,
                                String ciudad, String direccion) {
        Usuario u = usuario(usuarioId);
        if (nombre == null || nombre.trim().length() < 3) {
            throw new OperacionInvalidaException("Ingresa tu nombre completo (mínimo 3 caracteres).");
        }
        if (telefono != null && !telefono.isBlank() && !telefono.matches("^[0-9+\\-() ]{7,20}$")) {
            throw new OperacionInvalidaException("El teléfono no tiene un formato válido.");
        }
        u.setNombre(nombre.trim());
        u.setTelefono(telefono == null ? null : telefono.trim());
        u.setCiudad(ciudad == null ? null : ciudad.trim());
        u.setDireccion(direccion == null ? null : direccion.trim());
        usuarios.save(u);
    }

    /**
     * Cambio de contraseña:
     * - Si el usuario ya tiene contraseña (registro normal), debe confirmar la actual.
     * - Si se registró con Google (contraseña nula), puede fijar una nueva sin "actual".
     * - La nueva debe tener mínimo 6 caracteres y coincidir con la confirmación
     *   (mismas reglas de RegistroService).
     */
    @Transactional
    public void cambiarContrasena(Integer usuarioId, String actual, String nueva, String confirmacion) {
        Usuario u = usuario(usuarioId);
        boolean tieneContrasena = u.getContrasena() != null && !u.getContrasena().isBlank();
        if (tieneContrasena && (actual == null || !encoder.matches(actual, u.getContrasena()))) {
            throw new OperacionInvalidaException("La contraseña actual no es correcta.");
        }
        if (nueva == null || nueva.length() < 6) {
            throw new OperacionInvalidaException("La nueva contraseña debe tener al menos 6 caracteres.");
        }
        if (!nueva.equals(confirmacion)) {
            throw new OperacionInvalidaException("La confirmación no coincide con la nueva contraseña.");
        }
        u.setContrasena(encoder.encode(nueva));
        usuarios.save(u);
    }

    @Transactional(readOnly = true)
    public boolean tieneContrasena(Integer usuarioId) {
        Usuario u = usuario(usuarioId);
        return u.getContrasena() != null && !u.getContrasena().isBlank();
    }
}
