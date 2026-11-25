package co.cabore.CaboreApplication.service;

import co.cabore.CaboreApplication.model.Categoria;
import co.cabore.CaboreApplication.model.EstadoVerificacion;
import co.cabore.CaboreApplication.model.Producto;
import co.cabore.CaboreApplication.repository.CategoriaRepository;
import co.cabore.CaboreApplication.repository.ProductoRepository;
import co.cabore.CaboreApplication.repository.ResenaRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogo del cliente (fase 3): búsqueda, filtros, orden, paginación y
 * calificaciones agregadas desde las reseñas.
 */
@Service
@RequiredArgsConstructor
public class CatalogoService {

    /** Página de productos del catálogo. */
    public static final int TAMANO_PAGINA = 12;

    /** Parámetros de búsqueda/filtrado ya normalizados. */
    public record Filtros(String q, Integer categoriaId, Integer tiendaId,
                          boolean oferta, boolean disponible,
                          BigDecimal minPrecio, BigDecimal maxPrecio, String orden, int pagina) {}

    public record FiltrosVigentes(String q, Integer categoriaId, boolean oferta, boolean disponible,
                                  BigDecimal minPrecio, BigDecimal maxPrecio, String orden, boolean activos) {
        public static FiltrosVigentes de(Filtros f) {
            boolean hayFiltro = (f.q() != null && !f.q().isBlank())
                    || f.categoriaId() != null || f.tiendaId() != null
                    || f.oferta() || f.disponible()
                    || f.minPrecio() != null || f.maxPrecio() != null;
            return new FiltrosVigentes(f.q(), f.categoriaId(), f.oferta(), f.disponible(),
                    f.minPrecio(), f.maxPrecio(), f.orden(), hayFiltro);
        }
    }

    private final ProductoRepository productos;
    private final CategoriaRepository categorias;
    private final ResenaRepository resenas;

    @Transactional(readOnly = true)
    public Page<Producto> buscar(Filtros f) {
        String orden = normalizarOrden(f.orden());
        Page<Producto> page = productos.buscar(
                f.q() == null ? "" : f.q().trim(),
                f.categoriaId(),
                f.tiendaId(),
                f.oferta(),
                f.disponible(),
                f.minPrecio(),
                f.maxPrecio(),
                orden,
                PageRequest.of(Math.max(0, f.pagina()), TAMANO_PAGINA, Sort.unsorted()));
        anotarCalificaciones(page.getContent());
        return page;
    }

    @Transactional(readOnly = true)
    public List<Producto> destacados() {
        List<Producto> list = productos.buscarDestacados(PageRequest.of(0, 4));
        anotarCalificaciones(list);
        return list;
    }

    @Transactional(readOnly = true)
    public List<Categoria> categorias() {
        return categorias.findAllByOrderByIdAsc();
    }

    /** Productos comprables de una tienda (activo + aprobado, tienda activa). */
    @Transactional(readOnly = true)
    public List<Producto> productosDeTienda(Integer tiendaId) {
        List<Producto> list = productos.findByTiendaIdAndActivoTrueAndEstadoVerificacion(
                tiendaId, EstadoVerificacion.aprobado).stream()
                .filter(p -> p.getTienda() != null && p.getTienda().isActivo())
                .toList();
        anotarCalificaciones(list);
        return list;
    }

    /** Productos de una tienda con búsqueda, filtro por categoría/oferta y ordenamiento. */
    @Transactional(readOnly = true)
    public List<Producto> productosDeTiendaFiltrados(Integer tiendaId,
                                                     String q,
                                                     Integer categoriaId,
                                                     boolean soloOferta,
                                                     String orden) {
        String qNorm = q == null ? "" : q.trim().toLowerCase();
        String ordenNorm = normalizarOrden(orden);
        java.util.Comparator<Producto> cmp = switch (ordenNorm) {
            case "precio_asc" -> java.util.Comparator.comparing(Producto::getPrecioActual);
            case "precio_desc" -> java.util.Comparator.comparing(Producto::getPrecioActual).reversed();
            case "nombre" -> java.util.Comparator.comparing(
                    p -> p.getNombre() == null ? "" : p.getNombre().toLowerCase());
            default -> java.util.Comparator.comparing(Producto::getId).reversed();
        };

        List<Producto> list = productosDeTienda(tiendaId).stream()
                .filter(p -> qNorm.isEmpty()
                        || (p.getNombre() != null && p.getNombre().toLowerCase().contains(qNorm))
                        || (p.getDescripcion() != null && p.getDescripcion().toLowerCase().contains(qNorm)))
                .filter(p -> categoriaId == null || categoriaId.equals(p.getCategoriaId()))
                .filter(p -> !soloOferta || p.isTieneOferta())
                .sorted(cmp)
                .toList();
        return list;
    }

    /** Productos relacionados por categoría o tienda para la ficha de producto. */
    @Transactional(readOnly = true)
    public List<Producto> relacionados(Producto base) {
        if (base == null || base.getId() == null) return List.of();
        Integer tiendaId = base.getTienda() != null ? base.getTienda().getId() : null;
        List<Producto> list = productos.buscarRelacionados(
                base.getId(), base.getCategoriaId(), tiendaId, PageRequest.of(0, 4));
        anotarCalificaciones(list);
        return list;
    }

    /** Ficha de producto con reseñas y calificación agregada. */
    @Transactional(readOnly = true)
    public Optional<Producto> productoDisponible(Long id) {
        Optional<Producto> p = productos.findByIdAndActivoTrueAndEstadoVerificacion(
                id, EstadoVerificacion.aprobado)
                .filter(prod -> prod.getTienda() != null && prod.getTienda().isActivo());
        p.ifPresent(this::anotarCalificaciones);
        return p;
    }

    /** Acepta solo los cuatro órdenes conocidos; cualquier otro cae en 'reciente'. */
    public static String normalizarOrden(String orden) {
        if (orden == null) return "reciente";
        return switch (orden) {
            case "precio_asc", "precio_desc", "nombre" -> orden;
            default -> "reciente";
        };
    }

    /** Asigna ratingPromedio/totalResenas (campos transitorios) en lote. */
    @Transactional(readOnly = true)
    public void anotarCalificaciones(List<Producto> lista) {
        if (lista.isEmpty()) return;
        Map<Long, Object[]> resumen = resumenResenas(lista.stream().map(Producto::getId).toList());
        for (Producto p : lista) {
            Object[] r = resumen.get(p.getId());
            p.setRatingPromedio(r == null ? null : ((Number) r[1]).doubleValue());
            p.setTotalResenas(r == null ? 0 : ((Number) r[2]).intValue());
        }
    }

    private void anotarCalificaciones(Producto p) {
        Map<Long, Object[]> resumen = resumenResenas(List.of(p.getId()));
        Object[] r = resumen.get(p.getId());
        p.setRatingPromedio(r == null ? null : ((Number) r[1]).doubleValue());
        p.setTotalResenas(r == null ? 0 : ((Number) r[2]).intValue());
    }

    private Map<Long, Object[]> resumenResenas(List<Long> ids) {
        Map<Long, Object[]> mapa = new HashMap<>();
        for (Object[] fila : resenas.resumenPorProductos(ids)) {
            mapa.put(((Number) fila[0]).longValue(), fila);
        }
        return mapa;
    }
}
