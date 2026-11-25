package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.Pedido;
import co.cabore.CaboreApplication.model.PedidoEstado;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Integer> {

    @EntityGraph(attributePaths = {"items", "items.producto"})
    List<Pedido> findByUsuarioIdOrderByCreadoEnDesc(Integer usuarioId);

    /** Solo el dueño puede consultar su pedido por id (protección por usuario autenticado). */
    Optional<Pedido> findByIdAndUsuarioId(Integer id, Integer usuarioId);

    /** Pedido con sus ítems y productos cargados en una sola consulta (evita N+1 en la vista). */
    @Query("""
            select distinct p from Pedido p
            join fetch p.usuario u
            left join fetch p.items i
            left join fetch i.producto pr
            left join fetch pr.tienda t
            where p.id = :id and p.usuario.id = :usuarioId
            """)
    Optional<Pedido> buscarDetalle(@Param("id") Integer id, @Param("usuarioId") Integer usuarioId);

    /* ── Consultas del módulo administrador ─────────────────────────────── */

    /** Detalle completo de cualquier pedido para el administrador (sin filtrar por comprador). */
    @Query("""
            select distinct p from Pedido p
            join fetch p.usuario u
            left join fetch p.items i
            left join fetch i.producto pr
            left join fetch pr.tienda t
            where p.id = :id
            """)
    Optional<Pedido> buscarDetalleAdmin(@Param("id") Integer id);

    long countByEstado(PedidoEstado estado);

    long countByEstadoNot(PedidoEstado estado);

    @Query("select coalesce(sum(p.total), 0) from Pedido p where p.estado <> :excluido")
    BigDecimal sumarTotalExceptoEstado(@Param("excluido") PedidoEstado excluido);

    @EntityGraph(attributePaths = {"usuario"})
    List<Pedido> findAllByOrderByCreadoEnDesc(Pageable pageable);

    @Query("""
            select p from Pedido p
            join fetch p.usuario u
            where (:pedidoId is null or p.id = :pedidoId)
              and (:cliente = '' or lower(u.nombre) like lower(concat('%', :cliente, '%')))
              and (:estado is null or p.estado = :estado)
            order by p.creadoEn desc
            """)
    List<Pedido> buscarFacturasAdmin(@Param("pedidoId") Integer pedidoId,
                                     @Param("cliente") String cliente,
                                     @Param("estado") PedidoEstado estado,
                                     Pageable pageable);

    @Query("""
            select distinct p from Pedido p
            join fetch p.usuario u
            left join fetch p.items i
            left join fetch i.producto pr
            left join fetch pr.tienda t
            where (:desde is null or p.creadoEn >= :desde)
              and (:hasta is null or p.creadoEn <= :hasta)
              and (:estado is null or p.estado = :estado)
            order by p.creadoEn desc
            """)
    List<Pedido> buscarParaReporteAdmin(@Param("desde") LocalDateTime desde,
                                        @Param("hasta") LocalDateTime hasta,
                                        @Param("estado") PedidoEstado estado);
}
