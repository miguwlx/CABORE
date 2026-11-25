package co.cabore.CaboreApplication.service;

/**
 * Verificador de tokens ID de Google Sign-In.
 * Permite validar tokens reales contra Google y probar el flujo de autenticación
 * en pruebas de integración sin simular tokens falsos en producción.
 */
public interface GoogleTokenVerifier {

    record DatosGoogle(
            String googleId,
            String correo,
            String nombre,
            boolean emailVerificado
    ) {}

    DatosGoogle verificar(String credential);
}
