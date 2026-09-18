package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.Rol;
import co.cabore.CaboreApplication.model.Tienda;
import co.cabore.CaboreApplication.model.Usuario;
import co.cabore.CaboreApplication.repository.TiendaRepository;
import co.cabore.CaboreApplication.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistroService {

    private final UsuarioRepository usuarios;
    private final TiendaRepository tiendas;
    private final PasswordEncoder encoder;

    @Transactional
    public Usuario registrar(String nombre, String correo, String contrasena, Rol rol) {
        if (nombre.length() < 3) {
            throw new RegistroException("Ingresa tu nombre completo (mínimo 3 caracteres).");
        }
        if (!correo.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new RegistroException("Ingresa un correo electrónico válido.");
        }
        if (contrasena.length() < 6) {
            throw new RegistroException("La contraseña debe tener al menos 6 caracteres.");
        }
        if (usuarios.existsByCorreo(correo)) {
            throw new RegistroException("Este correo ya está registrado.");
        }

        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setCorreo(correo);
        u.setContrasena(encoder.encode(contrasena));
        u.setRol(rol);
        usuarios.save(u);

        if (rol == Rol.emprendedor) {
            Tienda t = new Tienda();
            t.setUsuario(u);
            tiendas.save(t);
        }
        return u;
    }
}