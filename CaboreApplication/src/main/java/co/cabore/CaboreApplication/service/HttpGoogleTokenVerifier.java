package co.cabore.CaboreApplication.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Verificador real de tokens de Google mediante el endpoint oficial
 * https://oauth2.googleapis.com/tokeninfo?id_token=..., igual que google_auth.php.
 */
@Component
public class HttpGoogleTokenVerifier implements GoogleTokenVerifier {

    public static final String DEFAULT_CLIENT_ID =
            "853981485167-vsbi9h44ktc32d6c6ppipnls9fc6iqh7.apps.googleusercontent.com";

    private final String googleClientId;
    private final JsonMapper jsonMapper;

    public HttpGoogleTokenVerifier(
            @Value("${cabore.google.client-id:" + DEFAULT_CLIENT_ID + "}") String googleClientId,
            JsonMapper jsonMapper) {
        String id = googleClientId == null ? "" : googleClientId.trim();
        this.googleClientId = id.isEmpty() ? DEFAULT_CLIENT_ID : id;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public DatosGoogle verificar(String credential) {
        if (credential == null || credential.isBlank()) {
            throw new OperacionInvalidaException("Falta el token de Google.");
        }

        HttpURLConnection conn = null;
        try {
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token="
                    + URLEncoder.encode(credential.trim(), StandardCharsets.UTF_8);
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(6000);

            int status = conn.getResponseCode();
            if (status != 200) {
                throw new OperacionInvalidaException("Token de Google inválido.");
            }

            JsonNode datos;
            try (InputStream in = conn.getInputStream()) {
                datos = jsonMapper.readTree(in);
            }

            String aud = texto(datos, "aud");
            if (aud == null || !aud.equals(googleClientId)) {
                throw new OperacionInvalidaException("Token de Google inválido.");
            }

            String emailVerified = texto(datos, "email_verified");
            if (!"true".equalsIgnoreCase(emailVerified) && !"1".equals(emailVerified)) {
                throw new OperacionInvalidaException("El correo de Google no está verificado.");
            }

            String sub = texto(datos, "sub");
            String email = texto(datos, "email");
            String name = texto(datos, "name");
            if (sub == null || sub.isBlank() || email == null || email.isBlank()) {
                throw new OperacionInvalidaException("Token de Google incompleto.");
            }

            return new DatosGoogle(sub, email, (name == null || name.isBlank()) ? email : name, true);
        } catch (OperacionInvalidaException e) {
            throw e;
        } catch (Exception e) {
            throw new OperacionInvalidaException("No se pudo verificar el token con Google.");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String texto(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) return null;
        JsonNode child = node.get(field);
        if (child.isString()) {
            return child.asString();
        }
        return child.asString("");
    }
}
