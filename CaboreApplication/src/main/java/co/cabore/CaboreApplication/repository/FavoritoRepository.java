package co.cabore.CaboreApplication.repository;

import co.cabore.CaboreApplication.model.Favorito;
import co.cabore.CaboreApplication.model.FavoritoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoritoRepository extends JpaRepository<Favorito, FavoritoId> {

    List<Favorito> findByUsuarioIdOrderByGuardadoEnDesc(Integer usuarioId);

    Optional<Favorito> findByUsuarioIdAndProductoId(Integer usuarioId, Long productoId);

    boolean existsByUsuarioIdAndProductoId(Integer usuarioId, Long productoId);

    @Query("select f.producto.id from Favorito f where f.usuario.id = :usuarioId")
    List<Long> idsProductosFavoritos(@Param("usuarioId") Integer usuarioId);
}
