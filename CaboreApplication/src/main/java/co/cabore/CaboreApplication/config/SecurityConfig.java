package co.cabore.CaboreApplication.config;

import co.cabore.CaboreApplication.model.Rol;
import co.cabore.CaboreApplication.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
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
        return new BCryptPasswordEncoder();
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
            .csrf(csrf -> csrf.disable()) // pendiente reactivar cuando los formularios tengan token CSRF
            .securityContext(sc -> sc.securityContextRepository(securityContextRepository()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/landing", "/login", "/registro",
                                 "/CSS/**", "/JS/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/cliente", "/tienda/**").hasRole("CLIENTE")
                .requestMatchers("/emprendedor/**").hasRole("EMPRENDEDOR")
                .requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
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