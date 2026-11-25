package co.cabore.CaboreApplication.config;

import co.cabore.CaboreApplication.model.Rol;
import co.cabore.CaboreApplication.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return bcrypt.encode(rawPassword);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
                    return false;
                }
                if (encodedPassword.startsWith("$2a$")
                        || encodedPassword.startsWith("$2b$")
                        || encodedPassword.startsWith("$2y$")) {
                    return bcrypt.matches(rawPassword, encodedPassword);
                }
                // Fallback de compatibilidad con login.php para cuentas legacy sin hash BCrypt
                return encodedPassword.equals(rawPassword.toString());
            }
        };
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Habilitado para toda la aplicación. Thymeleaf añade el hidden input
            // a los formularios th:action y las peticiones AJAX envían el token
            // de sesión en el header indicado por Spring Security.
            .csrf(Customizer.withDefaults())
            .securityContext(sc -> sc.securityContextRepository(securityContextRepository()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/landing", "/login", "/registro", "/tienda/**",
                                 "/olvide-password", "/olvide_password.html",
                                 "/restablecer-password", "/restablecer_password.php",
                                 "/auth/google", "/google_auth.php",
                                 "/crear-admin", "/crear_admin.php",
                                 "/CSS/**", "/JS/**", "/images/**", "/img/**", "/favicon.ico").permitAll()
                .requestMatchers("/chat", "/chat/**").hasAnyRole("CLIENTE", "EMPRENDEDOR")
                // FASE 3 (cliente): todo el módulo vive bajo /cliente/** y requiere rol cliente.
                // Las operaciones filtran además por el usuario autenticado en los servicios.
                .requestMatchers("/cliente", "/cliente/**").hasRole("CLIENTE")
                .requestMatchers("/emprendedor", "/emprendedor/**").hasRole("EMPRENDEDOR")
                .requestMatchers("/admin", "/admin/**").hasRole("ADMINISTRADOR")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("correo")
                .passwordParameter("contrasena")
                .successHandler(exitoLogin())
                .failureHandler(falloLogin())
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler exitoLogin() {
        return (req, res, auth) -> {
            Rol rol = ((UsuarioPrincipal) auth.getPrincipal()).getRol();
            String destino = switch (rol) {
                case emprendedor -> "/emprendedor";
                case administrador -> "/admin";
                default -> "/cliente";
            };
            res.sendRedirect(destino);
        };
    }

    @Bean
    public AuthenticationFailureHandler falloLogin() {
        return (req, res, ex) -> {
            if (ex instanceof DisabledException) {
                res.sendRedirect("/login?error=suspendida");
                return;
            }
            String panel = "emprendedor".equals(req.getParameter("tipo")) ? "empren" : "cliente";
            res.sendRedirect("/login?error=1&panel=" + panel);
        };
    }
}
