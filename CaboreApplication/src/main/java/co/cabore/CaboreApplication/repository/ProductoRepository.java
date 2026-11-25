package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.EstadoVerificacion;
import co.cabore.CaboreApplication.model.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /** Método original del catálogo básico — se conserva. */
    List<Producto> findByTiendaId(Integer tiendaId);

    List<Producto> findByTiendaIdOrderByIdDesc(Integer tiendaId);

    List<Producto> findByTiendaIdAndActivoTrueAndEstadoVerificacion(Integer tiendaId, EstadoVerificacion estado);

    Optional<Producto> findByIdAndActivoTrueAndEstadoVerificacion(Long id, EstadoVerificacion estado);

    /**
     * Búsqueda con filtros del catálogo (fase 3): texto, categoría, tienda,
     * solo-oferta, solo-disponible y rango de precio sobre el precio vigente.
     * El ordenamiento se resuelve en la propia consulta (CASE) para servir
     * páginas ordenadas desde MariaDB sin cargar todo en memoria.
     */
    @Query(value = """
            select p from Producto p
            join p.tienda t
            where p.activo = true
              and p.estadoVerificacion = co.cabore.CaboreApplication.model.EstadoVerificacion.aprobado
              and t.activo = true
              and (:q = '' or lower(p.nombre) like lower(concat('%', :q, '%'))
                   or coalesce(p.descripcion, '') like lower(concat('%', :q, '%'))
                   or lower(t.nombre) like lower(concat('%', :q, '%')))
              and (:categoriaId is null or p.categoriaId = :categoriaId)
              and (:tiendaId is null or t.id = :tiendaId)
              and (:soloOferta = false or (p.precioOferta is not null and p.precioOferta < p.precio))
              and (:soloDisponible = false or p.stock > 0)
              and (:minPrecio is null or (case when p.precioOferta is not null and p.precioOferta < p.precio then p.precioOferta else p.precio end) >= :minPrecio)
              and (:maxPrecio is null or (case when p.precioOferta is not null and p.precioOferta < p.precio then p.precioOferta else p.precio end) <= :maxPrecio)
            order by
              case when :orden = 'precio_asc'  then (case when p.precioOferta is not null and p.precioOferta < p.precio then p.precioOferta else p.precio end) end asc,
              case when :orden = 'precio_desc' then (case when p.precioOferta is not null and p.precioOferta < p.precio then p.precioOferta else p.precio end) end desc,
              case when :orden = 'nombre'      then lower(p.nombre) end asc,
              case when :orden = 'reciente'    then cast(p.id as double) end desc,
              p.id desc
            """,
            countQuery = """
                    select count(p) from Producto p
                    join p.tienda t
                    where p.activo = true
                      and p.estadoVerificacion = co.cabore.CaboreApplication.model.EstadoVerificacion.aprobado
                      and t.activo = true
                      and (:q = '' or lower(p.nombre) like lower(concat('%', :q, '%'))
                           or coalesce(p.descripcion, '') like lower(concat('%', :q, '%'))
                           or lower(t.nombre) like lower(concat('%', :q, '%')))
                      and (:categoriaId is null or p.categoriaId = :categoriaId)
                      and (:tiendaId is null or t.id = :tiendaId)
                      and (:soloOferta = false or (p.precioOferta is not null and p.precioOferta < p.precio))
                      and (:soloDisponible = false or p.stock > 0)
                      and (:minPrecio is null or (case when p.precioOferta is not null and p.precioOferta < p.precio then p.precioOferta else p.precio end) >= :minPrecio)
                      and (:maxPrecio is null or (case when p.precioOferta is not null and p.precioOferta < p.precio then p.precioOferta else p.precio end) <= :maxPrecio)
                    """)
    Page<Producto> buscar(@Param("q") String q,
                          @Param("categoriaId") Integer categoriaId,
                          @Param("tiendaId") Integer tiendaId,
                          @Param("soloOferta") boolean soloOferta,
                          @Param("soloDisponible") boolean soloDisponible,
                          @Param("minPrecio") BigDecimal minPrecio,
                          @Param("maxPrecio") BigDecimal maxPrecio,
                          @Param("orden") String orden,
                          Pageable pageable);

    /** Productos marcados como destacados para "Destacados para ti". */
    @Query("""
            select p from Producto p
            join p.tienda t
            where p.destacado = true
              and p.activo = true
              and p.stock > 0
              and p.estadoVerificacion = co.cabore.CaboreApplication.model.EstadoVerificacion.aprobado
              and t.activo = true
            order by p.id desc
            """)
    List<Producto> buscarDestacados(Pageable pageable);

    /** Productos relacionados por categoría o tienda (excluyendo el producto actual). */
    @Query("""
            select p from Producto p
            join fetch p.tienda t
            where p.id <> :excluirId
              and p.activo = true
              and p.stock > 0
              and p.estadoVerificacion = co.cabore.CaboreApplication.model.EstadoVerificacion.aprobado
              and t.activo = true
              and ((:categoriaId is not null and p.categoriaId = :categoriaId)
                   or (:tiendaId is not null and t.id = :tiendaId))
            order by
              case when (:categoriaId is not null and p.categoriaId = :categoriaId) then 0 else 1 end asc,
              p.destacado desc,
              p.id desc
            """)
    List<Producto> buscarRelacionados(@Param("excluirId") Long excluirId,
                                      @Param("categoriaId") Integer categoriaId,
                                      @Param("tiendaId") Integer tiendaId,
                                      Pageable pageable);

    /**
     * Descuenta stock de forma ATÓMICA: solo si hay existencias suficientes.
     * Devuelve 1 cuando el descuento ocurrió y 0 si no alcanza el stock,
     * lo que permite al checkout abortar la compra sin escrituras fantasma.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Producto p set p.stock = p.stock - :cantidad where p.id = :id and p.stock >= :cantidad")
    int descontarStock(@Param("id") Long id, @Param("cantidad") int cantidad);

    /** Restaura stock cuando un pedido pendiente es cancelado por su dueño. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Producto p set p.stock = p.stock + :cantidad where p.id = :id")
    int restaurarStock(@Param("id") Long id, @Param("cantidad") int cantidad);

    /* ── Consultas del módulo administrador ─────────────────────────────── */

    long countByEstadoVerificacion(EstadoVerificacion estado);

    long countByCategoriaId(Integer categoriaId);

    long countByTiendaId(Integer tiendaId);

    @Query("""
            select p from Producto p
            join fetch p.tienda t
            join fetch t.usuario u
            where p.estadoVerificacion = :estado
            order by p.id desc
            """)
    List<Producto> listarPorEstadoVerificacion(@Param("estado") EstadoVerificacion estado, Pageable pageable);
}
