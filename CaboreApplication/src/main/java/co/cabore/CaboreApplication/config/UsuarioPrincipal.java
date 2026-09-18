package co.cabore.CaboreApplication.config;

import co.cabore.CaboreApplication.model.Rol;
import co.cabore.CaboreApplication.model.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UsuarioPrincipal implements UserDetails {

    private final Integer id;
    private final String nombre;
    private final String correo;
    private final String contrasena;
    private final Rol rol;
    private final boolean activo;

    public UsuarioPrincipal(Usuario u) {
        this.id = u.getId();
        this.nombre = u.getNombre();
        this.correo = u.getCorreo();
        this.contrasena = u.getContrasena();
        this.rol = u.getRol();
        this.activo = u.isActivo();
    }

    public Integer getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public Rol getRol() {
        return rol;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name().toUpperCase()));
    }

    @Override
    public String getPassword() {
        return contrasena;
    }

    @Override
    public String getUsername() {
        return correo;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }
}