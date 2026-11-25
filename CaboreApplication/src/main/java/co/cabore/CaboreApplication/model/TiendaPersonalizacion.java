package co.cabore.CaboreApplication.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Configuración avanzada del constructor de tiendas personalizables (tabla tienda_personalizacion).
 * Guarda tanto los atributos estructurados de identidad, diseño, portada, productos, secciones
 * y datos comerciales como las instantáneas JSON de la versión publicada y del borrador.
 */
@Entity
@Table(name = "tienda_personalizacion")
@Getter
@Setter
public class TiendaPersonalizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tienda_id", nullable = false, unique = true)
    private Integer tiendaId;

    @Column(length = 200)
    private String eslogan;

    @Column(length = 255)
    private String favicon;

    @Column(length = 40, nullable = false)
    private String plantilla = "moderna";

    @Column(name = "color_primario", length = 20, nullable = false)
    private String colorPrimario = "#c9a84c";

    @Column(name = "color_secundario", length = 20, nullable = false)
    private String colorSecundario = "#e2603f";

    @Column(name = "color_fondo", length = 20, nullable = false)
    private String colorFondo = "#faf7f0";

    @Column(name = "color_superficie", length = 20, nullable = false)
    private String colorSuperficie = "#ffffff";

    @Column(name = "color_texto", length = 20, nullable = false)
    private String colorTexto = "#1c1810";

    @Column(name = "color_botones", length = 20, nullable = false)
    private String colorBotones = "#c9a84c";

    @Column(name = "color_texto_botones", length = 20, nullable = false)
    private String colorTextoBotones = "#0d0d14";

    @Column(name = "color_encabezado", length = 20, nullable = false)
    private String colorEncabezado = "#ffffff";

    @Column(name = "fuente_titulos", length = 80, nullable = false)
    private String fuenteTitulos = "Playfair Display";

    @Column(name = "fuente_cuerpo", length = 80, nullable = false)
    private String fuenteCuerpo = "DM Sans";

    @Column(name = "tamano_texto", length = 20, nullable = false)
    private String tamanoTexto = "normal";

    @Column(name = "estilo_tarjeta", length = 30, nullable = false)
    private String estiloTarjeta = "elevada";

    @Column(name = "distribucion_productos", length = 30, nullable = false)
    private String distribucionProductos = "cuadricula";

    @Column(name = "columnas_productos", nullable = false)
    private int columnasProductos = 3;

    @Column(name = "radio_bordes", length = 20, nullable = false)
    private String radioBordes = "suave";

    @Column(name = "nivel_sombra", length = 20, nullable = false)
    private String nivelSombra = "media";

    @Column(length = 20, nullable = false)
    private String espaciado = "normal";

    @Column(name = "estilo_encabezado", length = 30, nullable = false)
    private String estiloEncabezado = "centrado";

    @Column(name = "estilo_pie", length = 30, nullable = false)
    private String estiloPie = "completo";

    @Column(name = "portada_diseno", length = 30, nullable = false)
    private String portadaDiseno = "impacto";

    @Column(name = "portada_titulo", length = 180)
    private String portadaTitulo;

    @Column(name = "portada_subtitulo", length = 220)
    private String portadaSubtitulo;

    @Column(name = "portada_descripcion", columnDefinition = "TEXT")
    private String portadaDescripcion;

    @Column(name = "portada_boton_texto", length = 80)
    private String portadaBotonTexto;

    @Column(name = "portada_boton_url", length = 255)
    private String portadaBotonUrl;

    @Column(name = "portada_boton2_texto", length = 80)
    private String portadaBoton2Texto;

    @Column(name = "portada_boton2_url", length = 255)
    private String portadaBoton2Url;

    @Column(name = "portada_alineacion", length = 20, nullable = false)
    private String portadaAlineacion = "centro";

    @Column(name = "portada_overlay", nullable = false)
    private int portadaOverlay = 40;

    @Column(name = "mostrar_precios", nullable = false)
    private boolean mostrarPrecios = true;

    @Column(name = "mostrar_descuentos", nullable = false)
    private boolean mostrarDescuentos = true;

    @Column(name = "mostrar_calificaciones", nullable = false)
    private boolean mostrarCalificaciones = true;

    @Column(name = "mostrar_etiquetas", nullable = false)
    private boolean mostrarEtiquetas = true;

    @Column(name = "mostrar_descripcion_prod", nullable = false)
    private boolean mostrarDescripcionProd = true;

    @Column(name = "estilo_boton_compra", length = 30, nullable = false)
    private String estiloBotonCompra = "solido";

    @Column(name = "texto_boton_compra", length = 60, nullable = false)
    private String textoBotonCompra = "Agregar al carrito";

    @Column(name = "orden_predeterminado", length = 30, nullable = false)
    private String ordenPredeterminado = "reciente";

    @Column(length = 180)
    private String facebook;

    @Column(length = 180)
    private String tiktok;

    @Column(name = "telefono_contacto", length = 60)
    private String telefonoContacto;

    @Column(name = "correo_contacto", length = 160)
    private String correoContacto;

    @Column(name = "horario_atencion", length = 220)
    private String horarioAtencion;

    @Column(name = "historia_tienda", columnDefinition = "TEXT")
    private String historiaTienda;

    @Column(name = "politica_envios", columnDefinition = "TEXT")
    private String politicaEnvios;

    @Column(name = "politica_devoluciones", columnDefinition = "TEXT")
    private String politicaDevoluciones;

    @Column(name = "secciones_json", columnDefinition = "TEXT")
    private String seccionesJson;

    @Column(name = "galeria_json", columnDefinition = "TEXT")
    private String galeriaJson;

    @Column(name = "testimonios_json", columnDefinition = "TEXT")
    private String testimoniosJson;

    @Column(name = "config_publicada", columnDefinition = "LONGTEXT")
    private String configPublicada;

    @Column(name = "config_borrador", columnDefinition = "LONGTEXT")
    private String configBorrador;

    @Column(name = "tiene_borrador", nullable = false)
    private boolean tieneBorrador = false;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    @Transient
    public String getRutaFavicon() {
        return Rutas.publica(favicon, "logos");
    }
}
