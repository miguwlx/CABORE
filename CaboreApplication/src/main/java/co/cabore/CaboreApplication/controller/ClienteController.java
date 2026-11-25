package co.cabore.CaboreApplication.controller;

import co.cabore.CaboreApplication.model.Producto;
import co.cabore.CaboreApplication.model.Tienda;
import co.cabore.CaboreApplication.repository.TiendaRepository;
import co.cabore.CaboreApplication.service.CarritoService;
import co.cabore.CaboreApplication.service.CatalogoService;
import co.cabore.CaboreApplication.service.FavoritoService;
import co.cabore.CaboreApplication.service.ResenaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Módulo del cliente (FASE 3): catálogo de productos con búsqueda y filtros,
 * catálogo de tiendas y ficha de producto con reseñas.
 *
 * Se conservan los endpoints y vistas originales:
 *   GET /cliente      -> cliente.html (ahora catálogo completo)
 *   GET /tienda/{id}  -> tienda-detalle.html
 */
@Controller
@RequiredArgsConstructor
public class ClienteController extends BaseClienteController {

    private final TiendaRepository tiendas;
    private final CatalogoService catalogo;
    private final FavoritoService favoritos;
    private final CarritoService carrito;
    private final ResenaService resenas;
    private final co.cabore.CaboreApplication.service.TiendaPersonalizacionService personalizacionService;
    private final co.cabore.CaboreApplication.repository.ResenaRepository resenaRepository;
    private final co.cabore.CaboreApplication.repository.PedidoItemRepository pedidoItemRepository;

    /* ── Catálogo de productos con búsqueda y filtros ───────────────────── */

    @GetMapping("/cliente")
    public String catalogo(@RequestParam(defaultValue = "") String q,
                           @RequestParam(name = "categoria", required = false) Integer categoriaId,
                           @RequestParam(name = "tienda", required = false) Integer tiendaId,
                           @RequestParam(name = "oferta", defaultValue = "false") boolean oferta,
                           @RequestParam(name = "disponible", defaultValue = "false") boolean disponible,
                           @RequestParam(name = "min", required = false) BigDecimal minPrecio,
                           @RequestParam(name = "max", required = false) BigDecimal maxPrecio,
                           @RequestParam(name = "orden", defaultValue = "reciente") String orden,
                           @RequestParam(name = "p", defaultValue = "0") int pagina,
                           Model model) {

        CatalogoService.Filtros filtros = new CatalogoService.Filtros(
                q, categoriaId, tiendaId, oferta, disponible, minPrecio, maxPrecio,
                CatalogoService.normalizarOrden(orden), Math.max(0, pagina));

        Page<Producto> resultados = catalogo.buscar(filtros);
        CatalogoService.FiltrosVigentes vigentes = CatalogoService.FiltrosVigentes.de(filtros);

        model.addAttribute("cats", catalogo.categorias().stream()
                .collect(java.util.stream.Collectors.toMap(
                        co.cabore.CaboreApplication.model.Categoria::getId,
                        co.cabore.CaboreApplication.model.Categoria::getNombre)));
        model.addAttribute("page", resultados);
        model.addAttribute("productos", resultados.getContent());
        model.addAttribute("categorias", catalogo.categorias());
        model.addAttribute("tiendas", tiendas.findByActivoTrue()); // vista original: lista de tiendas
        model.addAttribute("destacados", vigentes.activos() ? java.util.List.<Producto>of() : catalogo.destacados());
        model.addAttribute("favoritosIds", favoritos.idsFavoritos(usuarioId()));
        model.addAttribute("carritoResumen", carrito.ver(usuarioId()));
        model.addAttribute("f", filtros);
        model.addAttribute("vigentes", vigentes);
        model.addAttribute("categoriaSel", categoriaId == null ? null
                : catalogo.categorias().stream().filter(c -> c.getId().equals(categoriaId)).findFirst().orElse(null));
        model.addAttribute("tiendaSel", tiendaId == null ? null
                : tiendas.findById(tiendaId).orElse(null));
        model.addAttribute("tituloCatalogo", titulo(q, categoriaId));
        return "cliente";
    }

    /** Título legible de la sección según la búsqueda o categoría activa. */
    private String titulo(String q, Integer categoriaId) {
        if (q != null && !q.isBlank()) {
            return "Resultados para \u201c" + q.trim() + "\u201d";
        }
        if (categoriaId != null) {
            return catalogo.categorias().stream()
                    .filter(c -> c.getId().equals(categoriaId))
                    .map(co.cabore.CaboreApplication.model.Categoria::getNombre)
                    .findFirst().orElse("Categoría");
        }
        return "Todos los productos";
    }

    /* ── Tienda individual (con Constructor Avanzado, búsqueda y filtros) ── */

    @GetMapping("/tienda/{id}")
    public String verTienda(@PathVariable Integer id,
                            @RequestParam(name = "q", defaultValue = "") String q,
                            @RequestParam(name = "categoria", required = false) Integer categoriaId,
                            @RequestParam(name = "oferta", defaultValue = "false") boolean oferta,
                            @RequestParam(name = "orden", required = false) String orden,
                            @RequestParam(name = "preview", required = false) String preview,
                            Model model) {
        Tienda tienda = tiendas.findById(id)
                .filter(Tienda::isActivo)
                .orElseThrow(() -> new NoSuchElementException("Tienda no encontrada."));
        var principal = co.cabore.CaboreApplication.service.UsuarioActual.principal();
        boolean esCliente = principal != null && principal.getRol() == co.cabore.CaboreApplication.model.Rol.cliente;
        boolean esDuenoOAdmin = principal != null && (
                principal.getRol() == co.cabore.CaboreApplication.model.Rol.administrador
                || (tienda.getUsuario() != null && principal.getId().equals(tienda.getUsuario().getId())));
        boolean previewBorrador = "borrador".equalsIgnoreCase(preview) && esDuenoOAdmin;

        var cfg = personalizacionService.cargarParaTiendaPublica(tienda, previewBorrador);
        String ordenEfectivo = (orden == null || orden.isBlank())
                ? cfg.getOrdenPredeterminado()
                : CatalogoService.normalizarOrden(orden);

        var todosTienda = catalogo.productosDeTienda(id);
        var filtrados = catalogo.productosDeTiendaFiltrados(id, q, categoriaId, oferta, ordenEfectivo);

        Set<Integer> catIdsTienda = todosTienda.stream()
                .map(Producto::getCategoriaId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        var categoriasTienda = catalogo.categorias().stream()
                .filter(c -> catIdsTienda.contains(c.getId()))
                .toList();
        var categoriasMap = catalogo.categorias().stream()
                .collect(java.util.stream.Collectors.toMap(
                        co.cabore.CaboreApplication.model.Categoria::getId,
                        c -> c));

        // Colecciones para el constructor de secciones de la tienda pública
        var destacadosTienda = todosTienda.stream()
                .filter(Producto::isDestacado)
                .limit(4)
                .toList();
        if (destacadosTienda.isEmpty()) {
            destacadosTienda = todosTienda.stream().limit(4).toList();
        }
        var nuevosTienda = todosTienda.stream()
                .sorted(java.util.Comparator.comparing(Producto::getId).reversed())
                .limit(4)
                .toList();
        var ofertasTiendaList = todosTienda.stream()
                .filter(Producto::isTieneOferta)
                .limit(4)
                .toList();

        // Más vendidos de la tienda calculados sobre pedido_items reales en MariaDB
        java.util.Map<Long, Long> ventasPorProd = new java.util.HashMap<>();
        for (var pi : pedidoItemRepository.findByTiendaId(id)) {
            if (pi.getProducto() != null && pi.getPedido() != null
                    && pi.getPedido().getEstado() != co.cabore.CaboreApplication.model.PedidoEstado.cancelado) {
                long cant = pi.getCantidad() == null ? 0L : pi.getCantidad().longValue();
                ventasPorProd.merge(pi.getProducto().getId(), cant, Long::sum);
            }
        }
        var masVendidosTienda = todosTienda.stream()
                .sorted(java.util.Comparator
                        .comparingLong((Producto p) -> ventasPorProd.getOrDefault(p.getId(), 0L)).reversed()
                        .thenComparing(Producto::isDestacado, java.util.Comparator.reverseOrder())
                        .thenComparing(Producto::getId, java.util.Comparator.reverseOrder()))
                .limit(4)
                .toList();

        var resenasTienda = resenaRepository.buscarRecientesPorTienda(
                id, org.springframework.data.domain.PageRequest.of(0, 6));

        model.addAttribute("tienda", tienda);
        model.addAttribute("cfg", cfg);
        model.addAttribute("modoPreviewBorrador", previewBorrador);
        model.addAttribute("esDuenoTienda", esDuenoOAdmin);
        model.addAttribute("productos", filtrados);
        model.addAttribute("productosDestacadosTienda", destacadosTienda);
        model.addAttribute("productosNuevosTienda", nuevosTienda);
        model.addAttribute("productosOfertaTienda", ofertasTiendaList);
        model.addAttribute("productosMasVendidosTienda", masVendidosTienda);
        model.addAttribute("resenasTienda", resenasTienda);
        model.addAttribute("totalProductosTienda", todosTienda.size());
        model.addAttribute("categoriasTienda", categoriasTienda);
        model.addAttribute("categoriasMap", categoriasMap);
        model.addAttribute("qTienda", q == null ? "" : q.trim());
        model.addAttribute("categoriaTiendaSel", categoriaId);
        model.addAttribute("ofertaTienda", oferta);
        model.addAttribute("ordenTienda", ordenEfectivo);
        model.addAttribute("hayFiltroTienda",
                (q != null && !q.isBlank()) || categoriaId != null || oferta);
        model.addAttribute("esClienteAutenticado", esCliente);
        model.addAttribute("estaAutenticado", principal != null);
        model.addAttribute("rolAutenticado", principal != null && principal.getRol() != null ? principal.getRol().name() : null);
        model.addAttribute("favoritosIds",
                esCliente ? favoritos.idsFavoritos(usuarioId()) : Set.of());
        return "tienda-detalle";
    }

    /* ── Ficha de producto + reseñas + productos relacionados ───────────── */

    @GetMapping("/cliente/producto/{id}")
    public String verProducto(@PathVariable Long id, Model model) {
        Producto p = catalogo.productoDisponible(id)
                .orElseThrow(() -> new NoSuchElementException("El producto no está disponible."));
        Set<Long> favIds = favoritos.idsFavoritos(usuarioId());
        model.addAttribute("cats", catalogo.categorias().stream()
                .collect(java.util.stream.Collectors.toMap(
                        co.cabore.CaboreApplication.model.Categoria::getId,
                        co.cabore.CaboreApplication.model.Categoria::getNombre)));
        model.addAttribute("producto", p);
        model.addAttribute("relacionados", catalogo.relacionados(p));
        model.addAttribute("resenas", resenas.porProducto(id));
        model.addAttribute("miResena", resenas.mia(id, usuarioId()).orElse(null));
        model.addAttribute("puedeResenar", resenas.puedeResenar(id, usuarioId()));
        model.addAttribute("esFavorito", favIds.contains(id));
        model.addAttribute("favoritosIds", favIds);
        return "producto-detalle";
    }
}
