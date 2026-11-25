package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.model.Rol;
import co.cabore.CaboreApplication.model.Usuario;
import co.cabore.CaboreApplication.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Migración de crear_admin.php a Spring Boot.
 *
 * Permite crear una cuenta con rol "administrador" de forma controlada mediante
 * una clave de instalación definida en la variable de entorno ADMIN_SETUP_KEY
 * (propiedad cabore.admin.setup-key). Nunca permite crear administradores sin
 * que ADMIN_SETUP_KEY esté configurada y coincida exactamente.
 */
@Controller
public class CrearAdminController {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final String setupKey;

    public CrearAdminController(
            UsuarioRepository usuarios,
            PasswordEncoder passwordEncoder,
            @Value("${cabore.admin.setup-key:}") String setupKey) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.setupKey = setupKey == null ? "" : setupKey.trim();
    }

    @GetMapping({"/crear-admin", "/crear_admin.php"})
    public String formulario(Model model) {
        model.addAttribute("exito", false);
        model.addAttribute("habilitado", !setupKey.isEmpty());
        return "crear-admin";
    }

    @PostMapping({"/crear-admin", "/crear_admin.php"})
    public String crear(
            @RequestParam(name = "clave_instalacion", defaultValue = "") String clave,
            @RequestParam(name = "nombre", defaultValue = "") String nombreRaw,
            @RequestParam(name = "correo", defaultValue = "") String correoRaw,
            @RequestParam(name = "contrasena", defaultValue = "") String contrasena,
            Model model) {

        String nombre = nombreRaw.trim();
        String correo = correoRaw.trim();
        model.addAttribute("habilitado", !setupKey.isEmpty());

        if (setupKey.isEmpty() || !setupKey.equals(clave)) {
            model.addAttribute("exito", false);
            model.addAttribute("mensaje", "Clave de instalación incorrecta.");
            return "crear-admin";
        }
        if (nombre.isEmpty() || correo.isEmpty() || contrasena.length() < 8) {
            model.addAttribute("exito", false);
            model.addAttribute("mensaje",
                    "Completa nombre y correo, y usa una contraseña de al menos 8 caracteres.");
            return "crear-admin";
        }
        if (usuarios.findByCorreo(correo).isPresent()) {
            model.addAttribute("exito", false);
            model.addAttribute("mensaje", "Ese correo ya está registrado.");
            return "crear-admin";
        }

        Usuario admin = new Usuario();
        admin.setNombre(nombre);
        admin.setCorreo(correo);
        admin.setContrasena(passwordEncoder.encode(contrasena));
        admin.setRol(Rol.administrador);
        admin.setActivo(true);
        usuarios.save(admin);

        model.addAttribute("exito", true);
        model.addAttribute("mensaje",
                "✅ Cuenta de administrador creada correctamente. Ya puedes iniciar sesión en /login con este correo.");
        return "crear-admin";
    }
}
