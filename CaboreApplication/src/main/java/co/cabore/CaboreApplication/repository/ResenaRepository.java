package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.Resena;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResenaRepository extends JpaRepository<Resena, Integer> {

    List<Resena> findByProductoIdOrderByCreadoEnDesc(Long productoId);

    Optional<Resena> findByProductoIdAndUsuarioId(Long productoId, Integer usuarioId);

    List<Resena> findByUsuarioIdOrderByCreadoEnDesc(Integer usuarioId);

    /**
     * Resumen agregado (id producto, promedio, cantidad) para pintar estrellas
     * en el catálogo con una sola consulta, sin N+1.
     */
    @Query("""
            select r.producto.id, avg(r.calificacion), count(r)
            from Resena r
            where r.producto.id in :ids
            group by r.producto.id
            """)
    List<Object[]> resumenPorProductos(@Param("ids") Collection<Long> ids);

    /**
     * Regla de negocio: solo se puede reseñar un producto que el cliente
     * compró en un pedido ya ENTREGADO.
     */
    @Query("""
            select count(i) from PedidoItem i
            where i.producto.id = :productoId
              and i.pedido.usuario.id = :usuarioId
              and i.pedido.estado = co.cabore.CaboreApplication.model.PedidoEstado.entregado
            """)
    long comprasEntregadas(@Param("productoId") Long productoId, @Param("usuarioId") Integer usuarioId);

    @Query("""
            select r from Resena r
            join fetch r.usuario u
            join fetch r.producto p
            where p.tienda.id = :tiendaId
            order by r.creadoEn desc
            """)
    List<Resena> buscarRecientesPorTienda(@Param("tiendaId") Integer tiendaId,
                                          org.springframework.data.domain.Pageable pageable);
}
