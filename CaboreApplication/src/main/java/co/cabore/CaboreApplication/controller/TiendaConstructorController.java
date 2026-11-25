package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.config.UsuarioPrincipal;
import co.cabore.CaboreApplication.model.*;
import co.cabore.CaboreApplication.repository.ResenaRepository;
import co.cabore.CaboreApplication.service.CatalogoService;
import co.cabore.CaboreApplication.service.EmprendedorService;
import co.cabore.CaboreApplication.service.TiendaPersonalizacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador del Constructor Avanzado de Tiendas Personalizables (/emprendedor/constructor).
 *
 * Garantiza que cada emprendedor únicamente pueda personalizar su propia tienda
 * (obtenida a partir de la sesión autenticada) y persiste todos los cambios en MariaDB.
 */
@Controller
@RequestMapping("/emprendedor/constructor")
@RequiredArgsConstructor
public class TiendaConstructorController {

    private final EmprendedorService emprendedorService;
    private final TiendaPersonalizacionService personalizacionService;
    private final CatalogoService catalogoService;
    private final ResenaRepository resenaRepository;

    @GetMapping
    public String editor(@AuthenticationPrincipal UsuarioPrincipal yo, Model model) {
        Tienda tienda = emprendedorService.obtenerOCrearTienda(yo.getId());
        TiendaPersonalizacionService.EstadoConstructorVista estado =
                personalizacionService.cargarParaEditor(tienda);

        List<Producto> prodsTienda = emprendedorService.listarProductosTienda(tienda.getId());
        catalogoService.anotarCalificaciones(prodsTienda);

        model.addAttribute("nombreUsuario", yo.getNombre());
        model.addAttribute("tienda", tienda);
        model.addAttribute("cfg", estado.configActiva());
        model.addAttribute("tieneBorrador", estado.tieneBorrador());
        model.addAttribute("actualizadoEn", estado.actualizadoEn());
        model.addAttribute("configActivaJson", estado.configActivaJson());
        model.addAttribute("configPublicadaJson", estado.configPublicadaJson());
        model.addAttribute("configDefaultJson", estado.configDefaultJson());
        model.addAttribute("productosTienda", prodsTienda);
        model.addAttribute("categorias", emprendedorService.listarCategorias());
        model.addAttribute("resenasTienda",
                resenaRepository.buscarRecientesPorTienda(tienda.getId(), PageRequest.of(0, 6)));

        return "constructor-tienda";
    }

    @PostMapping("/borrador")
    @ResponseBody
    public Map<String, Object> guardarBorrador(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestBody TiendaConfigDto config) {
        try {
            Tienda tienda = emprendedorService.obtenerOCrearTienda(yo.getId());
            TiendaConfigDto guardado = personalizacionService.guardarBorrador(tienda, config);
            return Map.of(
                    "ok", true,
                    "tieneBorrador", true,
                    "mensaje", "Borrador guardado en MariaDB.",
                    "config", guardado
            );
        } catch (RuntimeException e) {
            return errorJson(e, "No se pudo guardar el borrador.");
        }
    }

    @PostMapping("/publicar")
    @ResponseBody
    public Map<String, Object> publicar(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestBody TiendaConfigDto config) {
        try {
            Tienda tienda = emprendedorService.obtenerOCrearTienda(yo.getId());
            TiendaConfigDto publicado = personalizacionService.publicar(tienda, config);
            return Map.of(
                    "ok", true,
                    "tieneBorrador", false,
                    "mensaje", "¡Cambios publicados! Tu tienda pública ya refleja el nuevo diseño.",
                    "config", publicado
            );
        } catch (RuntimeException e) {
            return errorJson(e, "No se pudieron publicar los cambios.");
        }
    }

    @PostMapping("/restaurar")
    @ResponseBody
    public Map<String, Object> restaurar(@AuthenticationPrincipal UsuarioPrincipal yo) {
        try {
            Tienda tienda = emprendedorService.obtenerOCrearTienda(yo.getId());
            TiendaConfigDto restaurado = personalizacionService.restaurarPredeterminados(tienda);
            return Map.of(
                    "ok", true,
                    "tieneBorrador", false,
                    "mensaje", "Se restauraron los valores predeterminados de la tienda.",
                    "config", restaurado
            );
        } catch (RuntimeException e) {
            return errorJson(e, "No se pudo restaurar la configuración.");
        }
    }

    @PostMapping("/imagen")
    @ResponseBody
    public Map<String, Object> subirImagen(
            @AuthenticationPrincipal UsuarioPrincipal yo,
            @RequestParam(name = "tipo", defaultValue = "banner") String tipo,
            @RequestParam("archivo") MultipartFile archivo) {
        try {
            Tienda tienda = emprendedorService.obtenerOCrearTienda(yo.getId());
            String ruta = personalizacionService.subirImagen(tienda, yo.getId(), tipo, archivo);
            String subcarpeta = ("logo".equalsIgnoreCase(tipo) || "favicon".equalsIgnoreCase(tipo))
                    ? "logos" : "banners";
            String urlPublica = Rutas.publica(ruta, subcarpeta);
            return Map.of(
                    "ok", true,
                    "ruta", ruta,
                    "urlPublica", urlPublica != null ? urlPublica : ("/" + ruta)
            );
        } catch (RuntimeException e) {
            return errorJson(e, "No se pudo subir la imagen.");
        }
    }

    private static Map<String, Object> errorJson(RuntimeException e, String fallback) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", false);
        map.put("error", e.getMessage() == null || e.getMessage().isBlank() ? fallback : e.getMessage());
        return map;
    }
}
