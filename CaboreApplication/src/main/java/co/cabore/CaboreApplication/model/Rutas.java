package co.cabore.CaboreApplication.model;

/**
 * Normaliza las rutas de imágenes guardadas en la base de datos para servirlas
 * desde /img/** (ver config.WebConfig). Acepta los formatos del PHP original
 * ("img/productos/x.jpg") y nombres sueltos ("x.jpg").
 */
public final class Rutas {

    private Rutas() {}

    public static String publica(String valor, String subcarpeta) {
        if (valor == null || valor.isBlank()) return null;
        String v = valor.trim().replace('\\', '/');
        if (v.startsWith("http://") || v.startsWith("https://") || v.startsWith("/")) return v;
        if (v.startsWith("img/")) return "/" + v;
        return "/img/" + subcarpeta + "/" + v;
    }
}
