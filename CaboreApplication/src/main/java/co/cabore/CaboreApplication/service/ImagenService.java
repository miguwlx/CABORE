package co.cabore.CaboreApplication.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Subida segura de imágenes para productos, logos y banners.
 *
 * Reglas (idénticas a guardar_producto.php y actualizar_tienda.php):
 *  - Extensiones permitidas: jpg, jpeg, png, webp, gif.
 *  - Tamaño máximo: 5 MB.
 *  - Verificación de firma binaria (magic bytes) para impedir archivos ejecutables
 *    o scripts disfrazados con extensión de imagen.
 *  - Nombre aleatorio sin caracteres del usuario (evita path traversal).
 *  - Devuelve la ruta relativa "img/<subcarpeta>/<prefijo>_<uuid>.<ext>",
 *    compatible con el formato del PHP original y con Rutas.publica().
 */
@Service
public class ImagenService {

    public static final long MAX_BYTES = 5L * 1024L * 1024L; // 5 MB
    private static final Set<String> EXTENSIONES_PERMITIDAS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final Path baseDir;

    public ImagenService(@Value("${cabore.uploads.dir:uploads/img}") String uploadsDir) {
        this.baseDir = Paths.get(uploadsDir).toAbsolutePath().normalize();
    }

    public Path getBaseDir() {
        return baseDir;
    }

    /**
     * Guarda la imagen subida y devuelve la ruta relativa para almacenar en BD
     * (p. ej. "img/productos/prod_a1b2c3.jpg"), o null si no se envió archivo.
     */
    public String guardar(MultipartFile archivo, String subcarpeta, String prefijo) {
        if (archivo == null || archivo.isEmpty()) {
            return null;
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new OperacionInvalidaException("La imagen supera el tamaño máximo permitido de 5 MB.");
        }

        String original = archivo.getOriginalFilename();
        String ext = extraerExtension(original);
        if (!EXTENSIONES_PERMITIDAS.contains(ext)) {
            throw new OperacionInvalidaException("Formato de imagen no permitido. Usa JPG, PNG, WEBP o GIF.");
        }

        try {
            byte[] cabecera = leerCabecera(archivo);
            if (!esFirmaImagenValida(cabecera)) {
                throw new OperacionInvalidaException("El archivo subido no es una imagen válida.");
            }

            Path destinoDir = baseDir.resolve(subcarpeta).normalize();
            if (!destinoDir.startsWith(baseDir)) {
                throw new OperacionInvalidaException("Ruta de destino inválida.");
            }
            Files.createDirectories(destinoDir);

            String token = UUID.randomUUID().toString().replace("-", "").substring(0, 13);
            String filename = prefijo + "_" + token + "." + ext;
            Path destinoArchivo = destinoDir.resolve(filename).normalize();

            try (InputStream in = archivo.getInputStream()) {
                Files.copy(in, destinoArchivo, StandardCopyOption.REPLACE_EXISTING);
            }

            return "img/" + subcarpeta + "/" + filename;
        } catch (IOException e) {
            throw new OperacionInvalidaException("No se pudo guardar la imagen.");
        }
    }

    private static String extraerExtension(String nombre) {
        if (nombre == null) return "";
        String limpio = nombre.replace('\\', '/');
        int slash = limpio.lastIndexOf('/');
        if (slash >= 0) limpio = limpio.substring(slash + 1);
        int dot = limpio.lastIndexOf('.');
        if (dot < 0 || dot == limpio.length() - 1) return "";
        return limpio.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] leerCabecera(MultipartFile archivo) throws IOException {
        try (InputStream in = archivo.getInputStream()) {
            return in.readNBytes(12);
        }
    }

    /** Comprueba los magic bytes de JPEG, PNG, GIF y WEBP (RIFF....WEBP). */
    private static boolean esFirmaImagenValida(byte[] b) {
        if (b == null || b.length < 4) return false;
        // JPEG: FF D8 FF
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47
        if ((b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) {
            return true;
        }
        // GIF: "GIF8"
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return true;
        }
        // WEBP: "RIFF" .... "WEBP"
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return true;
        }
        return false;
    }
}
