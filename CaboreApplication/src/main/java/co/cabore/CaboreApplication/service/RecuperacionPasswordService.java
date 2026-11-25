package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.Usuario;
import co.cabore.CaboreApplication.repository.UsuarioRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Servicio de recuperación y restablecimiento de contraseña (FASE 4 — Miguel).
 *
 * Migra olvide_password.html, enviar_recuperacion.php y restablecer_password.php:
 *  - Token aleatorio criptográficamente seguro de 32 bytes (64 hex chars).
 *  - Expiración de 1 hora en usuarios.reset_expira.
 *  - No expone si un correo está registrado o no.
 *  - Invalida el token inmediatamente después de cambiar la contraseña.
 *  - Envía el correo con Spring Mail cuando hay credenciales SMTP configuradas.
 */
@Service
public class RecuperacionPasswordService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacionPasswordService.class);
    private static final String REGEX_EMAIL = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    public static final String MENSAJE_GENERICO =
            "Si el correo existe en nuestra base de datos, te enviamos un enlace de recuperación.";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String mailUsername;
    private final String appBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public RecuperacionPasswordService(
            UsuarioRepository usuarios,
            PasswordEncoder passwordEncoder,
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${cabore.mail.from:no-reply@cabore.co}") String mailFrom,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${cabore.app.base-url:http://localhost:8080}") String appBaseUrl) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.mailUsername = mailUsername;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Solicita la recuperación de contraseña para un correo.
     * Valida formato de correo; si existe la cuenta genera token de 64 chars
     * con vigencia de 1 hora e intenta enviar el correo por SMTP.
     */
    @Transactional
    public void solicitarRecuperacion(String correoRaw) {
        String correo = correoRaw == null ? "" : correoRaw.trim();
        if (correo.isEmpty() || !correo.matches(REGEX_EMAIL)) {
            throw new OperacionInvalidaException("Ingresa un correo válido.");
        }

        Optional<Usuario> usuarioOpt = usuarios.findByCorreo(correo)
                .or(() -> usuarios.findByCorreoIgnoreCase(correo));
        if (usuarioOpt.isEmpty()) {
            // Por seguridad no revelamos que el correo no existe
            return;
        }

        Usuario usuario = usuarioOpt.get();
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);

        usuario.setResetToken(token);
        usuario.setResetExpira(LocalDateTime.now().plusHours(1));
        usuarios.save(usuario);

        String base = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        String enlace = base + "/restablecer-password?token=" + token;
        enviarCorreoRecuperacion(usuario.getCorreo(), usuario.getNombre(), enlace);
    }

    @Transactional(readOnly = true)
    public boolean esTokenValido(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return usuarios.findByResetToken(token.trim())
                .filter(u -> u.getResetExpira() != null && u.getResetExpira().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    @Transactional
    public void restablecerContrasena(String tokenRaw, String nueva, String confirmar) {
        String token = tokenRaw == null ? "" : tokenRaw.trim();
        if (token.isEmpty()) {
            throw new OperacionInvalidaException("El enlace expiró o no es válido. Solicita uno nuevo.");
        }

        Usuario usuario = usuarios.findByResetToken(token)
                .filter(u -> u.getResetExpira() != null && u.getResetExpira().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new OperacionInvalidaException(
                        "El enlace expiró o no es válido. Solicita uno nuevo."));

        if (nueva == null || nueva.length() < 8) {
            throw new OperacionInvalidaException("La contraseña debe tener al menos 8 caracteres.");
        }
        if (!nueva.equals(confirmar)) {
            throw new OperacionInvalidaException("Las contraseñas no coinciden.");
        }

        usuario.setContrasena(passwordEncoder.encode(nueva));
        // Invalidar token inmediatamente para impedir su reutilización
        usuario.setResetToken(null);
        usuario.setResetExpira(null);
        usuarios.save(usuario);
    }

    private void enviarCorreoRecuperacion(String destinatario, String nombre, String enlace) {
        if (mailSender == null || mailUsername == null || mailUsername.isBlank()) {
            log.info("SMTP no configurado (MAIL_USERNAME vacío). Enlace de recuperación generado en BD para {}: {}", destinatario, enlace);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(mailFrom, "Caboré Marketplace");
            helper.setTo(destinatario);
            helper.setSubject("Recupera tu contraseña — Caboré");
            String html = """
                    <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto">
                      <h2 style="color:#b8923c">Caboré</h2>
                      <p>Hola %s,</p>
                      <p>Recibimos una solicitud para restablecer tu contraseña. Haz clic en el siguiente botón (válido por 1 hora):</p>
                      <p style="text-align:center;margin:28px 0">
                        <a href="%s" style="background:#b8923c;color:#fff;padding:14px 28px;border-radius:10px;text-decoration:none;font-weight:600">Restablecer contraseña</a>
                      </p>
                      <p style="font-size:12px;color:#888">Si no solicitaste esto, ignora este correo — tu contraseña seguirá siendo la misma.</p>
                    </div>
                    """.formatted(escaparHtml(nombre), enlace);
            helper.setText("Restablece tu contraseña en: " + enlace, html);
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("No fue posible enviar el correo SMTP de recuperación a {}: {}", destinatario, e.getMessage());
        }
    }

    private static String escaparHtml(String s) {
        if (s == null) return "Usuario";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
