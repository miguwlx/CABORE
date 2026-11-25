package co.cabore.CaboreApplication.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Modelo completo de configuración del Constructor Avanzado de Tiendas Personalizables.
 * Se serializa en JSON para el editor en tiempo real (borrador y versión publicada) y se
 * sincroniza con las columnas de tienda_personalizacion y tiendas en MariaDB.
 */
@Getter
@Setter
@NoArgsConstructor
public class TiendaConfigDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeccionConfig {
        private String id;
        private String nombre;
        private String titulo;
        private String subtitulo;
        private boolean visible;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestimonioConfig {
        private String autor;
        private String comentario;
        private int calificacion = 5;
    }

    /* ── 1. Identidad de la tienda ──────────────────────────────────────── */
    private String nombre = "Mi tienda";
    private String eslogan = "Calidad artesanal hecha en Colombia";
    private String descripcion = "Descripción de mi tienda";
    private String ciudad = "";
    private String logo;
    private String favicon;
    private String banner;

    private String colorPrimario = "#c9a84c";
    private String colorSecundario = "#e2603f";
    private String colorFondo = "#faf7f0";
    private String colorSuperficie = "#ffffff";
    private String colorTexto = "#1c1810";
    private String colorBotones = "#c9a84c";
    private String colorTextoBotones = "#0d0d14";
    private String colorEncabezado = "#ffffff";

    private String fuenteTitulos = "Playfair Display";
    private String fuenteCuerpo = "DM Sans";
    private String tamanoTexto = "normal"; // compacto | normal | grande

    /* ── 2. Diseño y apariencia ─────────────────────────────────────────── */
    private String plantilla = "moderna"; // minimalista | moderna | elegante | tecnologica | colorida
    private String estiloTarjeta = "elevada"; // clasica | minimal | elevada | bordeada | cristal
    private String distribucionProductos = "cuadricula"; // cuadricula | detallada | compacta
    private int columnasProductos = 3; // 2 | 3 | 4
    private String radioBordes = "suave"; // recto | suave | redondeado | capsula
    private String nivelSombra = "media"; // ninguna | suave | media | intensa
    private String espaciado = "normal"; // compacto | normal | amplio
    private String estiloEncabezado = "centrado"; // centrado | lateral | minimal | hero_integrado
    private String estiloPie = "completo"; // completo | minimalista | columnas

    /* ── 3. Editor de portada (Hero) ────────────────────────────────────── */
    private String portadaDiseno = "impacto"; // impacto | dividida | editorial | minimal
    private String portadaTitulo = "";
    private String portadaSubtitulo = "";
    private String portadaDescripcion = "";
    private String portadaBotonTexto = "Explorar colección";
    private String portadaBotonUrl = "#seccion-catalogo";
    private String portadaBoton2Texto = "Contactar tienda";
    private String portadaBoton2Url = "#seccion-contacto";
    private String portadaAlineacion = "centro"; // izquierda | centro | derecha
    private int portadaOverlay = 40; // 0..85

    /* ── 4. Constructor de secciones ────────────────────────────────────── */
    private List<SeccionConfig> secciones = seccionesPredeterminadas();

    /* ── 5. Personalización de productos ────────────────────────────────── */
    private boolean mostrarPrecios = true;
    private boolean mostrarDescuentos = true;
    private boolean mostrarCalificaciones = true;
    private boolean mostrarEtiquetas = true;
    private boolean mostrarDescripcionProd = true;
    private String estiloBotonCompra = "solido"; // solido | contorno | capsula | gradiente
    private String textoBotonCompra = "Agregar al carrito";
    private String ordenPredeterminado = "reciente"; // reciente | precio_asc | precio_desc | nombre

    /* ── 6. Redes sociales e información comercial ──────────────────────── */
    private String whatsapp = "";
    private String instagram = "";
    private String facebook = "";
    private String tiktok = "";
    private String telefonoContacto = "";
    private String correoContacto = "";
    private String horarioAtencion = "Lunes a Sábado · 8:00 a.m. – 6:00 p.m.";
    private String historiaTienda = "";
    private String politicaEnvios = "Envíos seguros a todo el país. Coordinamos la entrega directamente contigo tras confirmar el pedido.";
    private String politicaDevoluciones = "Garantía de satisfacción: cambios y devoluciones dentro de los primeros 5 días hábiles.";

    private List<String> galeria = new ArrayList<>();
    private List<TestimonioConfig> testimonios = new ArrayList<>();

    public static List<SeccionConfig> seccionesPredeterminadas() {
        List<SeccionConfig> list = new ArrayList<>();
        list.add(new SeccionConfig("destacados", "Productos destacados", "✨ Selección destacada", "Nuestras piezas favoritas elegidas para ti", true));
        list.add(new SeccionConfig("categorias", "Categorías", "🗂️ Explora por categoría", "Encuentra rápidamente lo que buscas", true));
        list.add(new SeccionConfig("ofertas", "Ofertas y descuentos", "🏷️ Ofertas especiales", "Aprovecha precios de temporada por tiempo limitado", true));
        list.add(new SeccionConfig("mas_vendidos", "Productos más vendidos", "🔥 Los más vendidos", "Los favoritos de nuestra comunidad", true));
        list.add(new SeccionConfig("nuevos", "Nuevos productos", "🆕 Recién llegados", "Lo último que hemos creado en nuestra tienda", true));
        list.add(new SeccionConfig("catalogo", "Catálogo completo", "🛍️ Todos nuestros productos", "Filtra, busca y ordena todo nuestro inventario", true));
        list.add(new SeccionConfig("historia", "Información de la tienda", "📖 Sobre nuestra marca", "Conoce nuestra historia y compromiso de calidad", true));
        list.add(new SeccionConfig("galeria", "Galería de imágenes", "🖼️ Galería visual", "Inspiración y detalles de nuestro trabajo", true));
        list.add(new SeccionConfig("testimonios", "Testimonios y reseñas", "⭐ Clientes felices", "Opiniones reales de quienes ya compraron", true));
        list.add(new SeccionConfig("contacto", "Información de contacto", "📍 Contacto, horarios y políticas", "Estamos listos para atenderte por nuestros canales oficiales", true));
        return list;
    }

    public String getRutaLogoPublica() {
        return Rutas.publica(logo, "logos");
    }

    public String getRutaFaviconPublica() {
        return Rutas.publica(favicon, "logos");
    }

    public String getRutaBannerPublica() {
        return Rutas.publica(banner, "banners");
    }

    public List<String> getRutasGaleriaPublicas() {
        if (galeria == null || galeria.isEmpty()) return List.of();
        List<String> res = new ArrayList<>(galeria.size());
        for (String g : galeria) {
            String pub = Rutas.publica(g, "banners");
            if (pub != null) res.add(pub);
        }
        return res;
    }

    public String getWhatsappDigitos() {
        return whatsapp == null ? "" : whatsapp.replaceAll("[^0-9]", "");
    }

    public String getInstagramLimpio() {
        if (instagram == null) return "";
        String i = instagram.trim();
        while (i.startsWith("@")) i = i.substring(1).trim();
        return i;
    }

    public String getTiktokLimpio() {
        if (tiktok == null) return "";
        String t = tiktok.trim();
        while (t.startsWith("@")) t = t.substring(1).trim();
        return t;
    }

    public String getRadioCss() {
        return switch (radioBordes == null ? "suave" : radioBordes) {
            case "recto" -> "4px";
            case "redondeado" -> "22px";
            case "capsula" -> "30px";
            default -> "14px";
        };
    }

    public String getSombraCss() {
        return switch (nivelSombra == null ? "media" : nivelSombra) {
            case "ninguna" -> "none";
            case "suave" -> "0 4px 14px rgba(0,0,0,0.06)";
            case "intensa" -> "0 20px 48px rgba(0,0,0,0.22)";
            default -> "0 10px 28px rgba(0,0,0,0.11)";
        };
    }

    public String getTamanoBaseCss() {
        return switch (tamanoTexto == null ? "normal" : tamanoTexto) {
            case "compacto" -> "14.5px";
            case "grande" -> "17.5px";
            default -> "16px";
        };
    }

    public String getGapGridCss() {
        return switch (espaciado == null ? "normal" : espaciado) {
            case "compacto" -> "12px";
            case "amplio" -> "26px";
            default -> "18px";
        };
    }
}
