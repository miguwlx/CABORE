package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.PedidoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PedidoItemRepository extends JpaRepository<PedidoItem, Integer> {

    /** ¿El producto aparece en algún pedido? (si es así, no puede borrarse físicamente). */
    boolean existsByProductoId(Long productoId);

    @Query("""
            select i from PedidoItem i
            join fetch i.pedido p
            join fetch p.usuario u
            join fetch i.producto pr
            join fetch pr.tienda t
            where t.id = :tiendaId
            order by p.creadoEn desc, i.id asc
            """)
    List<PedidoItem> findByTiendaId(@Param("tiendaId") Integer tiendaId);

    @Query("""
            select count(i) from PedidoItem i
            where i.pedido.id = :pedidoId
              and i.producto.tienda.id = :tiendaId
            """)
    long contarItemsDeTiendaEnPedido(@Param("pedidoId") Integer pedidoId, @Param("tiendaId") Integer tiendaId);
}
