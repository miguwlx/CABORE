package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Servicio del Constructor Avanzado de Tiendas Personalizables (Módulo Emprendedor & Tienda Pública).
 *
 * Responsabilidades:
 *  - Cargar la configuración publicada o el borrador de una tienda desde MariaDB.
 *  - Inicializar valores predeterminados coherentes con la tienda existente sin perder ningún dato previo.
 *  - Guardar borradores (sin alterar la tienda pública) y publicar cambios (actualizando tienda_personalizacion y tiendas).
 *  - Restaurar valores predeterminados y aplicar plantillas preconfiguradas.
 *  - Gestionar subida segura de logo, favicon, portada y galería de imágenes.
 */
@Service
@RequiredArgsConstructor
public class TiendaPersonalizacionService {

    private static final Set<String> PLANTILLAS_VALIDAS = Set.of(
            "minimalista", "moderna", "elegante", "tecnologica", "colorida");
    private static final Set<String> ESTILOS_TARJETA = Set.of(
            "clasica", "minimal", "elevada", "bordeada", "cristal");
    private static final Set<String> DISTRIBUCIONES = Set.of(
            "cuadricula", "detallada", "compacta");
    private static final Set<String> RADIOS = Set.of(
            "recto", "suave", "redondeado", "capsula");
    private static final Set<String> SOMBRAS = Set.of(
            "ninguna", "suave", "media", "intensa");
    private static final Set<String> ESPACIADOS = Set.of(
            "compacto", "normal", "amplio");
    private static final Set<String> ENCABEZADOS = Set.of(
            "centrado", "lateral", "minimal", "hero_integrado");
    private static final Set<String> PIES = Set.of(
            "completo", "minimalista", "columnas");
    private static final Set<String> DISENOS_PORTADA = Set.of(
            "impacto", "dividida", "editorial", "minimal");
    private static final Set<String> ALINEACIONES = Set.of(
            "izquierda", "centro", "derecha");
    private static final Set<String> ESTILOS_BOTON = Set.of(
            "solido", "contorno", "capsula", "gradiente");
    private static final Set<String> TAMANOS_TEXTO = Set.of(
            "compacto", "normal", "grande");

    private final TiendaPersonalizacionRepository personalizaciones;
    private final TiendaRepository tiendas;
    private final ImagenService imagenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record EstadoConstructorVista(
            Tienda tienda,
            TiendaConfigDto configActiva,
            TiendaConfigDto configPublicada,
            boolean tieneBorrador,
            LocalDateTime actualizadoEn,
            String configActivaJson,
            String configPublicadaJson,
            String configDefaultJson
    ) {}

    /**
     * Carga el estado completo para el editor visual en tiempo real del emprendedor.
     * Si existe un borrador sin publicar, carga el borrador como configuración activa en el editor.
     */
    @Transactional(readOnly = true)
    public EstadoConstructorVista cargarParaEditor(Tienda tienda) {
        Optional<TiendaPersonalizacion> opt = personalizaciones.findByTiendaId(tienda.getId());
        TiendaConfigDto porDefecto = construirPredeterminadaDesdeTienda(tienda);

        TiendaConfigDto publicada = opt
                .map(tp -> construirDtoDesdeEntidad(tienda, tp, false))
                .orElseGet(() -> construirPredeterminadaDesdeTienda(tienda));

        TiendaConfigDto activa = opt
                .map(tp -> construirDtoDesdeEntidad(tienda, tp, true))
                .orElse(publicada);

        boolean tieneBorrador = opt.map(TiendaPersonalizacion::isTieneBorrador).orElse(false);
        LocalDateTime actEn = opt.map(TiendaPersonalizacion::getActualizadoEn).orElse(LocalDateTime.now());

        return new EstadoConstructorVista(
                tienda,
                activa,
                publicada,
                tieneBorrador,
                actEn,
                aJson(activa),
                aJson(publicada),
                aJson(porDefecto)
        );
    }

    /**
     * Carga la configuración que debe renderizar la tienda pública (/tienda/{id}).
     * Si previewBorrador = true (solo permitido al dueño de la tienda o admin), devuelve el borrador.
     */
    @Transactional(readOnly = true)
    public TiendaConfigDto cargarParaTiendaPublica(Tienda tienda, boolean previewBorrador) {
        return personalizaciones.findByTiendaId(tienda.getId())
                .map(tp -> construirDtoDesdeEntidad(tienda, tp, previewBorrador && tp.isTieneBorrador()))
                .orElseGet(() -> construirPredeterminadaDesdeTienda(tienda));
    }

    /**
     * Guarda un borrador en MariaDB sin modificar la versión publicada ni la tabla tiendas.
     */
    @Transactional
    public TiendaConfigDto guardarBorrador(Tienda tienda, TiendaConfigDto entrada) {
        TiendaConfigDto normalizada = sanitizarYNormalizar(tienda, entrada);
        TiendaPersonalizacion tp = personalizaciones.findByTiendaId(tienda.getId())
                .orElseGet(() -> {
                    TiendaPersonalizacion nueva = new TiendaPersonalizacion();
                    nueva.setTiendaId(tienda.getId());
                    aplicarDtoAColumnas(nueva, construirPredeterminadaDesdeTienda(tienda));
                    nueva.setConfigPublicada(aJson(construirPredeterminadaDesdeTienda(tienda)));
                    return nueva;
                });

        tp.setConfigBorrador(aJson(normalizada));
        tp.setTieneBorrador(true);
        tp.setActualizadoEn(LocalDateTime.now());
        personalizaciones.save(tp);
        return normalizada;
    }

    /**
     * Publica los cambios del constructor en MariaDB: actualiza todas las columnas de
     * tienda_personalizacion, sincroniza los campos base con la tabla tiendas y limpia el estado de borrador.
     */
    @Transactional
    public TiendaConfigDto publicar(Tienda tienda, TiendaConfigDto entrada) {
        TiendaConfigDto normalizada = sanitizarYNormalizar(tienda, entrada);

        // 1) Sincronizar campos base con la entidad Tienda (compatibilidad total con el resto del sistema)
        tienda.setNombre(normalizada.getNombre());
        tienda.setDescripcion(limpiar(normalizada.getDescripcion()));
        tienda.setCiudad(limpiar(normalizada.getCiudad()));
        tienda.setColor(normalizada.getColorPrimario());
        tienda.setPlantilla(normalizada.getPlantilla());
        if (normalizada.getLogo() != null && !normalizada.getLogo().isBlank()) {
            tienda.setLogo(normalizada.getLogo().trim());
        }
        if (normalizada.getBanner() != null && !normalizada.getBanner().isBlank()) {
            tienda.setBanner(normalizada.getBanner().trim());
        }
        tienda.setWhatsapp(normalizada.getWhatsappDigitos().isEmpty() ? null : normalizada.getWhatsappDigitos());
        tienda.setInstagram(normalizada.getInstagramLimpio().isEmpty() ? null : normalizada.getInstagramLimpio());
        tiendas.save(tienda);

        // 2) Guardar configuración estructurada y JSON publicado en tienda_personalizacion
        TiendaPersonalizacion tp = personalizaciones.findByTiendaId(tienda.getId())
                .orElseGet(() -> {
                    TiendaPersonalizacion nueva = new TiendaPersonalizacion();
                    nueva.setTiendaId(tienda.getId());
                    return nueva;
                });

        aplicarDtoAColumnas(tp, normalizada);
        String json = aJson(normalizada);
        tp.setConfigPublicada(json);
        tp.setConfigBorrador(json);
        tp.setTieneBorrador(false);
        tp.setActualizadoEn(LocalDateTime.now());
        personalizaciones.save(tp);

        return normalizada;
    }

    /**
     * Restaura los valores predeterminados de diseño para la tienda conservando su nombre,
     * descripción, logo, banner y datos de contacto básicos.
     */
    @Transactional
    public TiendaConfigDto restaurarPredeterminados(Tienda tienda) {
        TiendaConfigDto base = construirPredeterminadaDesdeTienda(tienda);
        aplicarPresetPlantilla(base, "moderna");
        return publicar(tienda, base);
    }

    /**
     * Sube una imagen desde el constructor visual (logo, favicon, banner o galería),
     * validando firma binaria y tamaño máximo de 5 MB.
     */
    @Transactional
    public String subirImagen(Tienda tienda, Integer usuarioId, String tipo, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new OperacionInvalidaException("Selecciona un archivo de imagen válido.");
        }
        String tipoNorm = tipo == null ? "banner" : tipo.trim().toLowerCase();
        return switch (tipoNorm) {
            case "logo" -> imagenService.guardar(archivo, "logos", "logo_" + usuarioId);
            case "favicon" -> imagenService.guardar(archivo, "logos", "fav_" + usuarioId);
            case "galeria" -> imagenService.guardar(archivo, "banners", "gal_" + tienda.getId());
            default -> imagenService.guardar(archivo, "banners", "banner_" + usuarioId);
        };
    }

    /**
     * Sincroniza los cambios hechos desde el formulario clásico /emprendedor/tienda hacia
     * tienda_personalizacion si ya existía una personalización guardada para esta tienda.
     */
    @Transactional
    public void sincronizarDesdeTienda(Tienda tienda) {
        personalizaciones.findByTiendaId(tienda.getId()).ifPresent(tp -> {
            TiendaConfigDto pub = construirDtoDesdeEntidad(tienda, tp, false);
            pub.setNombre(tienda.getNombre());
            pub.setDescripcion(tienda.getDescripcion() != null ? tienda.getDescripcion() : "");
            pub.setCiudad(tienda.getCiudad() != null ? tienda.getCiudad() : "");
            pub.setPlantilla(tienda.getPlantillaSegura());
            if (tienda.getColor() != null && tienda.getColor().matches("^#[0-9a-fA-F]{6}$")) {
                pub.setColorPrimario(tienda.getColor());
                pub.setColorBotones(tienda.getColor());
            }
            if (tienda.getLogo() != null) pub.setLogo(tienda.getLogo());
            if (tienda.getBanner() != null) pub.setBanner(tienda.getBanner());
            pub.setWhatsapp(tienda.getWhatsapp() != null ? tienda.getWhatsapp() : "");
            pub.setInstagram(tienda.getInstagram() != null ? tienda.getInstagram() : "");

            aplicarDtoAColumnas(tp, pub);
            String json = aJson(pub);
            tp.setConfigPublicada(json);
            if (!tp.isTieneBorrador()) {
                tp.setConfigBorrador(json);
            }
            tp.setActualizadoEn(LocalDateTime.now());
            personalizaciones.save(tp);
        });
    }

    /* ── Construcción, presets y sanitización ───────────────────────────── */

    public TiendaConfigDto construirPredeterminadaDesdeTienda(Tienda t) {
        TiendaConfigDto dto = new TiendaConfigDto();
        dto.setNombre(t.getNombre() != null && !t.getNombre().isBlank() ? t.getNombre() : "Mi tienda");
        dto.setDescripcion(t.getDescripcion() != null ? t.getDescripcion() : "Bienvenido a nuestra tienda oficial en Caboré");
        dto.setCiudad(t.getCiudad() != null ? t.getCiudad() : "");
        dto.setLogo(t.getLogo());
        dto.setBanner(t.getBanner());
        dto.setWhatsapp(t.getWhatsapp() != null ? t.getWhatsapp() : "");
        dto.setInstagram(t.getInstagram() != null ? t.getInstagram() : "");

        String plantilla = t.getPlantillaSegura();
        aplicarPresetPlantilla(dto, plantilla);
        if (t.getColor() != null && t.getColor().matches("^#[0-9a-fA-F]{6}$")) {
            dto.setColorPrimario(t.getColor());
            dto.setColorBotones(t.getColor());
        }

        dto.setPortadaTitulo(dto.getNombre());
        dto.setPortadaSubtitulo("Tienda verificada en Caboré Marketplace");
        dto.setPortadaDescripcion(dto.getDescripcion());
        dto.setHistoriaTienda(dto.getDescripcion());
        dto.setSecciones(TiendaConfigDto.seccionesPredeterminadas());
        return dto;
    }

    public static void aplicarPresetPlantilla(TiendaConfigDto dto, String plantilla) {
        String p = PLANTILLAS_VALIDAS.contains(plantilla) ? plantilla : "moderna";
        dto.setPlantilla(p);
        switch (p) {
            case "minimalista" -> {
                dto.setColorPrimario("#111111");
                dto.setColorSecundario("#555555");
                dto.setColorFondo("#fafafa");
                dto.setColorSuperficie("#ffffff");
                dto.setColorTexto("#111111");
                dto.setColorBotones("#111111");
                dto.setColorTextoBotones("#ffffff");
                dto.setColorEncabezado("#ffffff");
                dto.setFuenteTitulos("DM Sans");
                dto.setFuenteCuerpo("DM Sans");
                dto.setEstiloTarjeta("minimal");
                dto.setRadioBordes("recto");
                dto.setNivelSombra("ninguna");
            }
            case "elegante" -> {
                dto.setColorPrimario("#b8923c");
                dto.setColorSecundario("#8c6c28");
                dto.setColorFondo("#f7f2e9");
                dto.setColorSuperficie("#ffffff");
                dto.setColorTexto("#2a2118");
                dto.setColorBotones("#b8923c");
                dto.setColorTextoBotones("#ffffff");
                dto.setColorEncabezado("#efe6d3");
                dto.setFuenteTitulos("Playfair Display");
                dto.setFuenteCuerpo("DM Sans");
                dto.setEstiloTarjeta("clasica");
                dto.setRadioBordes("suave");
                dto.setNivelSombra("suave");
            }
            case "tecnologica" -> {
                dto.setColorPrimario("#38bdf8");
                dto.setColorSecundario("#6366f1");
                dto.setColorFondo("#090d16");
                dto.setColorSuperficie("#111827");
                dto.setColorTexto("#f1f5f9");
                dto.setColorBotones("#38bdf8");
                dto.setColorTextoBotones("#090d16");
                dto.setColorEncabezado("#0f172a");
                dto.setFuenteTitulos("Outfit");
                dto.setFuenteCuerpo("Plus Jakarta Sans");
                dto.setEstiloTarjeta("bordeada");
                dto.setRadioBordes("suave");
                dto.setNivelSombra("intensa");
            }
            case "colorida" -> {
                dto.setColorPrimario("#ec4899");
                dto.setColorSecundario("#a855f7");
                dto.setColorFondo("#150a1f");
                dto.setColorSuperficie("#231433");
                dto.setColorTexto("#fdf7ff");
                dto.setColorBotones("#ec4899");
                dto.setColorTextoBotones("#ffffff");
                dto.setColorEncabezado("#1e0f2b");
                dto.setFuenteTitulos("Playfair Display");
                dto.setFuenteCuerpo("DM Sans");
                dto.setEstiloTarjeta("elevada");
                dto.setRadioBordes("redondeado");
                dto.setNivelSombra("intensa");
            }
            default -> { // moderna
                dto.setColorPrimario("#c9a84c");
                dto.setColorSecundario("#e2603f");
                dto.setColorFondo("#0d0d14");
                dto.setColorSuperficie("#161625");
                dto.setColorTexto("#f5f0e8");
                dto.setColorBotones("#c9a84c");
                dto.setColorTextoBotones("#0d0d14");
                dto.setColorEncabezado("#12121f");
                dto.setFuenteTitulos("Playfair Display");
                dto.setFuenteCuerpo("DM Sans");
                dto.setEstiloTarjeta("elevada");
                dto.setRadioBordes("suave");
                dto.setNivelSombra("media");
            }
        }
    }

    public TiendaConfigDto sanitizarYNormalizar(Tienda tienda, TiendaConfigDto in) {
        if (in == null) {
            return construirPredeterminadaDesdeTienda(tienda);
        }
        TiendaConfigDto d = new TiendaConfigDto();

        String nom = in.getNombre() == null || in.getNombre().trim().isEmpty()
                ? tienda.getNombre()
                : in.getNombre().trim();
        if (nom == null || nom.isBlank()) nom = "Mi tienda";
        d.setNombre(recortar(nom, 120));
        d.setEslogan(recortar(textoO(in.getEslogan(), ""), 200));
        d.setDescripcion(textoO(in.getDescripcion(), tienda.getDescripcion()));
        d.setCiudad(recortar(textoO(in.getCiudad(), tienda.getCiudad()), 100));

        d.setLogo(in.getLogo() != null && !in.getLogo().isBlank() ? in.getLogo().trim() : tienda.getLogo());
        d.setFavicon(in.getFavicon() != null && !in.getFavicon().isBlank() ? in.getFavicon().trim() : null);
        d.setBanner(in.getBanner() != null && !in.getBanner().isBlank() ? in.getBanner().trim() : tienda.getBanner());

        String plant = in.getPlantilla() != null ? in.getPlantilla().trim().toLowerCase() : "moderna";
        d.setPlantilla(PLANTILLAS_VALIDAS.contains(plant) ? plant : "moderna");

        d.setColorPrimario(colorValido(in.getColorPrimario(), "#c9a84c"));
        d.setColorSecundario(colorValido(in.getColorSecundario(), "#e2603f"));
        d.setColorFondo(colorValido(in.getColorFondo(), "#faf7f0"));
        d.setColorSuperficie(colorValido(in.getColorSuperficie(), "#ffffff"));
        d.setColorTexto(colorValido(in.getColorTexto(), "#1c1810"));
        d.setColorBotones(colorValido(in.getColorBotones(), d.getColorPrimario()));
        d.setColorTextoBotones(colorValido(in.getColorTextoBotones(), "#0d0d14"));
        d.setColorEncabezado(colorValido(in.getColorEncabezado(), d.getColorSuperficie()));

        d.setFuenteTitulos(recortar(textoO(in.getFuenteTitulos(), "Playfair Display"), 80));
        d.setFuenteCuerpo(recortar(textoO(in.getFuenteCuerpo(), "DM Sans"), 80));
        d.setTamanoTexto(opcionValida(in.getTamanoTexto(), TAMANOS_TEXTO, "normal"));

        d.setEstiloTarjeta(opcionValida(in.getEstiloTarjeta(), ESTILOS_TARJETA, "elevada"));
        d.setDistribucionProductos(opcionValida(in.getDistribucionProductos(), DISTRIBUCIONES, "cuadricula"));
        int cols = in.getColumnasProductos();
        d.setColumnasProductos(cols >= 2 && cols <= 4 ? cols : 3);
        d.setRadioBordes(opcionValida(in.getRadioBordes(), RADIOS, "suave"));
        d.setNivelSombra(opcionValida(in.getNivelSombra(), SOMBRAS, "media"));
        d.setEspaciado(opcionValida(in.getEspaciado(), ESPACIADOS, "normal"));
        d.setEstiloEncabezado(opcionValida(in.getEstiloEncabezado(), ENCABEZADOS, "centrado"));
        d.setEstiloPie(opcionValida(in.getEstiloPie(), PIES, "completo"));

        d.setPortadaDiseno(opcionValida(in.getPortadaDiseno(), DISENOS_PORTADA, "impacto"));
        d.setPortadaTitulo(recortar(textoO(in.getPortadaTitulo(), d.getNombre()), 180));
        d.setPortadaSubtitulo(recortar(textoO(in.getPortadaSubtitulo(), ""), 220));
        d.setPortadaDescripcion(textoO(in.getPortadaDescripcion(), d.getDescripcion()));
        d.setPortadaBotonTexto(recortar(textoO(in.getPortadaBotonTexto(), "Explorar colección"), 80));
        d.setPortadaBotonUrl(urlSegura(in.getPortadaBotonUrl(), "#seccion-catalogo"));
        d.setPortadaBoton2Texto(recortar(textoO(in.getPortadaBoton2Texto(), ""), 80));
        d.setPortadaBoton2Url(urlSegura(in.getPortadaBoton2Url(), "#seccion-contacto"));
        d.setPortadaAlineacion(opcionValida(in.getPortadaAlineacion(), ALINEACIONES, "centro"));
        d.setPortadaOverlay(Math.max(0, Math.min(85, in.getPortadaOverlay())));

        d.setMostrarPrecios(in.isMostrarPrecios());
        d.setMostrarDescuentos(in.isMostrarDescuentos());
        d.setMostrarCalificaciones(in.isMostrarCalificaciones());
        d.setMostrarEtiquetas(in.isMostrarEtiquetas());
        d.setMostrarDescripcionProd(in.isMostrarDescripcionProd());
        d.setEstiloBotonCompra(opcionValida(in.getEstiloBotonCompra(), ESTILOS_BOTON, "solido"));
        d.setTextoBotonCompra(recortar(textoO(in.getTextoBotonCompra(), "Agregar al carrito"), 60));
        d.setOrdenPredeterminado(CatalogoService.normalizarOrden(in.getOrdenPredeterminado()));

        d.setWhatsapp(recortar(textoO(in.getWhatsapp(), tienda.getWhatsapp()), 40));
        d.setInstagram(recortar(textoO(in.getInstagram(), tienda.getInstagram()), 100));
        d.setFacebook(recortar(textoO(in.getFacebook(), ""), 180));
        d.setTiktok(recortar(textoO(in.getTiktok(), ""), 180));
        d.setTelefonoContacto(recortar(textoO(in.getTelefonoContacto(), ""), 60));
        d.setCorreoContacto(recortar(textoO(in.getCorreoContacto(), ""), 160));
        d.setHorarioAtencion(recortar(textoO(in.getHorarioAtencion(), ""), 220));
        d.setHistoriaTienda(textoO(in.getHistoriaTienda(), d.getDescripcion()));
        d.setPoliticaEnvios(textoO(in.getPoliticaEnvios(), ""));
        d.setPoliticaDevoluciones(textoO(in.getPoliticaDevoluciones(), ""));

        d.setSecciones(normalizarSecciones(in.getSecciones()));
        d.setGaleria(in.getGaleria() == null ? new ArrayList<>()
                : in.getGaleria().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).limit(12).toList());
        d.setTestimonios(in.getTestimonios() == null ? new ArrayList<>()
                : in.getTestimonios().stream()
                .filter(t -> t != null && t.getComentario() != null && !t.getComentario().isBlank())
                .map(t -> new TiendaConfigDto.TestimonioConfig(
                        recortar(textoO(t.getAutor(), "Cliente verificado"), 80),
                        recortar(t.getComentario().trim(), 400),
                        Math.max(1, Math.min(5, t.getCalificacion()))))
                .limit(8)
                .toList());

        return d;
    }

    private List<TiendaConfigDto.SeccionConfig> normalizarSecciones(List<TiendaConfigDto.SeccionConfig> entrada) {
        List<TiendaConfigDto.SeccionConfig> base = TiendaConfigDto.seccionesPredeterminadas();
        if (entrada == null || entrada.isEmpty()) {
            return base;
        }
        Map<String, TiendaConfigDto.SeccionConfig> mapaBase = new LinkedHashMap<>();
        for (TiendaConfigDto.SeccionConfig s : base) {
            mapaBase.put(s.getId(), s);
        }

        List<TiendaConfigDto.SeccionConfig> resultado = new ArrayList<>();
        Set<String> vistos = new HashSet<>();

        for (TiendaConfigDto.SeccionConfig s : entrada) {
            if (s == null || s.getId() == null) continue;
            String id = s.getId().trim().toLowerCase();
            TiendaConfigDto.SeccionConfig def = mapaBase.get(id);
            if (def == null || !vistos.add(id)) continue;
            resultado.add(new TiendaConfigDto.SeccionConfig(
                    id,
                    def.getNombre(),
                    recortar(textoO(s.getTitulo(), def.getTitulo()), 120),
                    recortar(textoO(s.getSubtitulo(), def.getSubtitulo()), 200),
                    s.isVisible()
            ));
        }
        // Asegurar que ninguna sección del sistema se pierda si no venía en la lista
        for (TiendaConfigDto.SeccionConfig def : base) {
            if (vistos.add(def.getId())) {
                resultado.add(def);
            }
        }
        return resultado;
    }

    private TiendaConfigDto construirDtoDesdeEntidad(Tienda tienda, TiendaPersonalizacion tp, boolean preferirBorrador) {
        String jsonFuente = preferirBorrador && tp.getConfigBorrador() != null && !tp.getConfigBorrador().isBlank()
                ? tp.getConfigBorrador()
                : tp.getConfigPublicada();

        if (jsonFuente != null && !jsonFuente.isBlank()) {
            try {
                TiendaConfigDto leido = objectMapper.readValue(jsonFuente, TiendaConfigDto.class);
                // Si no es borrador, sincronizar siempre nombre/logo/banner vigentes de la tabla tiendas
                if (!preferirBorrador) {
                    if (tienda.getNombre() != null && !tienda.getNombre().isBlank()) leido.setNombre(tienda.getNombre());
                    if (tienda.getLogo() != null && !tienda.getLogo().isBlank()) leido.setLogo(tienda.getLogo());
                    if (tienda.getBanner() != null && !tienda.getBanner().isBlank()) leido.setBanner(tienda.getBanner());
                }
                return sanitizarYNormalizar(tienda, leido);
            } catch (Exception ignored) {
                // Si el JSON tuviera algún problema, reconstruir desde las columnas relacionales
            }
        }

        TiendaConfigDto dto = construirPredeterminadaDesdeTienda(tienda);
        dto.setEslogan(textoO(tp.getEslogan(), dto.getEslogan()));
        dto.setFavicon(tp.getFavicon());
        dto.setPlantilla(tp.getPlantilla());
        dto.setColorPrimario(tp.getColorPrimario());
        dto.setColorSecundario(tp.getColorSecundario());
        dto.setColorFondo(tp.getColorFondo());
        dto.setColorSuperficie(tp.getColorSuperficie());
        dto.setColorTexto(tp.getColorTexto());
        dto.setColorBotones(tp.getColorBotones());
        dto.setColorTextoBotones(tp.getColorTextoBotones());
        dto.setColorEncabezado(tp.getColorEncabezado());
        dto.setFuenteTitulos(tp.getFuenteTitulos());
        dto.setFuenteCuerpo(tp.getFuenteCuerpo());
        dto.setTamanoTexto(tp.getTamanoTexto());
        dto.setEstiloTarjeta(tp.getEstiloTarjeta());
        dto.setDistribucionProductos(tp.getDistribucionProductos());
        dto.setColumnasProductos(tp.getColumnasProductos());
        dto.setRadioBordes(tp.getRadioBordes());
        dto.setNivelSombra(tp.getNivelSombra());
        dto.setEspaciado(tp.getEspaciado());
        dto.setEstiloEncabezado(tp.getEstiloEncabezado());
        dto.setEstiloPie(tp.getEstiloPie());
        dto.setPortadaDiseno(tp.getPortadaDiseno());
        dto.setPortadaTitulo(textoO(tp.getPortadaTitulo(), tienda.getNombre()));
        dto.setPortadaSubtitulo(textoO(tp.getPortadaSubtitulo(), ""));
        dto.setPortadaDescripcion(textoO(tp.getPortadaDescripcion(), tienda.getDescripcion()));
        dto.setPortadaBotonTexto(textoO(tp.getPortadaBotonTexto(), "Explorar colección"));
        dto.setPortadaBotonUrl(textoO(tp.getPortadaBotonUrl(), "#seccion-catalogo"));
        dto.setPortadaBoton2Texto(textoO(tp.getPortadaBoton2Texto(), ""));
        dto.setPortadaBoton2Url(textoO(tp.getPortadaBoton2Url(), "#seccion-contacto"));
        dto.setPortadaAlineacion(tp.getPortadaAlineacion());
        dto.setPortadaOverlay(tp.getPortadaOverlay());
        dto.setMostrarPrecios(tp.isMostrarPrecios());
        dto.setMostrarDescuentos(tp.isMostrarDescuentos());
        dto.setMostrarCalificaciones(tp.isMostrarCalificaciones());
        dto.setMostrarEtiquetas(tp.isMostrarEtiquetas());
        dto.setMostrarDescripcionProd(tp.isMostrarDescripcionProd());
        dto.setEstiloBotonCompra(tp.getEstiloBotonCompra());
        dto.setTextoBotonCompra(tp.getTextoBotonCompra());
        dto.setOrdenPredeterminado(tp.getOrdenPredeterminado());
        dto.setFacebook(textoO(tp.getFacebook(), ""));
        dto.setTiktok(textoO(tp.getTiktok(), ""));
        dto.setTelefonoContacto(textoO(tp.getTelefonoContacto(), ""));
        dto.setCorreoContacto(textoO(tp.getCorreoContacto(), ""));
        dto.setHorarioAtencion(textoO(tp.getHorarioAtencion(), ""));
        dto.setHistoriaTienda(textoO(tp.getHistoriaTienda(), tienda.getDescripcion()));
        dto.setPoliticaEnvios(textoO(tp.getPoliticaEnvios(), ""));
        dto.setPoliticaDevoluciones(textoO(tp.getPoliticaDevoluciones(), ""));
        return sanitizarYNormalizar(tienda, dto);
    }

    private void aplicarDtoAColumnas(TiendaPersonalizacion tp, TiendaConfigDto d) {
        tp.setEslogan(d.getEslogan());
        tp.setFavicon(d.getFavicon());
        tp.setPlantilla(d.getPlantilla());
        tp.setColorPrimario(d.getColorPrimario());
        tp.setColorSecundario(d.getColorSecundario());
        tp.setColorFondo(d.getColorFondo());
        tp.setColorSuperficie(d.getColorSuperficie());
        tp.setColorTexto(d.getColorTexto());
        tp.setColorBotones(d.getColorBotones());
        tp.setColorTextoBotones(d.getColorTextoBotones());
        tp.setColorEncabezado(d.getColorEncabezado());
        tp.setFuenteTitulos(d.getFuenteTitulos());
        tp.setFuenteCuerpo(d.getFuenteCuerpo());
        tp.setTamanoTexto(d.getTamanoTexto());
        tp.setEstiloTarjeta(d.getEstiloTarjeta());
        tp.setDistribucionProductos(d.getDistribucionProductos());
        tp.setColumnasProductos(d.getColumnasProductos());
        tp.setRadioBordes(d.getRadioBordes());
        tp.setNivelSombra(d.getNivelSombra());
        tp.setEspaciado(d.getEspaciado());
        tp.setEstiloEncabezado(d.getEstiloEncabezado());
        tp.setEstiloPie(d.getEstiloPie());
        tp.setPortadaDiseno(d.getPortadaDiseno());
        tp.setPortadaTitulo(d.getPortadaTitulo());
        tp.setPortadaSubtitulo(d.getPortadaSubtitulo());
        tp.setPortadaDescripcion(d.getPortadaDescripcion());
        tp.setPortadaBotonTexto(d.getPortadaBotonTexto());
        tp.setPortadaBotonUrl(d.getPortadaBotonUrl());
        tp.setPortadaBoton2Texto(d.getPortadaBoton2Texto());
        tp.setPortadaBoton2Url(d.getPortadaBoton2Url());
        tp.setPortadaAlineacion(d.getPortadaAlineacion());
        tp.setPortadaOverlay(d.getPortadaOverlay());
        tp.setMostrarPrecios(d.isMostrarPrecios());
        tp.setMostrarDescuentos(d.isMostrarDescuentos());
        tp.setMostrarCalificaciones(d.isMostrarCalificaciones());
        tp.setMostrarEtiquetas(d.isMostrarEtiquetas());
        tp.setMostrarDescripcionProd(d.isMostrarDescripcionProd());
        tp.setEstiloBotonCompra(d.getEstiloBotonCompra());
        tp.setTextoBotonCompra(d.getTextoBotonCompra());
        tp.setOrdenPredeterminado(d.getOrdenPredeterminado());
        tp.setFacebook(d.getFacebook());
        tp.setTiktok(d.getTiktok());
        tp.setTelefonoContacto(d.getTelefonoContacto());
        tp.setCorreoContacto(d.getCorreoContacto());
        tp.setHorarioAtencion(d.getHorarioAtencion());
        tp.setHistoriaTienda(d.getHistoriaTienda());
        tp.setPoliticaEnvios(d.getPoliticaEnvios());
        tp.setPoliticaDevoluciones(d.getPoliticaDevoluciones());
        tp.setSeccionesJson(aJson(d.getSecciones()));
        tp.setGaleriaJson(aJson(d.getGaleria()));
        tp.setTestimoniosJson(aJson(d.getTestimonios()));
    }

    public String aJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String colorValido(String c, String fallback) {
        if (c != null && c.trim().matches("^#[0-9a-fA-F]{6}$")) {
            return c.trim().toLowerCase();
        }
        return fallback;
    }

    private static String opcionValida(String val, Set<String> permitidos, String fallback) {
        if (val == null) return fallback;
        String norm = val.trim().toLowerCase();
        return permitidos.contains(norm) ? norm : fallback;
    }

    private static String urlSegura(String url, String fallback) {
        if (url == null || url.isBlank()) return fallback;
        String u = url.trim();
        if (u.startsWith("#") || u.startsWith("/") || u.startsWith("https://") || u.startsWith("http://")) {
            return recortar(u, 255);
        }
        return fallback;
    }

    private static String textoO(String val, String fallback) {
        return val != null ? val.trim() : (fallback != null ? fallback : "");
    }

    private static String limpiar(String val) {
        return val == null || val.isBlank() ? null : val.trim();
    }

    private static String recortar(String val, int max) {
        if (val == null) return "";
        return val.length() <= max ? val : val.substring(0, max);
    }
}
