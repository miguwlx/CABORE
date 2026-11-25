package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        String c = correo == null ? "" : correo.trim();
        return usuarios.findByCorreo(c)
                .or(() -> usuarios.findByCorreoIgnoreCase(c))
                .map(UsuarioPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No existe: " + correo));
    }
}