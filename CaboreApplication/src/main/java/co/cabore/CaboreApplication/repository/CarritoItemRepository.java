package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.CarritoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Acceso a datos del carrito persistente por usuario. */
@Repository
public interface CarritoItemRepository extends JpaRepository<CarritoItem, Integer> {

    List<CarritoItem> findByUsuarioIdOrderByIdAsc(Integer usuarioId);

    Optional<CarritoItem> findByUsuarioIdAndProductoId(Integer usuarioId, Long productoId);

    long countByUsuarioId(Integer usuarioId);

    void deleteByUsuarioIdAndProductoId(Integer usuarioId, Long productoId);

    void deleteByUsuarioId(Integer usuarioId);

    /** Limpia el producto de todos los carritos (carrito_items no tiene FK en bases existentes). */
    void deleteByProductoId(Long productoId);

    @Modifying
    @Query("delete from CarritoItem c where c.usuario.id = :usuarioId and c.producto.id in :productoIds")
    int borrarDelCarrito(@Param("usuarioId") Integer usuarioId, @Param("productoIds") Collection<Long> productoIds);
}
